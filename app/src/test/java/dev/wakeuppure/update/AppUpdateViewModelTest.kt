package dev.wakeuppure.update

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.wakeuppure.data.update.AppRelease
import dev.wakeuppure.data.update.ApkDownloader
import dev.wakeuppure.data.update.ReleaseSource
import dev.wakeuppure.data.update.UpdateException
import dev.wakeuppure.ui.update.UpdateDownload
import dev.wakeuppure.ui.update.AppUpdateViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = 24 * 60 * 60 * 1000L
    private val release = AppRelease("0.3.0", "新版说明", "https://github.com/Qi-huanye/Schedule/releases/tag/v0.3.0")
    private lateinit var app: Application
    private val store = ViewModelStore()
    private var now = 10 * day
    private var calls = 0

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("app_updates", 0).edit().clear().commit()
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(source: ReleaseSource = ReleaseSource { calls++; release },
        downloader: ApkDownloader = ApkDownloader { _, _ -> error("No download expected") }): AppUpdateViewModel =
        AppUpdateViewModel(app, source, { now }, "0.2.0", downloader).also { store.put("model-${now}-${System.nanoTime()}", it) }

    @Test fun downloadReportsProgressThenBecomesReady() = runTest(dispatcher) {
        val finished = CompletableDeferred<File>()
        val vm = model(downloader = ApkDownloader { _, progress -> progress(5, 10); finished.await() })
        vm.checkForUpdates(manual = true); advanceUntilIdle()
        vm.download(); runCurrent()
        assertEquals(UpdateDownload.Running(5, 10), vm.state.value.download)
        val file = File(app.cacheDir, "Schedule-0.3.0.apk")
        finished.complete(file); advanceUntilIdle()
        assertEquals(UpdateDownload.Ready(file), vm.state.value.download)
        vm.dismissUpdate()
        assertEquals(UpdateDownload.Idle, vm.state.value.download)
        assertNull(vm.state.value.release)
    }

    @Test fun cancelledDownloadIgnoresItsLateResult() = runTest(dispatcher) {
        val finished = CompletableDeferred<File>()
        val vm = model(downloader = ApkDownloader { _, _ -> finished.await() })
        vm.checkForUpdates(manual = true); advanceUntilIdle()
        vm.download(); runCurrent()
        vm.cancelDownload()
        finished.complete(File(app.cacheDir, "late.apk")); advanceUntilIdle()
        assertEquals(UpdateDownload.Idle, vm.state.value.download)
        assertEquals(release, vm.state.value.release)
    }

    @Test fun downloadFailuresKeepTheDialogWithAMessage() = runTest(dispatcher) {
        var failure: Exception = UpdateException("安装包校验失败，请重试")
        val vm = model(downloader = ApkDownloader { _, _ -> throw failure })
        vm.checkForUpdates(manual = true); advanceUntilIdle()
        vm.download(); advanceUntilIdle()
        assertEquals(UpdateDownload.Failed("安装包校验失败，请重试"), vm.state.value.download)
        failure = IOException("reset")
        vm.download(); advanceUntilIdle()
        assertEquals(UpdateDownload.Failed("下载失败，请检查网络后重试。"), vm.state.value.download)
        assertEquals(release, vm.state.value.release)
    }

    @Test fun automaticChecksAreThrottledAcrossRecreation() = runTest(dispatcher) {
        val vm = model()
        vm.checkForUpdates(); advanceUntilIdle()
        assertEquals(release, vm.state.value.release)
        vm.dismissUpdate()
        vm.checkForUpdates(); advanceUntilIdle()
        assertEquals(1, calls)
        val recreated = model()
        recreated.checkForUpdates(); advanceUntilIdle()
        assertEquals(1, calls)
        now += day
        recreated.checkForUpdates(); advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun disabledAutomaticChecksPersistButManualChecksStillWork() = runTest(dispatcher) {
        val vm = model()
        vm.setAutoCheckEnabled(false)
        val recreated = model()
        assertFalse(recreated.state.value.autoCheckEnabled)
        recreated.checkForUpdates(); advanceUntilIdle()
        assertEquals(0, calls)
        recreated.checkForUpdates(manual = true); advanceUntilIdle()
        assertEquals(release, recreated.state.value.release)
    }

    @Test fun skippedVersionPersistsAndManualCheckCanRediscoverIt() = runTest(dispatcher) {
        val vm = model()
        vm.checkForUpdates(); advanceUntilIdle()
        vm.skipVersion()
        assertNull(vm.state.value.release)
        now += day
        val recreated = model()
        recreated.checkForUpdates(); advanceUntilIdle()
        assertNull(recreated.state.value.release)
        recreated.checkForUpdates(manual = true); advanceUntilIdle()
        assertEquals(release, recreated.state.value.release)
    }

    @Test fun skipDoesNotHideSubsequentVersions() = runTest(dispatcher) {
        val vm = model()
        vm.checkForUpdates(); advanceUntilIdle(); vm.skipVersion()
        now += day
        val next = release.copy(version = "0.4.0")
        val recreated = model(ReleaseSource { next })
        recreated.checkForUpdates(); advanceUntilIdle()
        assertEquals(next, recreated.state.value.release)
    }

    @Test fun dismissDoesNotPermanentlySkipARelease() = runTest(dispatcher) {
        val vm = model()
        vm.checkForUpdates(); advanceUntilIdle(); vm.dismissUpdate()
        now += day
        vm.checkForUpdates(); advanceUntilIdle()
        assertEquals(release, vm.state.value.release)
    }

    @Test fun automaticErrorsAreSilentAndThrottledButManualErrorsAreVisible() = runTest(dispatcher) {
        val vm = model(ReleaseSource { calls++; throw IOException("offline") })
        vm.checkForUpdates(); advanceUntilIdle()
        assertNull(vm.state.value.message)
        assertFalse(vm.state.value.checking)
        vm.checkForUpdates(); advanceUntilIdle()
        assertEquals(1, calls)
        vm.checkForUpdates(manual = true); advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals("检查更新失败，请检查网络后重试。", vm.state.value.message)
    }

    @Test fun concurrentRequestsAreCoalescedAndDisablingCancelsAutomaticResult() = runTest(dispatcher) {
        val result = CompletableDeferred<AppRelease?>()
        val vm = model(ReleaseSource { calls++; result.await() })
        vm.checkForUpdates(); runCurrent()
        vm.checkForUpdates(manual = true); runCurrent()
        assertEquals(1, calls)
        assertTrue(vm.state.value.checking)
        vm.setAutoCheckEnabled(false)
        result.complete(release); advanceUntilIdle()
        assertNull(vm.state.value.release)
        assertFalse(vm.state.value.checking)
        assertNull(vm.state.value.message)
    }

    @Test fun disablingDismissesAlreadyVisibleAutomaticPrompt() = runTest(dispatcher) {
        val vm = model()
        vm.checkForUpdates(); advanceUntilIdle()
        vm.setAutoCheckEnabled(false)
        assertNull(vm.state.value.release)
    }

    @Test fun disablingBeforeCoroutineStartsDoesNotLeaveCheckingStuck() = runTest(dispatcher) {
        val vm = model()
        vm.checkForUpdates()
        vm.setAutoCheckEnabled(false)
        advanceUntilIdle()
        assertFalse(vm.state.value.checking)
        vm.checkForUpdates(manual = true); advanceUntilIdle()
        assertEquals(release, vm.state.value.release)
    }

    @Test fun manualCurrentVersionAndNoReleaseHaveDistinctResults() = runTest(dispatcher) {
        val current = model(ReleaseSource { release.copy(version = "0.2.0") })
        current.checkForUpdates(manual = true); advanceUntilIdle()
        assertNull(current.state.value.release)
        assertEquals("已是最新版本", current.state.value.message)
        val empty = model(ReleaseSource { null })
        empty.checkForUpdates(manual = true); advanceUntilIdle()
        assertEquals("暂未找到可用的正式版本", empty.state.value.message)
    }

    @Test fun clockMovingBackwardsDoesNotDisableChecksIndefinitely() = runTest(dispatcher) {
        val vm = model()
        vm.checkForUpdates(); advanceUntilIdle(); vm.dismissUpdate()
        now -= day
        vm.checkForUpdates(); advanceUntilIdle()
        assertEquals(2, calls)
    }
}
