"use client";

import React, { useState } from "react";

export default function SurveyForm() {
  const [preference, setPreference] = useState(50);
  const [time, setTime] = useState("21:00");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  function generateTimes() {
    const times: string[] = [];
    // 21:00..23:45
    for (let h = 21; h <= 23; h++) {
      for (let m = 0; m < 60; m += 15) {
        times.push(`${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);
      }
    }
    // 00:00..02:00
    for (let h = 0; h <= 2; h++) {
      for (let m = 0; m < 60; m += 15) {
        const t = `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
        times.push(t);
        if (h === 2 && m === 0) break;
      }
    }
    // ensure we stop at 02:00
    return times.filter((t) => {
      // include up to 02:00
      const [hh, mm] = t.split(":").map(Number);
      if (hh > 2 && hh < 21) return false;
      return true;
    });
  }

  const times = generateTimes();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await fetch("/api/submit", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ preference, time }),
      });
      setDone(true);
    } catch (err) {
      console.error(err);
      alert("Fehler beim Absenden");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h2 className="mb-4 text-lg font-medium">Wie soll die Verteilung sein?</h2>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="mb-2 block text-sm">Salsa (links) — Präferenz — Bachata (rechts)</label>
          <div className="flex items-center gap-4">
            <span className="text-sm">100% Salsa</span>
            <input
              type="range"
              min={0}
              max={100}
              value={preference}
              onChange={(e) => setPreference(Number(e.target.value))}
              className="w-full"
            />
            <span className="text-sm">100% Bachata</span>
          </div>
          <div className="mt-2 text-sm">Deine Wahl: {preference}% Bachata / {100 - preference}% Salsa</div>
        </div>

        <div>
          <label className="mb-2 block text-sm">Wann kommst du zur Party?</label>
          <select value={time} onChange={(e) => setTime(e.target.value)} className="w-full rounded border px-3 py-2">
            {times.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>

        <div>
          <button
            type="submit"
            disabled={submitting}
            className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-60"
          >
            {submitting ? "Sende..." : done ? "Danke!" : "Absenden"}
          </button>
        </div>
      </form>
    </div>
  );
}
