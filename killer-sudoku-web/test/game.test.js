// Tests für Spielstand, Statistik, Codes, Käfigfarben und Löser der Web-Version.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { cageCombinations } from '../game/cagehelp.js';
import { daily, formatCode, parseCode, SEEDS } from '../game/code.js';
import { assignCageColors, COLOR_COUNT } from '../game/colors.js';
import { generateFromSeed, generateUnique } from '../game/generator.js';
import { neighbours, peers, units } from '../game/grid.js';
import { comboText, LogicSolver } from '../game/logic.js';
import { analyze } from '../game/combinations.js';
import { DIFFICULTIES } from '../game/puzzle.js';
import { KotlinRandom } from '../game/random.js';
import { GameState } from '../game/state.js';
import { Stats } from '../game/stats.js';

const [EASY, MEDIUM, HARD, EXPERT] = DIFFICULTIES;
const puzzle = generateFromSeed(MEDIUM, 42);
const emptyCell = puzzle.givens.findIndex((g) => g === 0);
const correct = puzzle.solution[emptyCell];
const wrong = correct === 1 ? 2 : 1;

test('falsche Eingabe wird markiert und gezählt', () => {
  const state = GameState.create(puzzle).enterDigit(emptyCell, wrong);
  assert.ok(state.isWrong(emptyCell));
  assert.ok(!state.isLocked(emptyCell));
  assert.equal(state.mistakes, 1);
  assert.equal(state.erase(emptyCell).values[emptyCell], 0);
});

test('richtige Eingabe sperrt das Feld und entfernt Notizen der Nachbarn', () => {
  const peer = peers[emptyCell].find((c) => puzzle.givens[c] === 0);
  const state = GameState.create(puzzle).toggleNote(peer, correct).toggleNote(peer, wrong).enterDigit(emptyCell, correct);
  assert.ok(state.isLocked(emptyCell));
  assert.ok(!state.hasNote(peer, correct));
  assert.ok(state.hasNote(peer, wrong));
  assert.equal(state.enterDigit(emptyCell, wrong), state);
});

test('Notizen umschalten und löschen', () => {
  let state = GameState.create(puzzle).toggleNote(emptyCell, 3).toggleNote(emptyCell, 7);
  assert.ok(state.hasNote(emptyCell, 3) && state.hasNote(emptyCell, 7));
  state = state.toggleNote(emptyCell, 3);
  assert.ok(!state.hasNote(emptyCell, 3));
  assert.equal(state.erase(emptyCell).notes[emptyCell], 0);
});

test('strenger Modus zeigt nur Regelverstöße', () => {
  const peer = peers[emptyCell].find((c) => puzzle.givens[c] === 0 && puzzle.solution[c] !== wrong);
  let state = GameState.create(puzzle, -1, true).enterDigit(emptyCell, wrong);
  assert.ok(!state.isError(emptyCell));
  assert.equal(state.mistakes, 0);
  state = state.enterDigit(peer, wrong);
  assert.ok(state.isError(emptyCell) && state.isError(peer));
});

test('mit Tipps lösen und Einheiten erkennen', () => {
  let state = GameState.create(puzzle);
  for (const c of units[0]) state = state.reveal(c);
  assert.ok(state.completedUnits(units[0][0]).includes(0));
  for (let c = 0; c < 81; c++) state = state.reveal(c);
  assert.ok(state.isSolved);
  assert.equal(state.mistakes, 0);
});

test('Spielstand speichern und laden', () => {
  const state = GameState.create(puzzle, 20000).enterDigit(emptyCell, wrong).toggleNote(80 - emptyCell, 5)
    .copy({ elapsedSeconds: 123 });
  const restored = GameState.deserialize(state.serialize());
  assert.equal(restored.serialize(), state.serialize());
  assert.equal(restored.puzzle.code, 'M-000042');
  assert.equal(restored.dailyDay, 20000);
  assert.equal(GameState.deserialize('kaputt'), null);
});

test('Codes lesen und schreiben', () => {
  assert.equal(formatCode(MEDIUM, 4711), 'M-004711');
  assert.deepEqual(parseCode('M-004711'), [MEDIUM, 4711]);
  assert.deepEqual(parseCode(' e 12 '), [EXPERT, 12]);
  assert.deepEqual(parseCode('L999999'), [EASY, 999999]);
  assert.equal(parseCode('X-123'), null);
  assert.equal(parseCode('M-1234567'), null);
});

test('tägliches Rätsel hängt vom Tag ab', () => {
  const monday = 20752; // 2026-10-26
  assert.equal(daily(monday)[0], EASY);
  assert.equal(daily(monday + 4)[0], HARD);
  assert.equal(daily(monday + 6)[0], EXPERT);
  assert.notEqual(daily(monday)[1], daily(monday + 7)[1]);
  assert.ok(daily(monday)[1] >= 0 && daily(monday)[1] < SEEDS);
});

test('Statistik und Serien', () => {
  const record = (seconds, mistakes = 0, hints = 0, dailyDay = -1) =>
    ({ difficulty: 'MEDIUM', seconds, mistakes, hints, day: 1, dailyDay });
  const stats = new Stats().withStarted(MEDIUM).withRecord(record(300)).withRecord(record(200, 0, 1))
    .withRecord(record(400, 2)).withRecord(record(250)).withRecord(record(350));
  const medium = stats.of(MEDIUM);
  assert.equal(medium.solved, 5);
  assert.equal(medium.bestSeconds, 250);
  assert.equal(medium.averageSeconds, 300);
  assert.equal(medium.flawless, 3);
  assert.equal(medium.currentFlawlessStreak, 2);
  assert.equal(Stats.deserialize(stats.serialize()).serialize(), stats.serialize());

  const daily = new Stats().withRecord(record(1, 0, 0, 10)).withRecord(record(1, 0, 0, 11)).withRecord(record(1, 0, 0, 12));
  assert.equal(daily.dailyStreak(12), 3);
  assert.equal(daily.dailyStreak(13), 3);
  assert.equal(daily.dailyStreak(15), 0);
});

test('benachbarte Käfige bekommen verschiedene Farben', () => {
  for (let seed = 0; seed < 5; seed++) {
    const p = generateUnique(HARD, new KotlinRandom(seed));
    const colors = assignCageColors(p);
    for (let c = 0; c < 81; c++) {
      for (const n of neighbours[c]) {
        const a = p.cageOf[c];
        const b = p.cageOf[n];
        assert.ok(colors[a] >= 0 && colors[a] < COLOR_COUNT);
        if (a !== b) assert.notEqual(colors[a], colors[b]);
      }
    }
  }
});

test('Löserschritte passen zur Lösung', () => {
  for (const difficulty of DIFFICULTIES) {
    const p = generateFromSeed(difficulty, 7);
    const solver = new LogicSolver(p, p.givens);
    while (!solver.isSolved) {
      const s = solver.nextStep();
      if (!s) break;
      if (s.isPlacement) assert.equal(s.digit, p.solution[s.cell], s.text);
      for (const [cell, mask] of s.eliminations) assert.equal(mask & (1 << (p.solution[cell] - 1)), 0, s.text);
      solver.apply(s);
    }
  }
});

test('Kombinationen berücksichtigen Kandidaten und Eingaben', () => {
  const analysis = analyze([0b11, 0x1ff], 10);
  assert.deepEqual(analysis.viable.map(comboText), ['1+9', '2+8']);
  const cageIndex = puzzle.cageOf[emptyCell];
  const all = cageCombinations(GameState.create(puzzle), cageIndex);
  const solutionMask = puzzle.cages[cageIndex].cells.reduce((acc, c) => acc | (1 << (puzzle.solution[c] - 1)), 0);
  assert.ok(all.some((c) => c.digits === solutionMask && c.possible));
});
