package dev.wakeuppure.data.wakeup.token

import android.content.Context
import android.provider.Settings
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class WakeUpProtocolProfile(
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val channel: String,
    val publicToken: String,
    val certificateHexMd5: String,
    val apiHost: String = "https://api.wakeup.fun",
) {
    fun validate() {
        require(packageName.isNotBlank() && versionCode > 0 && versionName.isNotBlank() && channel.isNotBlank() && publicToken.isNotBlank()) { "协议配置不完整，请导入有效的 WakeUp 协议配置" }
        require(Regex("[0-9a-f]{32}").matches(certificateHexMd5)) { "协议配置中的证书摘要无效" }
        val url = apiHost.toHttpUrlOrNull()
        require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null && url.encodedPath == "/") { "协议服务地址必须是 HTTPS 根地址" }
    }
}

fun interface DeviceIdentityProvider { fun androidId(): String }

class AndroidDeviceIdentityProvider(context: Context) : DeviceIdentityProvider {
    private val resolver = context.applicationContext.contentResolver
    override fun androidId(): String = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID).orEmpty()
}
