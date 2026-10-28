# Killer Sudoku für Android

Werbefreie Killer-Sudoku-App (Kotlin + Jetpack Compose). Funktioniert komplett offline, braucht keine
Berechtigungen.

## Funktionen

- **Rätsel-Generator** mit vier Schwierigkeitsgraden. Jedes Rätsel hat garantiert genau eine Lösung. Die
  Schwierigkeit richtet sich danach, welche Lösungstechniken nötig sind:
  - *Leicht*: einfache Schritte und Käfig-Kombinationen
  - *Mittel*: zusätzlich 45er-Regel, Pflichtziffern im Käfig, Block-und-Linie
  - *Schwer*: größere Käfige, teils Ausprobieren nötig
  - *Experte*: große Käfige, nur mit Ausprobieren lösbar
- **Erklärender Tipp:** zeigt den nächsten logischen Schritt mit Begründung und markiert die beteiligten Felder.
  Dazu gehören die Streichungen, die dafür nötig waren, z. B. „Käfig 3 in 2 Feldern: nur 1+2“ oder
  „45er-Regel: …“. Falsche Ziffern werden zuerst gemeldet.
- **Käfig-Kombinationen:** Zum gewählten Feld werden alle Zifferkombinationen seines Käfigs angezeigt. Was nicht
  mehr passt, ist durchgestrichen.
- **Tägliches Rätsel:** jeden Tag ein festes Rätsel (unter der Woche leichter, am Wochenende schwerer), mit Serie.
- **Rätsel-Codes:** Jedes Rätsel hat einen Code wie `M-123456`. Über „Teilen“ verschickst du ihn, über
  „Rätsel-Code eingeben“ bekommt jemand anderes genau dasselbe Rätsel – ganz ohne Server, auch in der
  [Web-Version](../killer-sudoku-web).
- **Statistik:** je Schwierigkeitsgrad gespielt/gelöst, Bestzeit, Durchschnitt, Serien ohne Fehler und ein Verlauf
  der letzten Lösezeiten.
- **Touch-Bedienung und Notizen:** Feld antippen, Ziffer antippen. Notizen über den Notizmodus oder per langem Druck;
  erneutes Tippen entfernt eine Notiz, „Löschen“ alle.
- **Rückmeldung wählbar:** sofort richtig/falsch (Standard) oder *streng* – dann werden nur Regelverstöße markiert.
- **Darstellung:** gestrichelte oder farbige Käfige, Aufleuchten fertiger Zeilen/Spalten/Blöcke, Hell-/Dunkelmodus,
  Hoch- und Querformat. Der Spielstand wird automatisch gespeichert.

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

- `game/` – reine Kotlin-Logik ohne Android-Abhängigkeiten:
  - `Generator`: Lösung erzeugen, Käfige aufteilen, Eindeutigkeit herstellen, Schwierigkeit prüfen
  - `Solver`: schneller Backtracking-Löser für die Eindeutigkeit
  - `LogicSolver`: menschliche Lösungstechniken für Tipps und Bewertung
  - `GameState`, `Stats`, `PuzzleCode`, `CageColors`, `CageHelp`
- `ui/` – Compose-Oberfläche:
  - `Board`: Spielfeld auf einem Canvas
  - `HomeScreen`, `GameScreen`, `StatsScreen`, `SettingsScreen`
  - `GameViewModel`: Zustand, Rückgängig, Speichern, Rätsel vorab im Hintergrund erzeugen

Ein Code erzeugt nur mit derselben Generator-Version dasselbe Rätsel. Ändert sich der Generator, ergeben alte
Codes andere Rätsel. `CompatibilityTest` prüft die App gegen dieselben Referenzrätsel wie die Web-Version
(`killer-sudoku-web/test/fixtures.tsv`).
- `tools/smoke-test.sh` – Rauchtest, den die CI im Android-Emulator ausführt (mit Screenshots als Artefakt).
