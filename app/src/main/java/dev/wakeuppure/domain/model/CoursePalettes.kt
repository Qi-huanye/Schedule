package dev.wakeuppure.domain.model

data class CoursePalette(val id: String, val name: String, val colors: List<String>)
object CoursePalettes {
    val all = listOf(
        CoursePalette("soft", "柔和缤纷", listOf("#C1E8DE", "#BFDDF1", "#E4D5F3", "#F3D3D2", "#F4E3B5", "#CFE4BD")),
        CoursePalette("forest", "森林薄荷", listOf("#B5D9C0", "#D2E3B5", "#A9D7D1", "#DDE7CB", "#BDCEAD", "#B8D8E0")),
        CoursePalette("sky", "晴空蓝紫", listOf("#B8D7F0", "#CBD3F1", "#DCD0EE", "#BFE1E9", "#D8DDF7", "#C7E4F2")),
        CoursePalette("sunset", "暖日桃橙", listOf("#F3CABC", "#F6DCB7", "#ECC7D3", "#F2E6BD", "#E4CEDF", "#EED4BB"))
    )
    fun find(id: String) = all.find { it.id == id }
    fun color(id: String, index: Int): String = (find(id) ?: all.first()).colors.let { it[index % it.size] }
}
