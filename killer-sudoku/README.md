# Killer Sudoku für Android

Werbefreie Killer-Sudoku-App (Kotlin + Jetpack Compose). Funktioniert komplett offline, braucht keine
Berechtigungen.

## Funktionen

- **Rätsel-Generator** mit vier Schwierigkeitsgraden (Leicht, Mittel, Schwer, Experte). Jedes Rätsel hat
  garantiert genau eine Lösung – das prüft ein eingebauter Löser beim Erzeugen.
- **Touch-Bedienung:** Feld antippen, Ziffer im Ziffernfeld unten antippen.
- **Notizen:** Mit „Notizen“ in den Notizmodus wechseln (oder lange auf eine Ziffer drücken). Mehrere
  Notizen pro Feld sind möglich; erneutes Tippen entfernt eine Notiz, „Löschen“ entfernt alle Notizen des Feldes.
  Wird eine Ziffer richtig gesetzt, verschwindet sie automatisch aus den Notizen in Zeile, Spalte und Block.
- **Direkte Rückmeldung:** Richtige Ziffern erscheinen blau und werden fixiert, falsche rot (mit Fehlerzähler).
- Rückgängig, Tipp (deckt ein Feld auf), Timer, Hervorhebung gleicher Ziffern und der Zeile/Spalte/des Blocks.
- Hell-/Dunkelmodus, Hoch- und Querformat. Der Spielstand wird automatisch gespeichert.

## APK bekommen

Jeder Push baut die App über GitHub Actions (Workflow „Killer Sudoku (Android)“):

- Auf `main` wird zusätzlich ein GitHub-Release `killer-sudoku-<Nummer>` mit der Datei `KillerSudoku.apk`
  erstellt – am einfachsten direkt auf dem Handy unter *Releases* herunterladen.
- Für andere Branches liegt die APK als Artefakt `KillerSudoku-apk` beim jeweiligen Workflow-Lauf.

Zum Installieren auf dem Handy die Installation aus „unbekannten Quellen“ für den Browser bzw. Dateimanager
erlauben. Alle Builds sind mit demselben Schlüssel (`app/signing.keystore`) signiert, daher lassen sich neue
Versionen einfach über die alte installieren, ohne den Spielstand zu verlieren.

## Selbst bauen

Voraussetzung: JDK 17 und Android SDK (z. B. über Android Studio).

```bash
./gradlew testDebugUnitTest   # Tests für Generator, Löser und Spiellogik
./gradlew assembleRelease     # APK unter app/build/outputs/apk/release/
```

## Aufbau

- `game/` – reine Kotlin-Logik ohne Android-Abhängigkeiten: `Generator` (Lösung erzeugen, Käfige aufteilen,
  Eindeutigkeit herstellen), `Solver` (Backtracking mit Propagation und 45er-Regel), `GameState` (Züge,
  Notizen, Fehler, Speichern).
- `ui/` – Compose-Oberfläche: `Board` (Spielfeld auf einem Canvas), `Screens` (Menü, Spiel, Ziffernfeld),
  `GameViewModel` (Zustand, Rückgängig, Speichern).
- `tools/smoke-test.sh` – Rauchtest, den die CI im Android-Emulator ausführt (mit Screenshots als Artefakt).
