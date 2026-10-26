package app.lumen.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home),
    Tab("activity", "Activity", Icons.Rounded.DirectionsWalk),
    Tab("settings", "Settings", Icons.Rounded.Settings)
)

val tabRoutes: List<String> = tabs.map { it.route }

/** Glass tab pill (blurs whatever scrolls under it) with a separate round Scan button beside it. */
@Composable
fun TabBar(route: String?, onTab: (String) -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalLumen.current
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.weight(1f).glass(RoundedCornerShape(50), blur = true).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { t ->
                val sel = route == t.route
                val bg by animateColorAsState(if (sel) c.accent.copy(alpha = 0.22f) else Color.Transparent, tween(220), label = "tab")
                Column(
                    Modifier.weight(1f).bounceClick(scale = 0.92f) { if (!sel) onTab(t.route) }
                        .clip(RoundedCornerShape(50)).background(bg).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(t.icon, t.label, tint = if (sel) c.accent else c.dim, modifier = Modifier.size(22.dp))
                    T(t.label, 10.sp, if (sel) FontWeight.SemiBold else FontWeight.Normal, if (sel) c.text else c.dim)
                }
            }
        }
        Box(
            Modifier.size(62.dp).bounceClick(scale = 0.9f, onClick = onScan)
                .clip(CircleShape).background(Brush.verticalGradient(listOf(c.accent, c.accent.copy(alpha = 0.75f)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.CameraAlt, "Scan", tint = if (c.dark) Color.Black else Color.White, modifier = Modifier.size(28.dp)) }
    }
}
