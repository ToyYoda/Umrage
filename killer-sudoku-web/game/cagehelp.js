// Kombinationen eines Käfigs für die Anzeige, mit „noch möglich?“ (wie CageHelp.kt).
import { all, analyze } from './combinations.js';
import { ALL, bit, peers } from './grid.js';

/** @returns [{digits, possible}] */
export function cageCombinations(state, cageIndex) {
  const cage = state.puzzle.cages[cageIndex];
  const visible = (cell) => (!state.strict && state.isWrong(cell) ? 0 : state.values[cell]);

  const filled = cage.cells.filter((c) => visible(c) !== 0);
  const placed = filled.reduce((acc, c) => acc | bit(visible(c)), 0);
  const empty = cage.cells.filter((c) => visible(c) === 0);
  const remaining = cage.sum - filled.reduce((acc, c) => acc + visible(c), 0);
  let duplicates = false;
  { let n = 0; for (let m = placed; m; m &= m - 1) n++; duplicates = n !== filled.length; }

  let viableRest = new Set();
  if (!duplicates && remaining >= 0) {
    if (empty.length === 0) {
      if (remaining === 0) viableRest = new Set([0]);
    } else {
      const candidates = empty.map((cell) => {
        const seen = peers[cell].reduce((acc, p) => (visible(p) !== 0 ? acc | bit(visible(p)) : acc), placed);
        return ALL & ~seen;
      });
      viableRest = new Set(analyze(candidates, remaining).viable);
    }
  }

  return all(cage.cells.length, cage.sum).map((combo) => ({
    digits: combo,
    possible: (combo & placed) === placed && viableRest.has(combo ^ placed),
  }));
}
