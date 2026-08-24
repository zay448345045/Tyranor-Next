package com.tyranor.next.scanner

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

data class VndbCandidate(
    val id: String,
    val title: String,
    val originalTitle: String,
    val developer: String,
    val released: String,
    val coverUrl: String,
)

object VndbCoverService {
    private const val ENDPOINT = "https://api.vndb.org/kana/vn"
    private const val FIELDS =
        "title,alttitle,titles.lang,titles.title,titles.latin,titles.official,titles.main,released,image.url,image.thumbnail,developers.name,developers.original"
    private const val MAX_COVER_BYTES = 20L * 1024L * 1024L
    private const val MIN_REQUEST_INTERVAL_MS = 1100L

    private var lastRequestTime = 0L

    fun fetchBestCover(context: Context, game: ScanGame): ScanGame? {
        if (!game.coverUri.isNullOrBlank()) return game
        val candidate = searchCandidates(game.title, 1).firstOrNull() ?: return null
        val cover = downloadCover(context, candidate.coverUrl, "vndb_${stableKey(game.uri)}") ?: return null
        return game.copy(
            coverUri = cover,
            vndbId = candidate.id,
            metadataTitle = candidate.displayTitle(),
        )
    }

    fun bindCandidate(context: Context, game: ScanGame, candidate: VndbCandidate): ScanGame? {
        val cover = downloadCover(context, candidate.coverUrl, "vndb_${stableKey(game.uri)}") ?: return null
        return game.copy(
            coverUri = cover,
            vndbId = candidate.id,
            metadataTitle = candidate.displayTitle(),
        )
    }

    /** 将用户从相册选择的图片保存为该游戏的自定义封面，返回更新后的游戏（失败返回 null）。 */
    fun saveCustomCover(context: Context, game: ScanGame, pickedUri: Uri): ScanGame? {
        val dir = File(context.filesDir, "covers_remote")
        if (!dir.exists() && !dir.mkdirs()) return null
        val ext = when (context.contentResolver.getType(pickedUri)?.lowercase()?.substringAfterLast('/')) {
            "png" -> "png"
            "webp" -> "webp"
            "gif" -> "gif"
            else -> "jpg"
        }
        val target = File(dir, "custom_${stableKey(game.uri)}.$ext")
        return try {
            context.contentResolver.openInputStream(pickedUri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return null
            // 删除旧封面文件（仅限应用封面缓存目录内）
            game.coverUri?.let { old ->
                val oldFile = runCatching { File(Uri.parse(old).path ?: "") }.getOrNull()
                if (oldFile != null && oldFile.canonicalPath != target.canonicalPath &&
                    runCatching { oldFile.canonicalPath }.getOrNull()?.startsWith(dir.canonicalPath) == true
                ) {
                    oldFile.delete()
                }
            }
            game.copy(coverUri = Uri.fromFile(target).toString())
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    fun searchCandidates(keyword: String, limit: Int): List<VndbCandidate> {
        val query = cleanTitle(keyword)
        if (query.isBlank()) return emptyList()

        val body = JSONObject()
            .put("filters", JSONArray().put("search").put("=").put(query))
            .put("fields", FIELDS)
            .put("sort", "searchrank")
            .put("results", limit.coerceIn(1, 10))

        throttle()

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TyranorNext/1.0")
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return emptyList()
            val text = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val results = JSONObject(text).optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    results.optJSONObject(i)?.let { add(parseCandidate(it)) }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseCandidate(o: JSONObject): VndbCandidate {
        var chineseTitle = ""
        var originalTitle = o.optString("alttitle", "")
        o.optJSONArray("titles")?.let { titles ->
            for (i in 0 until titles.length()) {
                val t = titles.optJSONObject(i) ?: continue
                val lang = t.optString("lang", "")
                val title = t.optString("title", "")
                if ((lang == "zh-Hans" || lang == "zh-Hant" || lang == "zh") && chineseTitle.isEmpty()) {
                    chineseTitle = title
                }
                if (t.optBoolean("main", false) && originalTitle.isEmpty()) originalTitle = title
            }
        }
        val image = o.optJSONObject("image")
        val devs = o.optJSONArray("developers")
        val developers = buildList {
            if (devs != null) {
                for (i in 0 until devs.length()) {
                    if (size >= 3) break
                    val d = devs.optJSONObject(i) ?: continue
                    val name = firstNonEmpty(d.optString("original", ""), d.optString("name", ""))
                    if (name.isNotBlank()) add(name)
                }
            }
        }.joinToString(" / ")
        return VndbCandidate(
            id = o.optString("id", ""),
            title = firstNonEmpty(chineseTitle, o.optString("title", "")),
            originalTitle = firstNonEmpty(originalTitle, o.optString("title", "")),
            developer = developers,
            released = o.optString("released", ""),
            coverUrl = firstNonEmpty(image?.optString("thumbnail", ""), image?.optString("url", "")),
        )
    }

    private fun downloadCover(context: Context, imageUrl: String, prefix: String): String? {
        if (imageUrl.isBlank()) return null
        val dir = File(context.filesDir, "covers_remote")
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = File(dir, "${prefix}_${stableKey(imageUrl)}.jpg")
        if (target.isFile && target.length() > 0) return Uri.fromFile(target).toString()

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Referer", "https://vndb.org/")
                setRequestProperty("Cookie", "vndb_img=1; vndb_samesite=1")
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode !in 200..299) return null
            if (conn.contentLengthLong > MAX_COVER_BYTES) return null
            var total = 0L
            conn.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_COVER_BYTES) error("cover too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            Uri.fromFile(target).toString()
        } catch (_: Exception) {
            target.delete()
            null
        } finally {
            conn?.disconnect()
        }
    }

    @Synchronized
    private fun throttle() {
        val now = System.currentTimeMillis()
        val wait = MIN_REQUEST_INTERVAL_MS - (now - lastRequestTime)
        if (wait > 0) Thread.sleep(wait)
        lastRequestTime = System.currentTimeMillis()
    }

    private fun cleanTitle(s: String): String {
        val cleaned = s.replace("""\[[^\]]*\]|【[^】]*】""".toRegex(), " ")
            .replace("[\\[\\]【】]".toRegex(), " ")
            .replace("[（）()].*".toRegex(), " ")
            .replace("(?i)complete|汉化|中文版|日文版|体验版|trial|patch".toRegex(), " ")
            .replace('_', ' ')
            .trim()
        return cleaned.ifEmpty { s.trim() }
    }

    private fun stableKey(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun firstNonEmpty(a: String?, b: String?): String {
        return when {
            !a.isNullOrBlank() && a != "null" -> a
            !b.isNullOrBlank() && b != "null" -> b
            else -> ""
        }
    }

    private fun VndbCandidate.displayTitle(): String =
        firstNonEmpty(title, originalTitle).trim()
}
