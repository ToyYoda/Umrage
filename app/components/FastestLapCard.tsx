"use client";

import { useCallback, useEffect, useState } from "react";

type Lap = {
  id?: string | number;
  lapTimeSeconds?: number;
  lapTimeFormatted?: string;
  driverName?: string;
  user?: { name?: string; firstName?: string; lastName?: string };
  car?: { name?: string };
  carName?: string;
  track?: { name?: string; shortName?: string };
  trackName?: string;
  startedAt?: string;
  endedAt?: string;
  recordedAt?: string;
  sessionType?: string;
};

type Track = { id: number | string; name: string; configName?: string; shortName?: string };

type SuccessResponse = { fastestLap: Lap | null; track: Track; message?: string };
type ErrorResponse = { error: string; status?: number; body?: string };
type ApiResponse = SuccessResponse | ErrorResponse;

function isError(r: ApiResponse): r is ErrorResponse {
  return typeof (r as ErrorResponse).error === "string";
}

function driverDisplay(lap: Lap): string | undefined {
  if (lap.driverName) return lap.driverName;
  const u = lap.user;
  if (!u) return undefined;
  if (u.name) return u.name;
  return [u.firstName, u.lastName].filter(Boolean).join(" ") || undefined;
}

function carDisplay(lap: Lap): string | undefined {
  return lap.car?.name ?? lap.carName;
}

function trackDisplay(track: Track): string {
  if (track.configName && !track.name.toLowerCase().includes(track.configName.toLowerCase())) {
    return `${track.name} · ${track.configName}`;
  }
  return track.name;
}

function whenDisplay(lap: Lap): string | undefined {
  const raw = lap.endedAt ?? lap.startedAt ?? lap.recordedAt;
  if (!raw) return undefined;
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  return d.toLocaleString("de-DE", { dateStyle: "medium", timeStyle: "short" });
}

export default function FastestLapCard() {
  const [data, setData] = useState<ApiResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch("/api/fastest-lap", { cache: "no-store" });
      const json = (await res.json()) as ApiResponse;
      setData(json);
    } catch (err) {
      setData({ error: err instanceof Error ? err.message : "Netzwerkfehler" });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <div className="rounded-xl border border-neutral-800 bg-neutral-900/60 p-6">
        <div className="animate-pulse space-y-3">
          <div className="h-4 w-32 bg-neutral-800 rounded" />
          <div className="h-12 w-48 bg-neutral-800 rounded" />
          <div className="h-3 w-64 bg-neutral-800 rounded" />
        </div>
      </div>
    );
  }

  if (!data) {
    return null;
  }

  if (isError(data)) {
    return (
      <div className="rounded-xl border border-red-900/60 bg-red-950/40 p-6">
        <h2 className="text-red-300 font-semibold mb-2">Fehler beim Laden</h2>
        <p className="text-sm text-red-200/90 whitespace-pre-wrap">{data.error}</p>
        {data.body ? (
          <pre className="mt-3 text-xs text-red-200/70 bg-black/30 rounded p-2 overflow-auto">{data.body}</pre>
        ) : null}
        <button
          onClick={() => void load()}
          className="mt-4 inline-flex items-center rounded-md border border-red-700/60 px-3 py-1.5 text-sm hover:bg-red-900/30"
        >
          Erneut versuchen
        </button>
      </div>
    );
  }

  const { fastestLap, track } = data;

  if (!fastestLap) {
    return (
      <div className="rounded-xl border border-neutral-800 bg-neutral-900/60 p-6">
        <h2 className="font-semibold mb-1">Keine Runde gefunden</h2>
        <p className="text-sm text-neutral-400">
          {data.message ?? `Für ${trackDisplay(track)} wurden keine Runden zurückgegeben.`}
        </p>
        <button
          onClick={() => void load()}
          className="mt-4 inline-flex items-center rounded-md border border-neutral-700 px-3 py-1.5 text-sm hover:bg-neutral-800"
        >
          Erneut laden
        </button>
      </div>
    );
  }

  const driver = driverDisplay(fastestLap);
  const car = carDisplay(fastestLap);
  const when = whenDisplay(fastestLap);

  return (
    <div className="rounded-xl border border-emerald-900/40 bg-gradient-to-br from-neutral-900 to-neutral-950 p-6 shadow-lg">
      <div className="flex items-center justify-between mb-4">
        <span className="text-xs uppercase tracking-widest text-emerald-400">Bestzeit</span>
        <span className="text-xs text-neutral-500">{trackDisplay(track)}</span>
      </div>
      <div className="font-mono text-5xl sm:text-6xl font-bold tabular-nums text-emerald-300">
        {fastestLap.lapTimeFormatted ?? "—"}
      </div>
      <dl className="mt-6 grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        {driver ? (
          <div>
            <dt className="text-neutral-500">Fahrer</dt>
            <dd className="text-neutral-100">{driver}</dd>
          </div>
        ) : null}
        {car ? (
          <div>
            <dt className="text-neutral-500">Fahrzeug</dt>
            <dd className="text-neutral-100">{car}</dd>
          </div>
        ) : null}
        {when ? (
          <div>
            <dt className="text-neutral-500">Gefahren am</dt>
            <dd className="text-neutral-100">{when}</dd>
          </div>
        ) : null}
        {fastestLap.sessionType ? (
          <div>
            <dt className="text-neutral-500">Session</dt>
            <dd className="text-neutral-100">{fastestLap.sessionType}</dd>
          </div>
        ) : null}
      </dl>
      <div className="mt-6 flex justify-end">
        <button
          onClick={() => void load()}
          className="inline-flex items-center rounded-md border border-neutral-700 px-3 py-1.5 text-sm hover:bg-neutral-800"
        >
          Aktualisieren
        </button>
      </div>
    </div>
  );
}
