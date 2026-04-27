import { NextResponse } from "next/server";
import {
  findLaps,
  formatLapTime,
  getLapTimeSeconds,
  pickFastest,
  resolveSpaTrackId,
  Garage61Error,
} from "../../lib/garage61";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET() {
  const token = process.env.GARAGE61_TOKEN;
  if (!token) {
    return NextResponse.json(
      {
        error:
          "Kein Garage 61 API-Token gefunden. Bitte GARAGE61_TOKEN in der Umgebung setzen (Personal Access Token aus Garage 61).",
      },
      { status: 500 },
    );
  }

  try {
    const resolved = await resolveSpaTrackId(token);
    if (!resolved) {
      return NextResponse.json(
        {
          error:
            "Spa-Francorchamps wurde in der Trackliste nicht gefunden. Bitte GARAGE61_SPA_TRACK_ID setzen, um die Track-ID manuell festzulegen.",
        },
        { status: 404 },
      );
    }

    const laps = await findLaps(token, { track: resolved.id });
    const fastest = pickFastest(laps);

    if (!fastest) {
      return NextResponse.json(
        {
          track: resolved.track,
          fastestLap: null,
          message:
            "Keine Runden für Spa-Francorchamps gefunden. Hast du dort schon eine Runde gefahren bzw. ist sie in Garage 61 sichtbar?",
        },
        { status: 200 },
      );
    }

    const seconds = getLapTimeSeconds(fastest);
    return NextResponse.json({
      track: resolved.track,
      fastestLap: {
        ...fastest,
        lapTimeSeconds: seconds,
        lapTimeFormatted: formatLapTime(seconds),
      },
    });
  } catch (err) {
    if (err instanceof Garage61Error) {
      return NextResponse.json(
        { error: err.message, status: err.status, body: err.body },
        { status: err.status === 401 || err.status === 403 ? err.status : 502 },
      );
    }
    const message = err instanceof Error ? err.message : "Unbekannter Fehler";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
