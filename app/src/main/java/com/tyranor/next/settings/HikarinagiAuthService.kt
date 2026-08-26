package com.tyranor.next.settings

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.tyranor.next.BuildConfig
import com.tyranor.next.ui.pages.HikarinagiOAuthCallbackActivity
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.max

object HikarinagiAuthService {
    const val EXTRA_AUTH_CANCELED = "extra_auth_canceled"

    private const val AUTHORIZE_URL = "https://id.hikarinagi.org/oidc/auth"
    private const val TOKEN_URL = "https://id.hikarinagi.org/oidc/token"
    private const val REDIRECT_URI = "tyranornext://oauth/hikarinagi"
    private const val SCOPES = "openid catalog:read catalog:full offline_access"
    private const val REFRESH_SKEW_MS = 60_000L

    private var authorizationService: AuthorizationService? = null

    fun startAuthorization(activity: Activity): String? {
        val clientId = clientId()
        if (clientId.isBlank()) return "Hikarinagi Client ID 未配置"
        val appContext = activity.applicationContext

        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(AUTHORIZE_URL),
            Uri.parse(TOKEN_URL),
        )
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI),
        )
            .setScope(SCOPES)
            .setPrompt("consent")
            .build()

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val completeIntent = PendingIntent.getActivity(
            activity,
            0,
            Intent(activity, HikarinagiOAuthCallbackActivity::class.java),
            flags,
        )
        val cancelIntent = PendingIntent.getActivity(
            activity,
            1,
            Intent(activity, HikarinagiOAuthCallbackActivity::class.java).putExtra(EXTRA_AUTH_CANCELED, true),
            flags,
        )
        val service = AuthorizationService(appContext)
        replaceAuthorizationService(service)
        return try {
            service.performAuthorizationRequest(request, completeIntent, cancelIntent)
            null
        } catch (_: ActivityNotFoundException) {
            replaceAuthorizationService(null)
            "未找到可用于 Hikarinagi 登录的浏览器"
        } catch (_: Exception) {
            replaceAuthorizationService(null)
            "Hikarinagi 登录启动失败"
        }
    }

    fun handleAuthorizationResponse(
        activity: Activity,
        intent: Intent,
        onResult: (success: Boolean, message: String) -> Unit,
    ) {
        replaceAuthorizationService(null)
        val appContext = activity.applicationContext
        if (intent.getBooleanExtra(EXTRA_AUTH_CANCELED, false)) {
            onResult(false, "Hikarinagi 授权已取消")
            return
        }
        val exception = AuthorizationException.fromIntent(intent)
        if (exception != null) {
            onResult(false, exception.errorDescription ?: exception.message ?: "Hikarinagi 授权失败")
            return
        }
        val response = AuthorizationResponse.fromIntent(intent)
        if (response == null) {
            onResult(false, "Hikarinagi 授权响应无效")
            return
        }
        if (response.state != response.request.state) {
            onResult(false, "Hikarinagi 授权状态校验失败")
            return
        }

        val service = AuthorizationService(appContext)
        try {
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenException ->
                service.dispose()
                val token = tokenResponse
                if (tokenException != null || token == null) {
                    onResult(
                        false,
                        tokenException?.errorDescription ?: tokenException?.message ?: "Hikarinagi token 交换失败",
                    )
                    return@performTokenRequest
                }
                val nonceError = validateIdTokenNonce(token, response.request.nonce)
                if (nonceError != null) {
                    onResult(false, nonceError)
                    return@performTokenRequest
                }
                val accessToken = token.accessToken.orEmpty()
                val refreshToken = token.refreshToken.orEmpty()
                if (accessToken.isBlank() || refreshToken.isBlank()) {
                    onResult(false, "Hikarinagi token 响应缺少必要字段")
                    return@performTokenRequest
                }
                val expiresAt = token.accessTokenExpirationTime ?: (System.currentTimeMillis() + 3_600_000L)
                HikarinagiAuthStore.saveTokens(appContext, accessToken, refreshToken, expiresAt)
                onResult(true, "Hikarinagi 授权成功")
            }
        } catch (_: Exception) {
            service.dispose()
            onResult(false, "Hikarinagi token 交换失败")
        }
    }

    @Synchronized
    fun getValidAccessToken(context: Context): String? {
        val accessToken = HikarinagiAuthStore.getAccessToken(context)
        val expiresAt = HikarinagiAuthStore.getExpiresAtMillis(context)
        if (accessToken.isNotBlank() && System.currentTimeMillis() + REFRESH_SKEW_MS < expiresAt) {
            return accessToken
        }
        return refreshAccessToken(context)
    }

    @Synchronized
    private fun refreshAccessToken(context: Context): String? {
        val clientId = clientId()
        val refreshToken = HikarinagiAuthStore.getRefreshToken(context)
        if (clientId.isBlank() || refreshToken.isBlank()) return null

        var conn: HttpURLConnection? = null
        return try {
            val form = formBody(
                "grant_type" to "refresh_token",
                "client_id" to clientId,
                "refresh_token" to refreshToken,
            )
            conn = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "TyranorNext/1.0")
            }
            conn.outputStream.use { it.write(form.toByteArray(StandardCharsets.UTF_8)) }
            val body = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (conn.responseCode == 400 || conn.responseCode == 401) {
                HikarinagiAuthStore.markNeedsReauth(context, "Hikarinagi 授权已失效，请重新登录")
                return null
            }
            if (conn.responseCode !in 200..299) return null

            val json = JSONObject(body)
            val access = json.optString("access_token", "")
            val nextRefresh = json.optString("refresh_token", "")
                .takeIf { it.isNotBlank() }
                ?: refreshToken
            if (access.isBlank()) return null
            val expiresIn = max(json.optLong("expires_in", 3600L), 1L)
            val expiresAt = System.currentTimeMillis() + expiresIn * 1000L
            HikarinagiAuthStore.saveTokens(context, access, nextRefresh, expiresAt)
            access
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun validateIdTokenNonce(token: TokenResponse, expectedNonce: String?): String? {
        val idToken = token.idToken?.trim().orEmpty()
        val nonce = expectedNonce?.trim().orEmpty()
        if (idToken.isBlank() || nonce.isBlank()) return null
        val parts = idToken.split(".")
        if (parts.size != 3) return "Hikarinagi ID token 格式无效"
        return try {
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), StandardCharsets.UTF_8)
            val actual = JSONObject(payload).optString("nonce", "")
            if (MessageDigest.isEqual(actual.toByteArray(StandardCharsets.UTF_8), nonce.toByteArray(StandardCharsets.UTF_8))) {
                null
            } else {
                "Hikarinagi ID token nonce 校验失败"
            }
        } catch (_: Exception) {
            "Hikarinagi ID token 解析失败"
        }
    }

    private fun clientId(): String = BuildConfig.HIKARINAGI_CLIENT_ID.trim()

    @Synchronized
    private fun replaceAuthorizationService(service: AuthorizationService?) {
        authorizationService?.dispose()
        authorizationService = service
    }

    private fun formBody(vararg values: Pair<String, String>): String =
        values.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}
