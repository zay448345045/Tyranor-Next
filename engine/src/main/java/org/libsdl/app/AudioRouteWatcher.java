package org.libsdl.app;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * Watches audio output device changes (wired/BT headset hotplug) and REBUILDS SDL2's static
 * AudioTrack on the new route. A mere pause/flush/play kick is not enough on many OEM ROMs:
 * once the pre-opened track is invalidated by a route change it never recovers and stays
 * silent until the game is relaunched. Instead we create a fresh track (which lands on the
 * current default device), atomically swap the static reference, then release the old one —
 * the native writer thread re-reads the field on every call, so playback continues seamlessly.
 *
 * Engines whose host registers through {@link #ensureRegistered()} (ONS via SDLActivity) use
 * this Java AudioTrack backend directly. krkrsdl3/SDL3 handles hotplug natively; Kirikiroid2's
 * audio bypasses this shim entirely (Oboe in libgame.so), so both stay unaffected.
 */
public final class AudioRouteWatcher {
    private static final String TAG = "SDLAudioRoute";
    private static final long REBUILD_DEBOUNCE_MS = 300L;
    private static final int REBUILD_MAX_ATTEMPTS = 3;
    /** registerAudioDeviceCallback 首次会同步投递现有设备列表，需忽略以免误触发恢复。 */
    private static final long REGISTRATION_GRACE_MS = 1500L;

    /** 自定义恢复动作（如 SDL3 的 pause/resume 迷你周期）；null 时走默认 AudioTrack 重建。 */
    private static Runnable sRecoveryAction;
    private static long sRegisteredAtMs;

    private static AudioDeviceCallback sCallback;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final Runnable sRebuildTask = () -> rebuildSdlAudioTrack();
    private static final Runnable sActionTask = () -> {
        Runnable action = sRecoveryAction;
        if (action != null) action.run();
    };
    private static int sRebuildAttempts;

    private AudioRouteWatcher() {
    }

    /**
     * Idempotent; safe to call from any thread once a Context is available.
     * Uses the application context so the static callback never pins an Activity
     * (AudioManager keeps a reference to the Context it was created from).
     */
    public static synchronized void ensureRegistered() {
        if (sCallback != null) return;
        try {
            Context context = SDL.getContext();
            if (context == null) context = SDLActivity.getContext();
            if (context != null) {
                ensureRegistered(context);
            }
        } catch (Throwable t) {
            Log.w(TAG, "register failed", t);
        }
    }

    /** Explicit-context variant for engines whose host is not an SDLActivity. */
    public static synchronized void ensureRegistered(Context rawContext) {
        if (sCallback != null) return;
        try {
            Context context = rawContext != null ? rawContext.getApplicationContext() : null;
            if (context == null) return;
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            sCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    onRouteChanged(addedDevices, true);
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    onRouteChanged(removedDevices, false);
                }
            };
            am.registerAudioDeviceCallback(sCallback, sHandler);
            sRegisteredAtMs = SystemClock.elapsedRealtime();
            Log.i(TAG, "registered");
        } catch (Throwable t) {
            Log.w(TAG, "register failed", t);
        }
    }

    /**
     * 注册时附带路由变化恢复动作。动作在主线程执行；注册后 {@link #REGISTRATION_GRACE_MS}
     * 内的事件（首次设备列表投递）会被忽略。
     */
    public static synchronized void ensureRegistered(Context rawContext, Runnable recoveryAction) {
        sRecoveryAction = recoveryAction;
        ensureRegistered(rawContext);
    }

    /**
     * Hosts MUST call this on Activity destroy: the action lambda captures the Activity,
     * and the static field would otherwise pin it after destruction (and a late route
     * flip could run pause/resume cycles against a dead instance). The device callback
     * itself stays registered — it only holds the application context.
     */
    public static synchronized void clearRecoveryAction() {
        sRecoveryAction = null;
    }

    private static void onRouteChanged(AudioDeviceInfo[] devices, boolean added) {
        boolean sinkChanged = false;
        if (devices != null) {
            for (AudioDeviceInfo info : devices) {
                if (isRelevantSink(info)) {
                    sinkChanged = true;
                    break;
                }
            }
        }
        // Some OEMs report empty arrays on route change; treat every callback as relevant
        // but keep the debounce so bursts collapse into a single rebuild. The initial device
        // list delivery right after registration has nothing to rebuild yet and no-ops safely.
        if (!sinkChanged && devices != null && devices.length > 0) return;
        boolean custom = sRecoveryAction != null;
        // Grace covers BOTH paths: the default path registers right after the initial
        // AudioTrack is created (SDLAudioManager.open), so the synchronous first delivery
        // would otherwise schedule a pointless rebuild of the just-created track.
        if (SystemClock.elapsedRealtime() - sRegisteredAtMs < REGISTRATION_GRACE_MS) {
            Log.i(TAG, "initial sink list delivery, ignored");
            return;
        }
        Log.i(TAG, (added ? "sink added" : "sink removed")
                + (custom ? ", notifying recovery" : ", scheduling rebuild"));
        sHandler.removeCallbacks(sRebuildTask);
        sHandler.removeCallbacks(sActionTask);
        if (custom) {
            sHandler.postDelayed(sActionTask, REBUILD_DEBOUNCE_MS);
        } else {
            sRebuildAttempts = 0;
            sHandler.postDelayed(sRebuildTask, REBUILD_DEBOUNCE_MS);
        }
    }

    private static boolean isRelevantSink(AudioDeviceInfo info) {
        int type = info.getType();
        return info.isSink()
                && type != AudioDeviceInfo.TYPE_TELEPHONY
                && type != AudioDeviceInfo.TYPE_UNKNOWN;
    }

    /**
     * Creates a replacement AudioTrack bound to the CURRENT output device, swaps the static
     * reference, then releases the stale one. Paused tracks are rebuilt paused so background
     * pause/mute handling keeps working afterwards. Retries briefly: right after a route flip
     * the audio policy may need a moment before new tracks open cleanly.
     */
    private static void rebuildSdlAudioTrack() {
        try {
            AudioTrack old = SDLAudioManager.mAudioTrack;
            if (old == null || old.getState() != AudioTrack.STATE_INITIALIZED) return;
            boolean wasPlaying = old.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;

            int sampleRate = old.getSampleRate();
            int channelCount = old.getChannelCount();
            int channelConfig = channelCount >= 6 ? AudioFormat.CHANNEL_OUT_5POINT1
                    : channelCount >= 4 ? AudioFormat.CHANNEL_OUT_QUAD
                    : channelCount == 2 ? AudioFormat.CHANNEL_OUT_STEREO
                    : AudioFormat.CHANNEL_OUT_MONO;
            // getEncoding() requires API 29; below that the SDL2 Java shim only negotiates 16-bit PCM.
            int encoding = Build.VERSION.SDK_INT >= 29
                    ? old.getFormat().getEncoding()
                    : AudioFormat.ENCODING_PCM_16BIT;
            int bufferBytes = Math.max(old.getBufferSizeInFrames(), 256)
                    * channelCount * bytesPerSample(encoding);

            AudioTrack next = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
                    encoding, bufferBytes, AudioTrack.MODE_STREAM);
            if (next.getState() != AudioTrack.STATE_INITIALIZED) {
                next.release();
                retryOrFail("replacement track init failed");
                return;
            }
            if (wasPlaying) next.play();
            // audioClose() runs on the native audio thread and may have raced us: it reads
            // the field, releases the old track and nulls the static. If the field no longer
            // holds the reference we started from, writing `next` back would leak an orphan
            // playing track (and clobber any freshly opened one). Abort instead.
            if (SDLAudioManager.mAudioTrack != old) {
                try {
                    next.pause();
                } catch (Throwable ignored) {
                }
                try {
                    next.release();
                } catch (Throwable ignored) {
                }
                Log.i(TAG, "track closed during rebuild, discarding replacement");
                return;
            }
            SDLAudioManager.mAudioTrack = next;
            try {
                old.pause();
            } catch (Throwable ignored) {
            }
            try {
                old.release();
            } catch (Throwable ignored) {
            }
            Log.i(TAG, "rebuilt AudioTrack rate=" + sampleRate + " ch=" + channelCount
                    + " enc=" + encoding + " playing=" + wasPlaying);
        } catch (Throwable t) {
            Log.w(TAG, "rebuild failed", t);
            retryOrFail(null);
        }
    }

    /** Route flips settle asynchronously; space out retries instead of failing instantly. */
    private static void retryOrFail(String reason) {
        if (++sRebuildAttempts < REBUILD_MAX_ATTEMPTS) {
            sHandler.postDelayed(sRebuildTask, REBUILD_DEBOUNCE_MS);
            return;
        }
        if (reason != null) Log.w(TAG, "rebuild abandoned: " + reason);
    }

    private static int bytesPerSample(int encoding) {
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) return 4;
        if (encoding == AudioFormat.ENCODING_PCM_8BIT) return 1;
        return 2;
    }
}
