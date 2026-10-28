// Käfigfärbung: benachbarte Käfige bekommen verschiedene Farben (wie CageColors.kt).
import { neighbours } from './grid.js';

export const COLOR_COUNT = 6;

export function assignCageColors(puzzle) {
  const n = puzzle.cages.length;
  const adjacent = Array.from({ length: n }, () => new Set());
  for (let cell = 0; cell < 81; cell++) {
    for (const other of neighbours[cell]) {
      const a = puzzle.cageOf[cell];
      const b = puzzle.cageOf[other];
      if (a !== b) adjacent[a].add(b);
    }
  }
  // Reihenfolge „kleinster Grad zuletzt“: für ebene Nachbarschaftsgraphen reichen damit 6 Farben.
  const remaining = new Set(Array.from({ length: n }, (_, i) => i));
  const degree = adjacent.map((s) => s.size);
  const order = [];
  while (remaining.size > 0) {
    let next = -1;
    for (const k of remaining) if (next === -1 || degree[k] * n + k < degree[next] * n + next) next = k;
    remaining.delete(next);
    order.unshift(next);
    for (const b of adjacent[next]) if (remaining.has(b)) degree[b]--;
  }
  const colors = new Array(n).fill(-1);
  for (const cage of order) {
    const used = new Set([...adjacent[cage]].map((k) => colors[k]).filter((c) => c >= 0));
    let best = -1;
    let bestKey = Infinity;
    for (let c = 0; c < COLOR_COUNT; c++) {
      if (used.has(c)) continue;
      const key = colors.filter((x) => x === c).length * COLOR_COUNT + c;
      if (key < bestKey) {
        bestKey = key;
        best = c;
      }
    }
    colors[cage] = best;
  }
  return colors;
}
