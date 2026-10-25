@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.wakeuppure.ui.settings

import androidx.compose.animation.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.BarcodeFormat
import dev.wakeuppure.data.shiguangxu.ShiguangxuClient
import dev.wakeuppure.data.shiguangxu.ShiguangxuImportStage
import dev.wakeuppure.data.shiguangxu.QrImageDecoder
import dev.wakeuppure.domain.model.ScheduleData
import dev.wakeuppure.ui.components.FocusPanel
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

private enum class ImportOperation { LOGIN, IMAGE, PREVIEW, IMPORT }

@Composable
internal fun ShiguangxuImportScreen(onClose: () -> Unit, onParsed: (List<ScheduleData>) -> Unit) {
    val session = remember { ShiguangxuClient() }
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(false) }
    var area by remember { mutableStateOf("86") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<JsonObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<ImportOperation?>(null) }
    var progressMessage by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun close() { job?.cancel(); session.clear(); password = ""; mobile = ""; onClose() }
    DisposableEffect(session) { onDispose { session.clear() } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { password = ""; showPassword = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun runTask(kind: ImportOperation, block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        operation = kind
        progressMessage = when (kind) {
            ImportOperation.LOGIN -> "正在登录时光序"
            ImportOperation.IMAGE -> "正在识别图片中的二维码"
            ImportOperation.PREVIEW -> "正在读取分享课表信息"
            ImportOperation.IMPORT -> "正在获取完整课程"
        }
        job = scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message?.takeIf { it.startsWith("时光序导入失败：") } ?: "请求未完成，请重试" }
            finally { busy = false; operation = null }
        }
    }
    fun recognize(scanned: String) {
        scanning = false
        preview = null
        runTask(ImportOperation.PREVIEW) { preview = session.preview(scanned) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            preview = null
            runTask(ImportOperation.IMAGE) {
                val scanned = withContext(Dispatchers.IO) {
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalArgumentException("时光序导入失败：无法读取所选图片")
                        input.use { QrImageDecoder.decode(it) }
                    } catch (e: CancellationException) { throw e }
                    catch (e: IllegalArgumentException) { throw e }
                    catch (_: Exception) { throw IllegalArgumentException("时光序导入失败：无法读取所选图片，请重新选择") }
                }
                progressMessage = "正在读取分享课表信息"
                preview = session.preview(scanned)
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scanning = granted
        if (!granted) message = "未获得相机权限，可以从相册选择二维码图片"
    }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val cameraSize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    Scaffold(bottomBar = {
        AnimatedVisibility(busy && loggedIn, enter = fadeIn(effects) + expandVertically(cameraSize),
            exit = fadeOut(effects) + shrinkVertically(cameraSize)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(progressMessage, style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }, topBar = {
        TopAppBar(title = { Text("时光序导入") }, navigationIcon = {
            IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }) { inset ->
        AnimatedContent(loggedIn, modifier = Modifier.fillMaxSize().padding(inset), transitionSpec = {
            (fadeIn(effects) + slideInHorizontally(spatial) { if (targetState) it / 12 else -it / 12 }) togetherWith
                (fadeOut(effects) + slideOutHorizontally(spatial) { if (targetState) -it / 12 else it / 12 })
        }, label = "shiguangxuImportStep") { authenticated ->
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FocusPanel(Modifier.fillMaxWidth()) {
                    Icon(if (authenticated) Icons.Default.QrCodeScanner else Icons.Default.AccountCircle, null, Modifier.size(40.dp))
                    Text(if (authenticated) "扫描分享课表" else "登录时光序", style = MaterialTheme.typography.headlineMedium)
                    Text(if (operation == ImportOperation.IMPORT) "3 / 3 · 获取课程，准备导入预览"
                        else if (authenticated) "2 / 3 · 扫描二维码，或从相册选择课表二维码图片" else "1 / 3 · 使用手机号与密码登录，仅用于本次导入",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (!authenticated) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(area, { area = it.filter(Char::isDigit).take(4) }, label = { Text("国家码") },
                            prefix = { Text("+") }, singleLine = true, enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.width(112.dp))
                        OutlinedTextField(mobile, { mobile = it.filter(Char::isDigit).take(20) }, label = { Text("手机号") },
                            singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(password, { password = it }, label = { Text("密码") }, enabled = !busy, singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showPassword) "隐藏密码" else "显示密码")
                        } })
                    Text("账号密码直接发送至时光序。不会保存账号、密码或登录状态，退出本次导入后清除会话。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        val submitted = password; password = ""; showPassword = false
                        runTask(ImportOperation.LOGIN) { session.login(area, mobile, submitted); mobile = ""; loggedIn = true }
                    }, enabled = !busy && area.isNotBlank() && mobile.isNotBlank() && password.isNotBlank(),
                        colors = if (busy) ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary) else ButtonDefaults.buttonColors(),
                        shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                color = LocalContentColor.current, trackColor = Color.Transparent)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(if (busy) "正在登录…" else "登录并继续", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                } else {
                    Button(onClick = {
                        preview = null; message = null
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true
                        else permission.launch(Manifest.permission.CAMERA)
                    },
                        enabled = !busy, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(12.dp)); Text("扫描二维码")
                    }
                    AnimatedVisibility(scanning, enter = fadeIn(effects) + expandVertically(cameraSize),
                        exit = fadeOut(effects) + shrinkVertically(cameraSize)) {
                        Column {
                            ShiguangxuCamera(scanning) { scanned -> recognize(scanned) }
                            TextButton(onClick = { scanning = false }) { Text("关闭相机") }
                        }
                    }
                    FilledTonalButton(onClick = {
                        scanning = false
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, enabled = !busy, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(12.dp)); Text("从相册选择")
                    }
                    AnimatedContent(preview, transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                        label = "shiguangxuQrPreview") { result ->
                        result?.let { term ->
                            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(term["termName"]?.jsonPrimitive?.contentOrNull ?: "分享课表", style = MaterialTheme.typography.titleLarge)
                                    Text("${term["weekCount"]?.jsonPrimitive?.contentOrNull ?: "—"} 周 · 确认后获取完整课程并预览")
                                    Text("获取课程会在你的时光序账号中新建一份课表，并切换为当前课表。原有课表会保留；取消本地导入不会删除这份新建课表。",
                                        style = MaterialTheme.typography.bodyMedium)
                                    Button(onClick = { runTask(ImportOperation.IMPORT) {
                                        scanning = false
                                        val data = session.importShared(term) { stage ->
                                            progressMessage = when (stage) {
                                                ShiguangxuImportStage.CHECKING_TIMETABLES -> "正在检查时光序中的已有课表"
                                                ShiguangxuImportStage.CREATING_TIMETABLE -> "正在时光序中新建课表"
                                                ShiguangxuImportStage.READING_SYNC_VERSION -> "正在准备课程同步"
                                                ShiguangxuImportStage.READING_TIMETABLE -> "正在读取新建课表"
                                                ShiguangxuImportStage.SYNCING_COURSES -> "正在同步课程信息"
                                                ShiguangxuImportStage.PREPARING_PREVIEW -> "正在整理课程，准备预览"
                                            }
                                        }
                                        session.clear()
                                        onParsed(data)
                                    } }, enabled = !busy, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                                        Text("新建并获取课程")
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { scanning = false; session.clear(); loggedIn = false; preview = null }, enabled = !busy) { Text("更换账号") }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ShiguangxuCamera(enabled: Boolean, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val callback by rememberUpdatedState(onCode)
    val active by rememberUpdatedState(enabled)
    val camera = remember { BarcodeView(context).apply {
        decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) { if (active) callback(result.text) }
        })
    } }
    AndroidView(factory = { camera }, modifier = Modifier.fillMaxWidth().height(280.dp).clip(MaterialTheme.shapes.extraLarge))
    DisposableEffect(camera, lifecycle, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && enabled) camera.resume()
            if (event == Lifecycle.Event.ON_PAUSE) camera.pause()
        }
        lifecycle.addObserver(observer)
        if (enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) camera.resume() else camera.pause()
        onDispose { lifecycle.removeObserver(observer); camera.pause() }
    }
}
