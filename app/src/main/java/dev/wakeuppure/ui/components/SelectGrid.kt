package dev.wakeuppure.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Two-tap range selection: the first tap picks a single cell and waits for the other end,
 * the second tap closes the range on either side of it.
 */
data class RangePick(val start: Int, val end: Int, val anchor: Int? = null) {
    fun tap(value: Int): RangePick = when (anchor) {
        null -> RangePick(value, value, value)
        else -> RangePick(minOf(anchor, value), maxOf(anchor, value), null)
    }
}

enum class CellState { NONE, EDGE, INSIDE }

/**
 * A grid of numbered cells. [state] decides how each value is drawn; [marked] adds the
 * small dot used for "this week".
 */
@Composable
fun SelectGrid(values: List<Int>, columns: Int, state: (Int) -> CellState, onTap: (Int) -> Unit,
    label: (Int) -> String = { it.toString() }, marked: (Int) -> Boolean = { false }, describe: (Int) -> String = label,
    trailing: (@Composable (Modifier) -> Unit)? = null) {
    val items: List<Int?> = values + if (trailing != null) listOf(null) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { value ->
                    val cell = Modifier.weight(1f).height(38.dp)
                    if (value == null) trailing?.invoke(cell) else {
                        val s = state(value)
                        val colors = MaterialTheme.colorScheme
                        val (bg, fg) = when (s) {
                            CellState.EDGE -> colors.primary to colors.onPrimary
                            CellState.INSIDE -> colors.primaryContainer to colors.onPrimaryContainer
                            CellState.NONE -> colors.surfaceContainerHighest to colors.onSurface
                        }
                        Box(cell.clip(RoundedCornerShape(10.dp)).background(bg).clickable { onTap(value) }
                            .semantics { selected = s != CellState.NONE; contentDescription = describe(value) },
                            contentAlignment = Alignment.Center) {
                            Text(label(value), color = fg, fontWeight = if (s == CellState.EDGE) FontWeight.SemiBold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium)
                            if (marked(value)) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp)
                                .background(fg, CircleShape))
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Dashed-looking outlined cell used for "add one more". */
@Composable
fun GridAddCell(modifier: Modifier, description: String, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
    }
}

/** Both ends stay visible as tapped; cells in between follow [included] (e.g. odd weeks only). */
fun rangeState(value: Int, start: Int, end: Int, included: (Int) -> Boolean = { true }): CellState = when {
    value < start || value > end -> CellState.NONE
    value == start || value == end -> CellState.EDGE
    !included(value) -> CellState.NONE
    else -> CellState.INSIDE
}
