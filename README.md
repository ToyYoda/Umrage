# Umfrage (Salsa vs Bachata)

Einfache Next.js-App (App Router). Auf der Startseite gibt es eine Umfrage mit:
- Slider: Verteilung zwischen 100% Salsa (links) und 100% Bachata (rechts)
- Dropdown: Ankunftszeit in 15-Minuten-Schritten von 21:00 bis 02:00

Die Antworten werden in `data/responses.json` gespeichert. Eine API `/api/results` liefert die aggregierten, gewichteten Ergebnisse pro 15-Minuten-Bucket.

Starten:

```powershell
npm install
npm run dev
```

Die Seite ist verfügbar unter http://localhost:3000

Deployment auf Render (mit beibehaltener dateibasierter Speicherung)
1. Erstelle ein Git-Repository lokal und pushe den Code zu GitHub:

```powershell
git init
git add .
git commit -m "Initial commit"
# Erstelle ein GitHub-Repo und füge es als remote hinzu, z.B.:
git remote add origin https://github.com/<your-username>/<repo>.git
git push -u origin main
```

2. Erstelle auf Render ein neues "Web Service"-Projekt und verbinde es mit deinem GitHub-Repo.
	- Build Command: `npm install && npm run build`
	- Start Command: `npm start` (Render setzt die Umgebungsvariable `PORT`; die App nutzt `next start -p $PORT`).

3. Wähle die Branch zum Deployen und starte das Deployment. Render wird die App bauen und starten.

Hinweis zur dateibasierten Speicherung:
- `data/responses.json` wird auf dem Render-Service geschrieben und bleibt solange erhalten, wie die Instanz läuft. Bei einem neuen Deploy oder wenn die Instanz ersetzt wird, können die Daten überschrieben werden.
- Für Produktionsdaten oder wenn Sie Persistenz über Deploys hinweg benötigen, empfehlen wir die Migration zu einer Datenbank (z. B. Supabase/Postgres).
This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
