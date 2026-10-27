package app.killersudoku

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.killersudoku.ui.GameScreen
import app.killersudoku.ui.GameViewModel
import app.killersudoku.ui.HomeScreen
import app.killersudoku.ui.Screen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KillerSudokuTheme {
                App(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.save()
    }
}

@Composable
private fun App(vm: GameViewModel) {
    Scaffold { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val game = vm.game
            if (vm.screen == Screen.GAME && game != null) {
                BackHandler(onBack = vm::showHome)
                GameTimer(vm)
                GameScreen(vm, game)
            } else {
                HomeScreen(vm)
            }
        }
    }
}

/** Zählt die Spielzeit hoch, solange die App im Vordergrund ist. */
@Composable
private fun GameTimer(vm: GameViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(1000)
                vm.tick()
            }
        }
    }
}

@Composable
private fun KillerSudokuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9ECAFF), secondaryContainer = Color(0xFF3A4857))
        else -> lightColorScheme(primary = Color(0xFF1F5FA8), secondaryContainer = Color(0xFFD7E3F7))
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
