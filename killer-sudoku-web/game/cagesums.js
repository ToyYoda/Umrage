// Vorberechnete Tabellen: welche Summen lassen sich mit k verschiedenen Ziffern aus einer Ziffernmenge
// bilden, und welche Ziffernmengen ergeben eine Summe (wie CageSums in der App).
import { bit, popcount } from './grid.js';

const possible = new Uint8Array(512 * 10 * 46);

for (let subset = 0; subset < 512; subset++) {
  const k = popcount(subset);
  let sum = 0;
  for (let d = 1; d <= 9; d++) if (subset & bit(d)) sum += d;
  // Alle Obermengen von subset durchlaufen.
  let mask = subset;
  while (mask < 512) {
    possible[(mask * 10 + k) * 46 + sum] = 1;
    mask = (mask + 1) | subset;
  }
}

export function isPossible(digitMask, count, sum) {
  return sum >= 0 && sum <= 45 && possible[(digitMask * 10 + count) * 46 + sum] === 1;
}

const digitKey = (mask) => {
  let s = '';
  for (let d = 1; d <= 9; d++) if (mask & bit(d)) s += d;
  return s;
};

const combos = Array.from({ length: 10 * 46 }, (_, index) => {
  const count = Math.floor(index / 46);
  const sum = index % 46;
  const list = [];
  for (let mask = 0; mask < 512; mask++) {
    if (popcount(mask) !== count) continue;
    let s = 0;
    for (let d = 1; d <= 9; d++) if (mask & bit(d)) s += d;
    if (s === sum) list.push(mask);
  }
  return list.sort((a, b) => (digitKey(a) < digitKey(b) ? -1 : digitKey(a) > digitKey(b) ? 1 : 0));
});

const EMPTY = [];

/** Alle Ziffernmengen (Bitmaske) mit `count` Ziffern und Summe `sum`, aufsteigend sortiert (1+9 vor 2+8). */
export function combinations(count, sum) {
  return sum >= 0 && sum <= 45 && count >= 0 && count <= 9 ? combos[count * 46 + sum] : EMPTY;
}
