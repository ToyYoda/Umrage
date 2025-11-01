import { NextResponse } from "next/server";
import fs from "fs/promises";
import path from "path";

type ResponseEntry = { preference: number; time: string; submittedAt: string };

const dataFile = path.join(process.cwd(), "data", "responses.json");

function generateBuckets() {
  const times: string[] = [];
  for (let h = 21; h <= 23; h++) {
    for (let m = 0; m < 60; m += 15) {
      times.push(`${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);
    }
  }
  for (let h = 0; h <= 2; h++) {
    for (let m = 0; m < 60; m += 15) {
      times.push(`${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);
      if (h === 2 && m === 0) break;
    }
  }
  return times;
}

function timeToAdjustedMinutes(t: string) {
  const [hh, mm] = t.split(":").map(Number);
  // treat times after midnight (0-2) as next day by adding 24h
  const adjustedHour = hh < 3 ? hh + 24 : hh;
  return adjustedHour * 60 + mm;
}

export async function GET() {
  try {
    let raw = "[]";
    try {
      raw = await fs.readFile(dataFile, "utf8");
    } catch (e) {
      // file not found -> no responses
    }
    const arr: ResponseEntry[] = raw ? JSON.parse(raw) : [];

    const buckets = generateBuckets();
    // Precompute adjusted minutes for responses
    const responsesWithMinutes = arr.map((r) => ({ ...r, minutes: timeToAdjustedMinutes(r.time) }));

    const data: number[] = buckets.map((b) => {
      const bucketMinutes = timeToAdjustedMinutes(b);
      // include any response whose arrival minutes <= bucketMinutes (they stay until the end)
      const items = responsesWithMinutes.filter((r) => r.minutes <= bucketMinutes);
      if (items.length === 0) return 50; // neutral default
      const sum = items.reduce((s, it) => s + it.preference, 0);
      const avg = sum / items.length; // 0..100 representing Bachata
      return Math.round(avg * 100) / 100;
    });

    return NextResponse.json({ labels: buckets, data });
  } catch (err) {
    console.error(err);
    return NextResponse.json({ labels: [], data: [] });
  }
}
