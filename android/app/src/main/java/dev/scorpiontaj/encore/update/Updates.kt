package dev.scorpiontaj.encore.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import dev.scorpiontaj.encore.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer Encore release that carries an Android package. */
data class Release(
    val version: String,
    val page: String,
    /** The release's changes, one entry per CHANGELOG.md list item. */
    val notes: List<String>,
    val apkName: String,
    val apkUrl: String,
    val sumsUrl: String?,
)

/**
 * Updates Encore from its GitHub releases, the same ones the desktop app updates from. A release
 * ships the macOS disk image and the Android package side by side; this side takes the package,
 * checks it against SHA256SUMS and hands it to the system installer.
 */
object Updates {
    private const val LATEST = "https://api.github.com/repos/scorpionTaj/encore/releases/latest"
    private const val SUMS = "SHA256SUMS"
    /** The suffix of the release asset Android installs, mirroring the desktop's `-macos-arm64.dmg`. */
    private const val PACKAGE_SUFFIX = "-android-arm64.apk"
    private const val AGENT = "Encore-Android/${BuildConfig.VERSION_NAME} (https://github.com/scorpionTaj/encore)"
    private const val TIMEOUT_MS = 20_000

    /** The latest release when it is newer than this build and carries an APK, else null. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val json = JSONObject(text(LATEST, accept = "application/vnd.github+json"))
        val version = json.getString("tag_name").removePrefix("v")
        if (!newer(version, BuildConfig.VERSION_NAME)) return@withContext null
        val assets = json.getJSONArray("assets").let { array -> List(array.length(), array::getJSONObject) }
        val apk = assets.firstOrNull { it.getString("name").endsWith(PACKAGE_SUFFIX) } ?: return@withContext null
        Release(
            version = version,
            page = json.getString("html_url"),
            notes = notes(json.optString("body")),
            apkName = apk.getString("name"),
            apkUrl = apk.getString("browser_download_url"),
            sumsUrl = assets.firstOrNull { it.getString("name") == SUMS }?.getString("browser_download_url"),
        )
    }

    /** Downloads the release's APK into the cache and keeps it only when its hash matches. */
    suspend fun download(context: Context, release: Release): File = withContext(Dispatchers.IO) {
        val sums = text(checkNotNull(release.sumsUrl) { "update: the release lists no checksums" })
        val expected = sums.lines().map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].trimStart('*') == release.apkName }?.get(0)?.lowercase()
            ?: error("update: no checksum for ${release.apkName}")
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, release.apkName)
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = open(release.apkUrl)
        try {
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            file.delete()
            error("update: checksum mismatch")
        }
        file
    }

    /**
     * Opens a system install session for [apk]. The system asks the person to confirm; the result
     * arrives at [InstallReceiver]. The caller checks `canRequestPackageInstalls` first.
     */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("encore.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, InstallReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(status.intentSender)
        }
    }

    private fun open(url: String, accept: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", AGENT)
            accept?.let { setRequestProperty("Accept", it) }
            check(responseCode == HttpURLConnection.HTTP_OK) { "update: HTTP $responseCode for $url" }
        }

    private fun text(url: String, accept: String? = null): String {
        val connection = open(url, accept)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** True when [offered] is a higher major.minor.patch than [running]. */
    internal fun newer(offered: String, running: String): Boolean {
        fun numbers(version: String) = version.substringBefore('-').split('.').mapNotNull(String::toIntOrNull)
            .takeIf { it.size == 3 }
        val a = numbers(offered) ?: return false
        val b = numbers(running) ?: return true
        return compareValuesBy(a, b, { it[0] }, { it[1] }, { it[2] }) > 0
    }

    /** The `- ` list items of a release body, with wrapped lines joined back onto their item. */
    private fun notes(body: String): List<String> = buildList {
        for (line in body.lines()) {
            when {
                line.startsWith("- ") -> add(line.removePrefix("- ").trim())
                line.startsWith("  ") && isNotEmpty() && line.isNotBlank() -> set(lastIndex, last() + " " + line.trim())
            }
        }
    }
}

/** Receives the install session's status and shows the system confirmation when it asks for one. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Log.w("update", "install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
