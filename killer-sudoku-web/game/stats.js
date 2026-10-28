// Statistik über gelöste Spiele (wie Stats.kt in der App).
import { difficultyByName } from './puzzle.js';

const VERSION = '1';
const MAX_RECORDS = 2000;
export const RECENT = 20;

/** record: {difficulty, seconds, mistakes, hints, day, dailyDay} */
export const isFlawless = (record) => record.mistakes === 0 && record.hints === 0;

export class Stats {
  constructor(started = {}, records = []) {
    this.started = started;
    this.records = records;
  }

  withStarted(difficulty) {
    return new Stats({ ...this.started, [difficulty.name]: (this.started[difficulty.name] ?? 0) + 1 }, this.records);
  }

  withRecord(record) {
    return new Stats(this.started, [...this.records, record].slice(-MAX_RECORDS));
  }

  of(difficulty) {
    const solved = this.records.filter((r) => r.difficulty === difficulty.name);
    let current = 0;
    let best = 0;
    for (const record of solved) {
      current = isFlawless(record) ? current + 1 : 0;
      best = Math.max(best, current);
    }
    const noHints = solved.filter((r) => r.hints === 0).map((r) => r.seconds);
    return {
      started: Math.max(this.started[difficulty.name] ?? 0, solved.length),
      solved: solved.length,
      bestSeconds: noHints.length ? Math.min(...noHints) : null,
      averageSeconds: solved.length ? Math.floor(solved.reduce((a, r) => a + r.seconds, 0) / solved.length) : null,
      flawless: solved.filter(isFlawless).length,
      currentFlawlessStreak: current,
      bestFlawlessStreak: best,
      recentSeconds: solved.slice(-RECENT).map((r) => r.seconds),
    };
  }

  dailyRecord(day) {
    for (let i = this.records.length - 1; i >= 0; i--) if (this.records[i].dailyDay === day) return this.records[i];
    return null;
  }

  get dailySolved() {
    return new Set(this.records.map((r) => r.dailyDay).filter((d) => d >= 0)).size;
  }

  /** Tage in Folge mit gelöstem täglichem Rätsel – bis heute oder, falls heute noch offen, bis gestern. */
  dailyStreak(today) {
    const days = new Set(this.records.map((r) => r.dailyDay).filter((d) => d >= 0));
    let day = days.has(today) ? today : today - 1;
    let streak = 0;
    while (days.has(day)) {
      streak++;
      day--;
    }
    return streak;
  }

  serialize() {
    return [
      VERSION,
      Object.entries(this.started).map(([k, v]) => `${k}=${v}`).join(','),
      this.records.map((r) => [r.difficulty, r.seconds, r.mistakes, r.hints, r.day, r.dailyDay].join(',')).join(';'),
    ].join('|');
  }

  static deserialize(text) {
    try {
      const parts = (text ?? '').split('|');
      if (parts.length !== 3 || parts[0] !== VERSION) return new Stats();
      const started = {};
      for (const entry of parts[1].split(',').filter(Boolean)) {
        const [name, count] = entry.split('=');
        if (difficultyByName(name)) started[name] = Number(count);
      }
      const records = parts[2].split(';').filter(Boolean).map((entry) => {
        const f = entry.split(',');
        return {
          difficulty: f[0], seconds: Number(f[1]), mistakes: Number(f[2]), hints: Number(f[3]), day: Number(f[4]), dailyDay: Number(f[5]),
        };
      }).filter((r) => difficultyByName(r.difficulty));
      return new Stats(started, records);
    } catch {
      return new Stats();
    }
  }
}
