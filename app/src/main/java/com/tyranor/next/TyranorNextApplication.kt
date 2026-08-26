package com.tyranor.next

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.tyranor.next.updater.BackgroundUpdateWorker
import com.tyranor.next.updater.UpdateNotificationManager

/** 在整个应用进入后台时安排一次静默更新检查。 */
class TyranorNextApplication : Application(), DefaultLifecycleObserver, Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super<Application>.onCreate()
        UpdateNotificationManager.createChannel(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        BackgroundUpdateWorker.enqueue(this)
    }
}
