package dev.wakeuppure.data.update

import dev.wakeuppure.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [apkUrl] and [checksumUrl] are built from the canonical repository like [pageUrl]; they are null
 * when the release lacks a SHA256SUMS.txt asset, in which case only the browser download is offered.
 */
data class AppRelease(
    val version: String, val notes: String, val pageUrl: String,
    val apkName: String? = null, val apkSize: Long = 0, val apkUrl: String? = null, val checksumUrl: String? = null,
)

internal const val CHECKSUM_ASSET = "SHA256SUMS.txt"

fun interface ReleaseSource {
    suspend fun latest(): AppRelease?
}

private val stableVersion = Regex("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")

private fun versionParts(version: String): List<Long>? {
    val match = stableVersion.matchEntire(version) ?: return null
    return match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
}

fun isNewerVersion(candidate: String, current: String): Boolean {
    val next = versionParts(candidate) ?: return false
    val installed = versionParts(current) ?: return false
    return next.zip(installed).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a > b } ?: false
}

class AppReleaseRepository(
    private val endpoint: HttpUrl = "https://api.github.com/repos/Qi-huanye/Schedule/releases/latest".toHttpUrl(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build(),
) : ReleaseSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun latest(): AppRelease? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Schedule/${BuildConfig.VERSION_NAME}")
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    continuation.resume(response.use(::readRelease))
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

    private fun readRelease(response: Response): AppRelease? {
        if (response.code == 404) return null
        if (!response.isSuccessful) throw IOException("Release check HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Empty release response")
        val release = try {
            json.decodeFromString<GitHubRelease>(body)
        } catch (e: SerializationException) {
            throw IOException("Invalid release response", e)
        }
        fun GitHubAsset.available() = state == "uploaded" && size > 0
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.available() }
        if (release.draft || release.prerelease || versionParts(release.tag) == null || apk == null) return null
        val checksum = release.assets.firstOrNull { it.name == CHECKSUM_ASSET && it.available() }
        fun download(name: String) = "https://github.com/Qi-huanye/Schedule/releases/download/".toHttpUrl()
            .newBuilder().addPathSegment(release.tag).addPathSegment(name).build().toString()
        return AppRelease(
            version = release.tag.removePrefix("v"),
            notes = release.body.orEmpty().trim(),
            pageUrl = "https://github.com/Qi-huanye/Schedule/releases/tag/".toHttpUrl()
                .newBuilder().addPathSegment(release.tag).build().toString(),
            apkName = apk.name.takeIf { checksum != null },
            apkSize = apk.size,
            apkUrl = checksum?.let { download(apk.name) },
            checksumUrl = checksum?.let { download(it.name) },
        )
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val body: String? = null,
    val assets: List<GitHubAsset>,
)

@Serializable
private data class GitHubAsset(val name: String, val state: String, val size: Long)
