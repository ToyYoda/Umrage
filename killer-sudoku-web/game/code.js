// Rätsel-Codes wie „M-123456“ und das tägliche Rätsel (wie PuzzleCode.kt in der App).
import { DIFFICULTIES } from './puzzle.js';

export const SEEDS = 1000000;

export const formatCode = (difficulty, seed) => `${difficulty.letter}-${String(seed).padStart(6, '0')}`;

/** @returns [difficulty, seed] oder null */
export function parseCode(text) {
  const match = /^\s*([A-Za-z])\s*-?\s*(\d{1,6})\s*$/.exec(text);
  if (!match) return null;
  const difficulty = DIFFICULTIES.find((d) => d.letter === match[1].toUpperCase());
  if (!difficulty) return null;
  return [difficulty, Number(match[2])];
}

const floorMod = (a, n) => ((a % n) + n) % n;

/** Tage seit 1970-01-01 für das lokale Datum (wie LocalDate.toEpochDay()). */
export const epochDay = (date = new Date()) =>
  Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86400000);

/** Tägliches Rätsel: unter der Woche leichter, am Wochenende schwerer. */
export function daily(day) {
  const dayOfWeek = floorMod(day + 3, 7) + 1; // 1 = Montag … 7 = Sonntag
  const difficulty = DIFFICULTIES[[0, 0, 0, 1, 1, 2, 2, 3][dayOfWeek]];
  const seed = floorMod(day * 7919 + 104729, SEEDS);
  return [difficulty, seed];
}
