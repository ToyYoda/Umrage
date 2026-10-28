// Mögliche Ziffernkombinationen für Felder mit fester Summe (wie Combinations in der App).
import { combinations as allCombinations } from './cagesums.js';

export class Analysis {
  constructor(viable, allowed) {
    /** Kombinationen (Bitmaske), die sich auf die Felder verteilen lassen. */
    this.viable = viable;
    /** Je Feld die Ziffern, die in mindestens einer verteilbaren Kombination dort stehen können. */
    this.allowed = allowed;
  }

  /** Ziffern, die in jeder verteilbaren Kombination vorkommen. */
  get required() {
    return this.viable.length === 0 ? 0 : this.viable.reduce((acc, combo) => acc & combo);
  }
}

export const all = allCombinations;

/**
 * @param candidates Kandidaten der leeren Felder
 * @param sum Summe, die diese Felder zusammen ergeben müssen
 * @param mustContain Ziffern, die in jeder Kombination vorkommen müssen
 */
export function analyze(candidates, sum, mustContain = 0) {
  const k = candidates.length;
  const union = candidates.reduce((acc, c) => acc | c, 0);
  const viable = [];
  const allowed = new Array(k).fill(0);
  for (const combo of allCombinations(k, sum)) {
    if ((combo & ~union) !== 0 || (combo & mustContain) !== mustContain) continue;
    const options = assignments(candidates, combo);
    if (!options) continue;
    viable.push(combo);
    for (let i = 0; i < k; i++) allowed[i] |= options[i];
  }
  return new Analysis(viable, allowed);
}

/** Je Feld die Ziffern, die es in einer gültigen Verteilung von `combo` bekommen kann, oder null. */
function assignments(candidates, combo) {
  const k = candidates.length;
  const forward = Array.from({ length: k + 1 }, () => new Uint8Array(512));
  forward[0][0] = 1;
  for (let i = 0; i < k; i++) {
    for (let mask = 0; mask < 512; mask++) {
      if (!forward[i][mask]) continue;
      let options = candidates[i] & combo & ~mask;
      while (options !== 0) {
        const b = options & -options;
        options ^= b;
        forward[i + 1][mask | b] = 1;
      }
    }
  }
  if (!forward[k][combo]) return null;
  const backward = Array.from({ length: k + 1 }, () => new Uint8Array(512));
  backward[k][combo] = 1;
  const result = new Array(k).fill(0);
  for (let i = k - 1; i >= 0; i--) {
    for (let mask = 0; mask < 512; mask++) {
      if (!forward[i][mask]) continue;
      let options = candidates[i] & combo & ~mask;
      while (options !== 0) {
        const b = options & -options;
        options ^= b;
        if (backward[i + 1][mask | b]) {
          backward[i][mask] = 1;
          result[i] |= b;
        }
      }
    }
  }
  return result;
}
