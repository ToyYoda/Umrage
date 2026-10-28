# Killer Sudoku im Browser

Web-Version der [Android-App](../killer-sudoku): gleiche Spiellogik, gleiche Funktionen, läuft in jedem modernen
Browser – auf dem Handy wie am Rechner. Ohne Werbung, ohne Server, ohne Build-Schritt (reines HTML, CSS und
JavaScript-Module).

## Funktionen

- Vier Schwierigkeitsgrade mit garantiert eindeutiger Lösung, bewertet nach den nötigen Lösungstechniken
- Erklärende Tipps, Käfig-Kombinationen, Notizen, Rückgängig
- Sofortige Rückmeldung oder strenger Modus (nur Regelverstöße), farbige oder gestrichelte Käfige
- Tägliches Rätsel mit Serie, Statistik mit Verlauf der Lösezeiten
- **Rätsel-Codes** wie `M-123456`: derselbe Code ergibt im Browser und in der Android-App genau dasselbe Rätsel.
  Ein Link wie `…/#M-123456` öffnet das Rätsel direkt.
- Spielstand, Statistik und Einstellungen werden im Browser gespeichert (`localStorage`).

### Tastatur

| Taste | Wirkung |
| --- | --- |
| Pfeiltasten | Feld wählen |
| 1–9 | Ziffer setzen |
| Umschalt + 1–9 | Notiz setzen/entfernen |
| N | Notizmodus an/aus |
| Rücktaste, Entf, 0 | Feld leeren |
| H | Tipp |
| Strg/⌘ + Z | Rückgängig |
| Esc | Tipp schließen |

Auf dem Handy: Ziffer antippen setzt sie, langer Druck setzt eine Notiz.

## Online

Bei jedem Push auf `main` veröffentlicht der Workflow „Killer Sudoku (Web)“ die Seite auf GitHub Pages. Dafür einmalig
unter *Settings → Pages* als Quelle „GitHub Actions“ auswählen.

## Lokal starten und testen

Die Seite braucht wegen der Module und Web Worker einen kleinen Webserver:

```bash
cd killer-sudoku-web
python3 -m http.server 8000   # dann http://localhost:8000 öffnen
npm test                      # Node 20+, keine Abhängigkeiten
```

## Aufbau

- `game/` – Spiellogik, eine 1:1-Portierung der Kotlin-Klassen der App (`random.js` bildet Kotlins Zufallsgenerator
  nach, damit Codes identische Rätsel ergeben)
- `worker.js` – Rätsel erzeugen und Tipps suchen im Hintergrund
- `app.js`, `index.html`, `styles.css` – Oberfläche
- `test/fixtures.tsv` – Referenzrätsel aus der App. `compat.test.js` und der Kotlin-Test `CompatibilityTest` prüfen
  beide Versionen dagegen. Wer Generator oder Löser ändert, muss beide Versionen gleich ändern und die Datei neu erzeugen.
