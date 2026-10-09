package dev.wakeuppure.update

import dev.wakeuppure.ui.update.NoteLine
import dev.wakeuppure.ui.update.releaseNoteLines
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test fun headingsAndBulletsLoseTheirMarkdownMarkers() {
        val notes = "界面精简。\n\n### 界面与操作\n\n- 「我的」成为**唯一**的设置页\n* 支持 `ICS` 导入\n"
        assertEquals(listOf(
            NoteLine("界面精简。", false),
            NoteLine("界面与操作", true),
            NoteLine("• 「我的」成为唯一的设置页", false),
            NoteLine("• 支持 ICS 导入", false),
        ), releaseNoteLines(notes))
        assertEquals(emptyList<NoteLine>(), releaseNoteLines("  \n"))
    }
}
