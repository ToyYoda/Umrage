package app.killersudoku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: GameViewModel) {
    val settings = vm.settings
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader("Einstellungen", vm::showHome)
        SettingRow(
            "Strenge Rückmeldung",
            "Nicht verraten, ob eine Ziffer richtig ist. Markiert werden nur Regelverstöße: doppelte Ziffern " +
                "und falsche Käfigsummen. Fehler werden nicht gezählt, richtige Ziffern bleiben änderbar.",
            settings.strict,
        ) { vm.updateSettings(Settings(it, settings.coloredCages, settings.animations)) }
        SettingRow(
            "Farbige Käfige",
            "Käfige mit Farben statt mit gestrichelten Linien darstellen.",
            settings.coloredCages,
        ) { vm.updateSettings(Settings(settings.strict, it, settings.animations)) }
        SettingRow(
            "Animationen",
            "Fertige Zeilen, Spalten und Blöcke kurz aufleuchten lassen.",
            settings.animations,
        ) { vm.updateSettings(Settings(settings.strict, settings.coloredCages, it)) }
    }
}

@Composable
private fun SettingRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
