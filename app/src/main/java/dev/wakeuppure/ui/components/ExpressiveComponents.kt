package dev.wakeuppure.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wakeuppure.R
import androidx.compose.animation.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Shared brand mark; decorative when paired with a visible heading. */
@Composable
fun ScheduleMark(modifier: Modifier = Modifier) {
    Icon(painterResource(R.drawable.ic_brand), null, modifier, tint = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DestinationIcon(selected: Boolean, active: ImageVector, inactive: ImageVector) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(selected, transitionSpec = {
        (fadeIn(effects) + scaleIn(spatial, initialScale = 0.8f)) togetherWith
            (fadeOut(effects) + scaleOut(spatial, targetScale = 0.8f))
    }, label = "destinationIcon") { checked -> Icon(if (checked) active else inactive, null) }
}

@Composable
fun PageHeading(title: String, subtitle: String, modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

/** Strong container reserved for the screen's primary piece of information. */
@Composable
fun FocusPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 12.dp),
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
