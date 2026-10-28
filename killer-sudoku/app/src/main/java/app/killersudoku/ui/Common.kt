package app.killersudoku.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.killersudoku.game.Difficulty

/** Kopfzeile mit Zurück-Knopf. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

val Difficulty.description: String
    get() = when (this) {
        Difficulty.EASY -> "Kleine Käfige, einige Ziffern vorgegeben"
        Difficulty.MEDIUM -> "Braucht die 45er-Regel und Käfig-Kombinationen"
        Difficulty.HARD -> "Größere Käfige, manchmal Ausprobieren nötig"
        Difficulty.EXPERT -> "Große Käfige, nur mit Ausprobieren lösbar"
    }

fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
