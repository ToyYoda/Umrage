const API_BASE = process.env.GARAGE61_API_BASE ?? "https://garage61.net/api/v1";

export class Garage61Error extends Error {
  status?: number;
  body?: string;
  constructor(message: string, status?: number, body?: string) {
    super(message);
    this.name = "Garage61Error";
    this.status = status;
    this.body = body;
  }
}

function authHeaders(token: string) {
  return {
    Authorization: `Bearer ${token}`,
    Accept: "application/json",
  } as const;
}

async function get<T>(path: string, token: string, params?: Record<string, string | number | undefined>): Promise<T> {
  const url = new URL(`${API_BASE}${path}`);
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== "") {
        url.searchParams.set(key, String(value));
      }
    }
  }
  const res = await fetch(url, {
    headers: authHeaders(token),
    cache: "no-store",
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Garage61Error(
      `Garage 61 API request failed: ${res.status} ${res.statusText}`,
      res.status,
      text.slice(0, 500),
    );
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new Garage61Error("Garage 61 API returned non-JSON response", res.status, text.slice(0, 500));
  }
}

export type Garage61Track = {
  id: number | string;
  name: string;
  shortName?: string;
  configName?: string;
  config?: string;
};

export type Garage61Lap = {
  id?: string | number;
  lapTime?: number;
  time?: number;
  driverName?: string;
  user?: { name?: string; firstName?: string; lastName?: string };
  car?: { id?: number; name?: string };
  track?: { id?: number; name?: string; shortName?: string };
  carName?: string;
  trackName?: string;
  startedAt?: string;
  endedAt?: string;
  recordedAt?: string;
  sessionType?: string;
  // Free-form additional fields are tolerated
  [key: string]: unknown;
};

type Listish<T> = T[] | { items?: T[]; data?: T[]; results?: T[] };

function unwrapList<T>(payload: Listish<T>): T[] {
  if (Array.isArray(payload)) return payload;
  if (payload && typeof payload === "object") {
    const obj = payload as { items?: T[]; data?: T[]; results?: T[] };
    return obj.items ?? obj.data ?? obj.results ?? [];
  }
  return [];
}

export async function findTracks(token: string, search?: string): Promise<Garage61Track[]> {
  const payload = await get<Listish<Garage61Track>>("/tracks", token, search ? { q: search, search } : undefined);
  return unwrapList(payload);
}

export async function findLaps(
  token: string,
  params: { track?: number | string; cars?: string; user?: string; take?: number; sort?: string },
): Promise<Garage61Lap[]> {
  const payload = await get<Listish<Garage61Lap>>("/laps", token, params);
  return unwrapList(payload);
}

export function getLapTimeSeconds(lap: Garage61Lap): number | undefined {
  const candidates = [lap.lapTime, lap.time, (lap as Record<string, unknown>).bestLapTime];
  for (const value of candidates) {
    if (typeof value === "number" && Number.isFinite(value) && value > 0) return value;
  }
  return undefined;
}

export function pickFastest(laps: Garage61Lap[]): Garage61Lap | undefined {
  let best: Garage61Lap | undefined;
  let bestTime = Infinity;
  for (const lap of laps) {
    const t = getLapTimeSeconds(lap);
    if (t !== undefined && t < bestTime) {
      bestTime = t;
      best = lap;
    }
  }
  return best;
}

export function formatLapTime(seconds: number | undefined): string {
  if (seconds === undefined || !Number.isFinite(seconds) || seconds <= 0) return "—";
  const totalMs = Math.round(seconds * 1000);
  const minutes = Math.floor(totalMs / 60000);
  const remaining = totalMs - minutes * 60000;
  const secs = Math.floor(remaining / 1000);
  const millis = remaining - secs * 1000;
  return `${minutes}:${secs.toString().padStart(2, "0")}.${millis.toString().padStart(3, "0")}`;
}

export function trackMatchesSpa(track: Garage61Track): boolean {
  const haystacks = [track.name, track.shortName, track.configName, track.config]
    .filter((v): v is string => typeof v === "string")
    .map((v) => v.toLowerCase());
  return haystacks.some((s) => s.includes("spa") || s.includes("francorchamps"));
}

export async function resolveSpaTrackId(token: string): Promise<{ id: number | string; track: Garage61Track } | undefined> {
  const configured = process.env.GARAGE61_SPA_TRACK_ID;
  if (configured) {
    return { id: configured, track: { id: configured, name: "Spa-Francorchamps (configured)" } };
  }
  const tracks = await findTracks(token, "spa");
  const candidates = tracks.filter(trackMatchesSpa);
  if (candidates.length === 0) return undefined;
  // Prefer "Grand Prix" config if available
  const gp = candidates.find((t) =>
    [t.name, t.configName, t.config]
      .filter((v): v is string => typeof v === "string")
      .some((v) => /grand\s*prix|gp/i.test(v)),
  );
  const chosen = gp ?? candidates[0];
  return { id: chosen.id, track: chosen };
}
