import { NextResponse } from "next/server";
import fs from "fs/promises";
import path from "path";

type ResponseEntry = { preference: number; time: string; submittedAt: string };

const dataDir = path.join(process.cwd(), "data");
const dataFile = path.join(dataDir, "responses.json");

async function ensureDataFile() {
  try {
    await fs.mkdir(dataDir, { recursive: true });
    try {
      await fs.access(dataFile);
    } catch (e) {
      await fs.writeFile(dataFile, "[]", "utf8");
    }
  } catch (e) {
    console.error("Could not ensure data file", e);
  }
}

export async function POST(req: Request) {
  await ensureDataFile();
  try {
    const body = await req.json();
    const preference = Number(body.preference);
    const time = String(body.time);
    if (Number.isNaN(preference) || preference < 0 || preference > 100) {
      return NextResponse.json({ error: "Ungültige Präferenz" }, { status: 400 });
    }
    if (!time) return NextResponse.json({ error: "Ungültige Zeit" }, { status: 400 });

    const entry: ResponseEntry = { preference, time, submittedAt: new Date().toISOString() };

    const raw = await fs.readFile(dataFile, "utf8");
    const arr: ResponseEntry[] = raw ? JSON.parse(raw) : [];
    arr.push(entry);
    await fs.writeFile(dataFile, JSON.stringify(arr, null, 2), "utf8");

    return NextResponse.json({ ok: true });
  } catch (err) {
    console.error(err);
    return NextResponse.json({ error: "Server error" }, { status: 500 });
  }
}
