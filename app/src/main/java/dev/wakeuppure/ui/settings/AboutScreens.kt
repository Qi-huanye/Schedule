@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.wakeuppure.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import dev.wakeuppure.BuildConfig
import dev.wakeuppure.ui.background.backgroundScreenColor
import dev.wakeuppure.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal const val PROJECT_URL = "https://github.com/Qi-huanye/Schedule"

@Composable
fun AboutScreen(onClose: () -> Unit, onLicenses: () -> Unit, onPrivacy: () -> Unit) {
    val uri = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(containerColor = backgroundScreenColor(), snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("关于 Schedule") }, navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { FocusPanel(Modifier.padding(20.dp).fillMaxWidth()) {
                ScheduleMark(Modifier.size(80.dp))
                Text("Schedule", style = MaterialTheme.typography.headlineLarge)
                Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
                Text("本地优先的课程表，让每一周更从容。", style = MaterialTheme.typography.bodyLarge)
            } }
            item { SettingsSection("项目") {
                SettingRow("GitHub 源代码", "Qi-huanye/Schedule") {
                    runCatching { uri.openUri(PROJECT_URL) }.onFailure {
                        scope.launch { snackbar.showSnackbar("无法打开链接，请检查是否已安装浏览器。") }
                    }
                }
            } }
            item { SettingsSection("许可与隐私") {
                SettingRow("开源许可", "应用内查看完整组件清单和许可正文", onClick = onLicenses)
                SettingRow("隐私声明", "了解本地数据、网络请求和系统权限", onClick = onPrivacy)
            } }
        }
    }
}

@Serializable
internal data class OpenSourceComponent(val id: String, val name: String, val licenses: List<String>,
    val projectUrl: String = "", val kind: String = "maven", val notice: String = "")

@Serializable
internal data class OpenSourceCatalog(val components: List<OpenSourceComponent>, val texts: Map<String, String>)

private sealed interface LicenseLoad {
    data object Loading : LicenseLoad
    data class Ready(val catalog: OpenSourceCatalog) : LicenseLoad
    data object Failed : LicenseLoad
}

@Composable
fun LicensesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val loaded by produceState<LicenseLoad>(LicenseLoad.Loading, context, attempt) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val data = context.assets.open("legal/catalog.json").bufferedReader().use { it.readText() }
                LicenseLoad.Ready(Json.decodeFromString<OpenSourceCatalog>(data))
            }.getOrElse { LicenseLoad.Failed }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(containerColor = backgroundScreenColor(), topBar = { TopAppBar(title = { Text("开源许可") }, navigationIcon = {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
    }) }) { padding ->
        when (val state = loaded) {
            LicenseLoad.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            LicenseLoad.Failed -> Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("无法读取开源许可")
                Button(onClick = { attempt++ }, shapes = ButtonDefaults.shapes()) { Text("重试") }
            }
            is LicenseLoad.Ready -> {
                val components = state.catalog.components.filter {
                    query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) || it.licenses.any { license -> license.contains(query, true) }
                }
                LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("本应用使用以下开源项目，点击条目查看许可全文。",
                            modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(query, { query = it }, label = { Text("查找开源组件") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (components.isEmpty()) item { Text("未找到匹配的组件", Modifier.padding(vertical = 16.dp)) }
                    items(components, key = { it.id }) { component -> LicenseEntry(component, state.catalog.texts) }
                }
            }
        }
    }
}

@Composable
private fun LicenseEntry(component: OpenSourceComponent, texts: Map<String, String>) {
    var expanded by rememberSaveable(component.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Surface(onClick = { expanded = !expanded }, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(component.name, style = MaterialTheme.typography.titleMedium)
                        Text(component.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(component.licenses.joinToString("、"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        if (expanded) "收起许可" else "展开许可")
                }
            }
            AnimatedVisibility(expanded) {
                SelectionContainer {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (component.notice.isNotBlank()) Text(component.notice, style = MaterialTheme.typography.bodySmall)
                        if (component.projectUrl.isNotBlank()) Text(component.projectUrl, style = MaterialTheme.typography.bodySmall)
                        component.licenses.forEach { license -> Text(texts.getValue(license), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(onClose: () -> Unit) {
    val sections = listOf(
        "本地存储" to "课表、课程、作息、主题设置和背景图片保存在本机。应用不提供账号，不包含广告、统计 SDK 或用户追踪。背景图片的缩放、方向校正和取色在本机完成，不会上传图片。",
        "文件与备份" to "导入、导出和备份使用系统文件选择器，不请求全盘存储权限。备份仅在你主动导出时保存到所选位置；课表备份不包含本机背景图片、背景配色开关或预设主题设置。系统云备份已关闭。",
        "应用更新" to "自动检查更新默认开启，每 24 小时最多请求一次 GitHub 正式版本信息，可在“我的”关闭或手动检查。请求不携带课表、背景图片或设备标识；GitHub 可见普通网络请求的来源 IP 和应用版本。更新安装包在你点击更新后下载，校验后由系统安装程序要求你确认。",
        "分享口令导入" to "仅在你点击“联网获取”后，向协议配置指定的 HTTPS 服务请求课表。普通模式包含派生设备标识、设备型号及必要协议字段；实验兼容模式使用公开兼容身份，不读取本机 Android ID。应用不读取电话、IMEI 或通讯录，不记录分享内容、密钥或完整设备标识。",
        "系统权限" to "课前提醒可按需申请通知权限。安装已验证的更新时，系统可能要求允许本应用安装未知应用。你主动打开 GitHub 或其他外部链接后，由浏览器处理网络访问。",
        "删除与管理" to "你可以在应用内删除课表、移除背景，也可以通过系统“清除应用数据”删除本机存储。主动导出的备份文件由你在所选位置自行管理。项目源码和问题反馈入口见关于页面的 GitHub 链接。",
    )
    Scaffold(containerColor = backgroundScreenColor(), topBar = { TopAppBar(title = { Text("隐私声明") }, navigationIcon = {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
    }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item { Text("你的课程，留在你的设备上。", style = MaterialTheme.typography.headlineMedium) }
            items(sections) { (title, body) -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
    }
}
