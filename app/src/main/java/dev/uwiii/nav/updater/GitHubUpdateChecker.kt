package dev.uwiii.nav.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?
)

class GitHubUpdateChecker(private val context: Context) {
    private val client = OkHttpClient()
    private val repoOwner = "uWiiiDev"
    private val repoName = "uWiiiNav"

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "uWiiiNav-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateInfo(false, currentVersion, "", null)
                }

                val body = response.body?.string() ?: return@withContext UpdateInfo(false, currentVersion, "", null)
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").removePrefix("v")
                val releaseNotes = json.optString("body", "Keine Versionshinweise vorhanden.")

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }

                val hasUpdate = isVersionNewer(currentVersion, tagName)
                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = tagName,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            UpdateInfo(false, currentVersion, "", null)
        }
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        if (latest.isBlank()) return false
        val curParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(curParts.size, latParts.size)
        for (i in 0 until maxLen) {
            val c = curParts.getOrElse(i) { 0 }
            val l = latParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun openBrowserDownload(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
