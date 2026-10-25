package app.lumen

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.lumen.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            val ctx = LocalContext.current
            LumenTheme(settings) {
                val nav = rememberNavController()
                LaunchedEffect(Unit) {
                    vm.events.collect { e ->
                        when (e) {
                            UiEvent.OpenReview -> nav.navigate("review") { launchSingleTop = true }
                            UiEvent.GoHome -> nav.popBackStack("home", false)
                            is UiEvent.Message -> Toast.makeText(ctx, e.text, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                LumenBackground {
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                vm, onScan = { nav.navigate("scan") }, onExercise = { nav.navigate("exercise") },
                                onManual = { vm.startManual() }, onSettings = { nav.navigate("settings") },
                                onEntry = { vm.openEntry(it) }
                            )
                        }
                        composable("scan") { ScanScreen(vm) { nav.popBackStack() } }
                        composable("review") { ReviewScreen(vm) { nav.popBackStack() } }
                        composable("exercise") { ExerciseScreen(vm) { nav.popBackStack() } }
                        composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
                    }
                    busy?.let { text ->
                        Box(Modifier.fillMaxSize().background(Black.copy(alpha = 0.45f)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
                            val c = LocalLumen.current
                            Column(
                                Modifier.glass(RoundedCornerShape(28.dp)).padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = c.accent)
                                Spacer(Modifier.height(16.dp))
                                T(text, 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
