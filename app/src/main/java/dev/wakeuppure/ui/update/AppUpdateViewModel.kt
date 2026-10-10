package dev.wakeuppure.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wakeuppure.BuildConfig
import dev.wakeuppure.data.update.AppRelease
import dev.wakeuppure.data.update.AppReleaseRepository
import dev.wakeuppure.data.update.ApkDownloader
import dev.wakeuppure.data.update.UpdateDownloader
import dev.wakeuppure.data.update.UpdateException
import dev.wakeuppure.data.update.ReleaseSource
import dev.wakeuppure.data.update.isNewerVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    data class Running(val read: Long, val total: Long) : UpdateDownload
    data class Ready(val file: File) : UpdateDownload
    data class Failed(val message: String) : UpdateDownload
}

data class AppUpdateState(
    val autoCheckEnabled: Boolean = true,
    val checking: Boolean = false,
    val release: AppRelease? = null,
    val message: String? = null,
    val download: UpdateDownload = UpdateDownload.Idle,
)

class AppUpdateViewModel(
    application: Application,
    source: ReleaseSource?,
    private val now: () -> Long,
    private val currentVersion: String,
    downloader: ApkDownloader? = null,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, null, System::currentTimeMillis, BuildConfig.VERSION_NAME)

    private val releases by lazy { source ?: AppReleaseRepository() }
    private val downloads by lazy { downloader ?: UpdateDownloader(application) }

    private val preferences = application.getSharedPreferences("app_updates", 0)
    private val mutableState = MutableStateFlow(AppUpdateState(autoCheckEnabled = preferences.getBoolean("automatic", true)))
    val state = mutableState.asStateFlow()
    private var checkJob: Job? = null
    private var automaticRequest = false
    private var downloadJob: Job? = null
    private var cleanupJob: Job? = null

    private fun prepare() {
        if (cleanupJob == null) cleanupJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { downloads.clean() }
        }
    }

    fun checkForUpdates(manual: Boolean = false) {
        // The automatic host mounts after the first draw. Manual checks also prepare on demand.
        prepare()
        if (state.value.checking) return
        val timestamp = now()
        val elapsed = timestamp - preferences.getLong("last_attempt", 0)
        if (!manual && (!state.value.autoCheckEnabled ||
                (preferences.contains("last_attempt") && elapsed in 0 until CHECK_INTERVAL_MS))) return
        preferences.edit().putLong("last_attempt", timestamp).apply()
        automaticRequest = !manual
        mutableState.update { it.copy(checking = true, message = null) }
        checkJob = viewModelScope.launch {
            try {
                val release = kotlinx.coroutines.withContext(Dispatchers.IO) { releases.latest() }
                val newer = release != null && isNewerVersion(release.version, currentVersion)
                val skipped = release?.version?.substringBefore('+') == preferences.getString("skipped_version", null)
                mutableState.update { state ->
                    state.copy(
                        release = release.takeIf { newer && (manual || (!skipped && state.autoCheckEnabled)) },
                        message = if (!manual || newer) null else if (release == null)
                            "暂未找到可用的正式版本" else "已是最新版本",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (manual) mutableState.update { it.copy(message = "检查更新失败，请检查网络后重试。") }
            }
        }.also { job ->
            // Also runs if the job is cancelled before its body starts.
            job.invokeOnCompletion { mutableState.update { it.copy(checking = false) } }
        }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("automatic", enabled).apply()
        mutableState.update { it.copy(autoCheckEnabled = enabled, release = if (enabled) it.release else null) }
        if (!enabled && automaticRequest) checkJob?.cancel()
    }

    fun dismissUpdate() {
        downloadJob?.cancel()
        mutableState.update { it.copy(release = null, download = UpdateDownload.Idle) }
    }

    fun download() {
        val release = state.value.release ?: return
        if (state.value.download is UpdateDownload.Running) return
        prepare()
        mutableState.update { it.copy(download = UpdateDownload.Running(0, release.apkSize)) }
        downloadJob = viewModelScope.launch {
            val result = try {
                // Cleanup must never remove a file that this download has just started writing.
                cleanupJob?.join()
                UpdateDownload.Ready(downloads.download(release) { read, total ->
                    mutableState.update { if (it.download is UpdateDownload.Running) it.copy(download = UpdateDownload.Running(read, total)) else it }
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                UpdateDownload.Failed(e.message ?: DOWNLOAD_FAILED)
            } catch (_: Exception) {
                UpdateDownload.Failed(DOWNLOAD_FAILED)
            }
            mutableState.update { it.copy(download = result) }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        mutableState.update { it.copy(download = UpdateDownload.Idle) }
    }

    fun skipVersion() {
        val release = state.value.release ?: return
        preferences.edit().putString("skipped_version", release.version.substringBefore('+')).apply()
        dismissUpdate()
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val DOWNLOAD_FAILED = "下载失败，请检查网络后重试。"
    }
}
