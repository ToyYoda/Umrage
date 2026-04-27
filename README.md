# Garage 61 – Schnellste Runde Spa-Francorchamps

Kleine Next.js-Anwendung, die deine persönliche Bestzeit auf **Spa-Francorchamps**
direkt aus der [Garage 61](https://garage61.net/) API anzeigt.

## Funktionsweise

- Server-Route `GET /api/fastest-lap` liest deinen Garage 61 Personal Access Token
  aus der Umgebung und ruft `GET https://garage61.net/api/v1/laps?track=<spa-id>` auf.
- Die Track-ID für Spa wird, sofern nicht manuell gesetzt, über die Garage 61
  Track-Liste anhand von „Spa“ / „Francorchamps“ ermittelt (bevorzugt die
  Grand-Prix-Konfiguration).
- Aus den zurückgelieferten Runden wird die schnellste (kleinste `lapTime`)
  ausgewählt und im Frontend hübsch dargestellt.

## Voraussetzungen

- Node.js 20+
- Einen **Garage 61 Personal Access Token** (in deinem Garage 61 Account unter
  „My applications“ erstellen).

## Einrichtung

```bash
npm install
cp .env.example .env.local
# anschließend GARAGE61_TOKEN in .env.local eintragen
npm run dev
```

Aufruf im Browser: <http://localhost:3000>

## Umgebungsvariablen

| Variable                  | Pflicht | Beschreibung                                                                                  |
| ------------------------- | ------- | --------------------------------------------------------------------------------------------- |
| `GARAGE61_TOKEN`          | ja      | Persönlicher Zugriffstoken (Bearer) für die Garage 61 API.                                    |
| `GARAGE61_SPA_TRACK_ID`   | nein    | Track-ID für Spa-Francorchamps. Wird sie nicht gesetzt, ermittelt die App sie automatisch.    |
| `GARAGE61_API_BASE`       | nein    | API-Basis-URL (Default: `https://garage61.net/api/v1`).                                       |

## Hinweise

- Die `findLaps`-API liefert nur Runden, die du selbst (oder Teamkollegen) gefahren
  hast – globale Bestzeiten werden vom Anbieter aus Datenschutzgründen nicht
  bereitgestellt.
- Wenn dein Account noch keine Runde in Spa enthält, zeigt die App eine
  entsprechende Meldung an.
- Token bitte **nicht** ins Repository einchecken; `.env*` ist via `.gitignore`
  bereits ausgeschlossen.

## Build / Deployment

```bash
npm run build
npm start
```

Für Render/Vercel reicht es, `GARAGE61_TOKEN` als Environment-Variable im Service
zu hinterlegen.
