package dev.wakeuppure.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.wakeuppure.BuildConfig
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A failure whose message can be shown to the user as-is. */
class UpdateException(message: String) : IOException(message)

fun interface ApkDownloader {
    /** Downloads and verifies the release APK; [onProgress] receives bytes read and the expected total. */
    suspend fun download(release: AppRelease, onProgress: (Long, Long) -> Unit): File

    fun clean() {}
}

private const val MAX_APK_BYTES = 200L * 1024 * 1024
private val safeApkName = Regex("[A-Za-z0-9._-]+\\.apk")

/** Reads the `sha256  name` line for [name] from a SHA256SUMS.txt file. */
fun parseChecksum(sums: String, name: String): String? = sums.lineSequence()
    .map { it.trim().split(Regex("\\s+"), limit = 2) }
    .firstOrNull { it.size == 2 && it[1].removePrefix("*") == name && it[0].matches(Regex("[0-9a-fA-F]{64}")) }
    ?.get(0)?.lowercase()

class UpdateDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : ApkDownloader {
    private val directory get() = File(context.cacheDir, "updates")

    override suspend fun download(release: AppRelease, onProgress: (Long, Long) -> Unit): File {
        val name = release.apkName
        val apkUrl = release.apkUrl
        val checksumUrl = release.checksumUrl
        if (name == null || apkUrl == null || checksumUrl == null) throw UpdateException("此版本不支持应用内更新")
        val file = fetchVerifiedApk(client, apkUrl, checksumUrl, name, release.apkSize, directory, onProgress)
        withContext(Dispatchers.IO) {
            runCatching { verifyArchive(file) }.onFailure { file.delete() }.getOrThrow()
        }
        return file
    }

    /** Drops downloads left by an earlier session; the installer has its own copy by then. */
    override fun clean() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun verifyArchive(file: File) {
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = manager.getPackageArchiveInfo(file.path, flags) ?: throw UpdateException("安装包无法解析，请重试")
        if (archive.packageName != context.packageName) throw UpdateException("安装包与当前应用不符")
        if (!isNewerVersion(archive.versionName.orEmpty(), BuildConfig.VERSION_NAME)) throw UpdateException("安装包版本不正确")
        // The system installer rejects a different signer anyway; checking here gives a clearer message.
        val downloaded = archive.signers()
        val installed = manager.getPackageInfo(context.packageName, flags).signers()
        if (!downloaded.isNullOrEmpty() && !installed.isNullOrEmpty() && downloaded.intersect(installed).isEmpty()) {
            throw UpdateException("安装包签名与当前应用不一致")
        }
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.signers(): Set<String>? = if (Build.VERSION.SDK_INT >= 28) {
    signingInfo?.let { info -> (if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory) }
        ?.map { it.toCharsString() }?.toSet()
} else signatures?.map { it.toCharsString() }?.toSet()

/**
 * Streams the APK next to its final name, hashing on the fly. It only gets the final name once the
 * hash matches the published checksum, so an interrupted or tampered download is never installed.
 */
internal suspend fun fetchVerifiedApk(client: OkHttpClient, apkUrl: String, checksumUrl: String, name: String, expectedSize: Long,
    directory: File, onProgress: (Long, Long) -> Unit): File {
    if (!safeApkName.matches(name)) throw UpdateException("安装包名称无效")
    val sums = client.fetch(checksumUrl) { response ->
        val body = response.body ?: throw IOException("Empty checksum response")
        if (body.contentLength() > 64 * 1024) throw IOException("Checksum file too large")
        body.string()
    }
    val expected = parseChecksum(sums, name) ?: throw UpdateException("没有找到安装包校验值")
    directory.mkdirs()
    directory.listFiles()?.forEach { it.delete() }
    val part = File(directory, "$name.part")
    val target = File(directory, name)
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        client.fetch(apkUrl) { response ->
            val body = response.body ?: throw IOException("Empty APK response")
            val total = body.contentLength().takeIf { it > 0 } ?: expectedSize
            if (total > MAX_APK_BYTES) throw UpdateException("安装包过大")
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var reported = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        read += count
                        if (read > MAX_APK_BYTES) throw UpdateException("安装包过大")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        if (read - reported >= 256 * 1024) { reported = read; onProgress(read, total) }
                    }
                    onProgress(read, maxOf(total, read))
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) throw UpdateException("安装包校验失败，请重试")
        if (!part.renameTo(target)) throw IOException("Cannot store the downloaded APK")
        return target
    } catch (e: Throwable) {
        part.delete()
        throw e
    }
}

/** Blocking GET on the IO pool; cancelling the coroutine closes the connection right away. */
private suspend fun <T> OkHttpClient.fetch(url: String, read: (Response) -> T): T = coroutineScope {
    val call = newCall(Request.Builder().url(url).header("User-Agent", "Schedule/${BuildConfig.VERSION_NAME}").build())
    val watcher = launch { try { awaitCancellation() } finally { call.cancel() } }
    try {
        withContext(Dispatchers.IO) {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                read(response)
            }
        }
    } catch (e: IOException) {
        ensureActive()
        throw e
    } finally {
        watcher.cancel()
    }
}
