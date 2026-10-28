// Gitter-Hilfen: Zeilen, Spalten, Blöcke, Nachbarn, Einheiten (wie Grid in der App).

export const ALL = 0x1ff;

export const row = (cell) => Math.floor(cell / 9);
export const col = (cell) => cell % 9;
export const box = (cell) => Math.floor(cell / 27) * 3 + Math.floor((cell % 9) / 3);

export const bit = (digit) => 1 << (digit - 1);

export function popcount(x) {
  let n = 0;
  while (x) {
    x &= x - 1;
    n++;
  }
  return n;
}

/** Index des niedrigsten gesetzten Bits (x != 0). */
export const ctz = (x) => 31 - Math.clz32(x & -x);

/** Ziffer zu einer Bitmaske mit genau einem Bit. */
export const digitOf = (mask) => ctz(mask) + 1;

export const digits = (mask) => [1, 2, 3, 4, 5, 6, 7, 8, 9].filter((d) => mask & bit(d));

/** Alle Zellen in derselben Zeile, Spalte oder demselben Block (ohne die Zelle selbst). */
export const peers = Array.from({ length: 81 }, (_, cell) => {
  const result = [];
  for (let i = 0; i < 81; i++) {
    if (i !== cell && (row(i) === row(cell) || col(i) === col(cell) || box(i) === box(cell))) result.push(i);
  }
  return result;
});

export const peerSets = peers.map((list) => new Set(list));

/** Orthogonale Nachbarn. */
export const neighbours = Array.from({ length: 81 }, (_, cell) => {
  const r = row(cell);
  const c = col(cell);
  const result = [];
  if (r > 0) result.push(cell - 9);
  if (r < 8) result.push(cell + 9);
  if (c > 0) result.push(cell - 1);
  if (c < 8) result.push(cell + 1);
  return result;
});

/** Die 27 Einheiten: Index 0–8 Zeilen, 9–17 Spalten, 18–26 Blöcke. */
export const units = Array.from({ length: 27 }, (_, u) => {
  const i = u % 9;
  const kind = Math.floor(u / 9);
  return Array.from({ length: 9 }, (_, k) => {
    if (kind === 0) return i * 9 + k;
    if (kind === 1) return k * 9 + i;
    return Math.floor(i / 3) * 27 + (i % 3) * 3 + Math.floor(k / 3) * 9 + (k % 3);
  });
});

export const unitSets = units.map((list) => new Set(list));

export const unitsOf = (cell) => [row(cell), 9 + col(cell), 18 + box(cell)];

const boxNames = [
  'oben links', 'oben Mitte', 'oben rechts',
  'Mitte links', 'Mitte', 'Mitte rechts',
  'unten links', 'unten Mitte', 'unten rechts',
];

/** „in Zeile 3“, „in Spalte 5“, „im Block oben links“. */
export function inUnit(u) {
  const kind = Math.floor(u / 9);
  if (kind === 0) return `in Zeile ${(u % 9) + 1}`;
  if (kind === 1) return `in Spalte ${(u % 9) + 1}`;
  return u % 9 === 4 ? 'im mittleren Block' : `im Block ${boxNames[u % 9]}`;
}

/** Kurzname eines Feldes, z. B. „Z3/S5“ (Zeile 3, Spalte 5). */
export const cellName = (cell) => `Z${row(cell) + 1}/S${col(cell) + 1}`;

export const capitalize = (text) => text.charAt(0).toUpperCase() + text.slice(1);
