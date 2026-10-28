// Erzeugt Killer-Sudokus mit eindeutiger Lösung (wie Generator.kt in der App, gleiche Zufallsfolge).
import { formatCode } from './code.js';
import { neighbours, peers } from './grid.js';
import { rate, UNSOLVABLE } from './logic.js';
import { maxCageSize, Puzzle } from './puzzle.js';
import { KotlinRandom, randomElement, shuffled } from './random.js';
import { Solver } from './solver.js';

const REPAIR_STEPS = 60;
const RATING_ATTEMPTS = 40;

/** Das Rätsel zu einem Code-Seed – gleicher Seed, gleiches Rätsel. */
export function generateFromSeed(difficulty, seed) {
  const puzzle = generate(difficulty, new KotlinRandom(seed));
  return new Puzzle(puzzle.difficulty, puzzle.solution, puzzle.cages, puzzle.givens, formatCode(difficulty, seed));
}

/** Erzeugt Rätsel, bis eines die gewünschte Bewertung hat (sonst das nächstbeste). */
export function generate(difficulty, random) {
  const [first, last] = difficulty.rating;
  let best = null;
  let bestDistance = Infinity;
  for (let attempt = 0; attempt < RATING_ATTEMPTS; attempt++) {
    const puzzle = generateUnique(difficulty, random);
    const rating = rate(puzzle, last);
    let distance;
    if (rating >= first && rating <= last) return puzzle;
    if (rating === UNSOLVABLE) distance = 100;
    else if (rating < first) distance = first - rating;
    else distance = rating - last;
    if (distance < bestDistance) {
      best = puzzle;
      bestDistance = distance;
    }
  }
  return best;
}

/** Ein Rätsel mit eindeutiger Lösung und dem Käfigprofil des Schwierigkeitsgrads, ohne Bewertung. */
export function generateUnique(difficulty, random) {
  for (;;) {
    const solution = randomSolvedGrid(random);
    const givens = new Array(81).fill(0);
    shuffled(range(81), random).slice(0, difficulty.givens).forEach((c) => { givens[c] = solution[c]; });

    let cages = randomCages(solution, difficulty, random, []);
    if (!cages) continue;
    for (let step = 0; step < REPAIR_STEPS; step++) {
      const result = new Solver(cages, givens).solve(2, 200000, solution);
      if (result.aborted) break;
      if (result.count === 1) return new Puzzle(difficulty, solution, cages, givens);
      const other = result.solutions.find((s) => !sameDigits(s, solution));
      const differing = new Set(range(81).filter((c) => other[c] !== solution[c]));
      const kept = cages.filter((cage) => !cage.cells.some((c) => differing.has(c)));
      cages = randomCages(solution, difficulty, random, kept);
      if (!cages) break;
    }
  }
}

export function randomSolvedGrid(random) {
  const grid = new Array(81).fill(0);
  fill(grid, 0, random);
  return grid;
}

function fill(grid, cell, random) {
  if (cell === 81) return true;
  for (const d of shuffled([1, 2, 3, 4, 5, 6, 7, 8, 9], random)) {
    if (!peers[cell].some((p) => grid[p] === d)) {
      grid[cell] = d;
      if (fill(grid, cell + 1, random)) return true;
    }
  }
  grid[cell] = 0;
  return false;
}

/**
 * Teilt das Gitter in zusammenhängende Käfige ohne doppelte Ziffern auf. Feste Käfige (kept) bleiben erhalten.
 * @returns null, wenn Einzelzellen übrig bleiben, die nicht zusammengelegt werden konnten
 */
export function randomCages(solution, difficulty, random, kept) {
  const cageId = new Array(81).fill(-1);
  const cages = kept.map((cage) => cage.cells.slice());
  cages.forEach((cells, index) => cells.forEach((c) => { cageId[c] = index; }));

  const freeNeighbours = (cell) => neighbours[cell].filter((n) => cageId[n] === -1).length;
  const digitMask = (cells) => cells.reduce((acc, c) => acc | (1 << solution[c]), 0);

  for (;;) {
    const free = range(81).filter((c) => cageId[c] === -1);
    if (free.length === 0) break;
    // Wie Kotlins minBy: Selektor für jedes Element in Reihenfolge auswerten, erstes Minimum gewinnt.
    // Bei nur einem Element ruft Kotlin den Selektor nicht auf – dann wird auch keine Zufallszahl verbraucht.
    let start = free[0];
    if (free.length > 1) {
      let startKey = Infinity;
      for (const c of free) {
        const key = freeNeighbours(c) * 1000 + random.nextInt(1000);
        if (key < startKey) {
          startKey = key;
          start = c;
        }
      }
    }
    const target = pickCageSize(difficulty, random);
    const cage = [start];
    cageId[start] = cages.length;
    let used = 1 << solution[start];
    while (cage.length < target) {
      const options = [...new Set(cage.flatMap((c) => neighbours[c]))]
        .filter((c) => cageId[c] === -1 && (used & (1 << solution[c])) === 0);
      if (options.length === 0) break;
      const next = randomElement(options, random);
      cage.push(next);
      cageId[next] = cages.length;
      used |= 1 << solution[next];
    }
    cages.push(cage);
  }

  // Einzelzellen mit einem Nachbarkäfig zusammenlegen.
  for (let index = 0; index < cages.length; index++) {
    const cage = cages[index];
    if (cage.length !== 1) continue;
    const cell = cage[0];
    const candidates = [...new Set(neighbours[cell].map((n) => cageId[n]))].filter((other) =>
      other !== index && cages[other].length >= 1 && cages[other].length < maxCageSize(difficulty) + 1 &&
      (digitMask(cages[other]) & (1 << solution[cell])) === 0);
    if (candidates.length === 0) return null;
    let target = candidates[0];
    for (const other of candidates) if (cages[other].length < cages[target].length) target = other;
    cages[target].push(cell);
    cageId[cell] = target;
    cage.length = 0;
  }

  return cages.filter((cells) => cells.length > 0).map((cells) => ({
    sum: cells.reduce((acc, c) => acc + solution[c], 0),
    cells: cells.slice().sort((a, b) => a - b),
  }));
}

function pickCageSize(difficulty, random) {
  const weights = difficulty.weights;
  let r = random.nextInt(weights.reduce((a, b) => a + b, 0));
  for (let size = 0; size < weights.length; size++) {
    r -= weights[size];
    if (r < 0) return size;
  }
  return weights.length - 1;
}

export const range = (n) => Array.from({ length: n }, (_, i) => i);

const sameDigits = (a, b) => a.every((v, i) => v === b[i]);
