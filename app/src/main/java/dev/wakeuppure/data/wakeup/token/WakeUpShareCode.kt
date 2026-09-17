package dev.wakeuppure.data.wakeup.token

object WakeUpShareCode {
    fun parse(text: String): String {
        require(text.length <= 4096) { "分享文本过长" }
        val matches = Regex("(?<![0-9a-fA-F])[0-9a-fA-F]{32}(?![0-9a-fA-F])")
            .findAll(text).map { it.value }.distinct().toList()
        require(matches.size == 1) { "请粘贴一条完整分享消息或 32 位分享口令" }
        return matches.single()
    }
}

enum class WakeUpIdentityMode { DEVICE, EXPERIMENTAL }

/** Explicit, user-selected compatibility identity, never a silent fallback. */
object WakeUpExperimentalIdentity {
    const val ANDROID_ID = "0000000000000000"
}
