package app.lumen

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.graphics.layer.drawLayer
import app.lumen.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val ctx = LocalContext.current
            LumenTheme(settings) {
                val nav = rememberNavController()
                val backEntry by nav.currentBackStackEntryAsState()
                val route = backEntry?.destination?.route

                val goTab: (String) -> Unit = { r ->
                    if (r == "home") nav.popBackStack("home", false)
                    else nav.navigate(r) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                LaunchedEffect(Unit) {
                    vm.events.collect { e ->
                        when (e) {
                            UiEvent.OpenReview -> nav.navigate("review") { launchSingleTop = true }
                            UiEvent.GoHome -> if (!nav.popBackStack("home", false)) nav.navigate("home") { launchSingleTop = true }
                            is UiEvent.Message -> Toast.makeText(ctx, e.text, Toast.LENGTH_LONG).show()
                        }
                    }
                }

                val backdrop = rememberGraphicsLayer()
                CompositionLocalProvider(LocalBackdrop provides backdrop) {
                    Box(Modifier.fillMaxSize()) {
                        // Everything the floating glass can sit on top of is drawn through this recording.
                        // The tab bar and overlays are siblings (not inside it), so they never record themselves.
                        Box(
                            Modifier.fillMaxSize().drawWithContent {
                                val sz = IntSize(size.width.roundToInt(), size.height.roundToInt())
                                backdrop.record(this, layoutDirection, sz) { this@drawWithContent.drawContent() }
                                drawLayer(backdrop)
                            }
                        ) {
                            LumenBackground {
                                // Tabs slide a little in the direction of travel and cross-fade; full-screen pages scale in.
                                fun tabDir(from: String?, to: String?): Int {
                                    val a = tabRoutes.indexOf(from); val b = tabRoutes.indexOf(to)
                                    return if (a < 0 || b < 0) 0 else if (b > a) 1 else -1
                                }
                                NavHost(
                                    nav, startDestination = "home",
                                    enterTransition = {
                                        val d = tabDir(initialState.destination.route, targetState.destination.route)
                                        if (d != 0) fadeIn(tween(240, delayMillis = 60)) + slideInHorizontally(tween(280)) { d * it / 10 }
                                        else fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f)
                                    },
                                    exitTransition = {
                                        val d = tabDir(initialState.destination.route, targetState.destination.route)
                                        if (d != 0) fadeOut(tween(120)) + slideOutHorizontally(tween(280)) { -d * it / 10 }
                                        else fadeOut(tween(160))
                                    },
                                    popEnterTransition = {
                                        val d = tabDir(initialState.destination.route, targetState.destination.route)
                                        if (d != 0) fadeIn(tween(240, delayMillis = 60)) + slideInHorizontally(tween(280)) { d * it / 10 }
                                        else fadeIn(tween(200))
                                    },
                                    popExitTransition = {
                                        val d = tabDir(initialState.destination.route, targetState.destination.route)
                                        if (d != 0) fadeOut(tween(120)) + slideOutHorizontally(tween(280)) { -d * it / 10 }
                                        else fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.96f)
                                    }
                                ) {
                                    composable("home") {
                                        HomeScreen(vm, onActivity = { goTab("activity") }, onManual = { vm.startManual() }, onEntry = { vm.openEntry(it) })
                                    }
                                    composable("activity") { ActivityScreen(vm) }
                                    composable("settings") { SettingsScreen(vm) }
                                    composable("scan") { ScanScreen(vm) { nav.popBackStack() } }
                                    composable("review") { ReviewScreen(vm) { nav.popBackStack() } }
                                }
                            }
                        }

                        if (route in tabRoutes) {
                            TabBar(route, onTab = goTab, onScan = { nav.navigate("scan") }, modifier = Modifier.align(Alignment.BottomCenter))
                        }
                        BusyOverlay(vm)
                    }
                }
            }
        }
    }
}

/** Own composable so progress changes never recompose the rest of the app. */
@Composable
private fun BusyOverlay(vm: AppViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val text = busy ?: return
    val c = LocalLumen.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.glass(RoundedCornerShape(28.dp), blur = true).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = c.accent)
            Spacer(Modifier.height(16.dp))
            T(text, 15.sp)
        }
    }
}
