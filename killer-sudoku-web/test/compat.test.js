// Prüft, dass die Web-Version dieselben Rätsel erzeugt und bewertet wie die Android-App.
// fixtures.tsv stammt aus der Kotlin-Version: Code, Bewertung, Rätsel, erster Tipp, Begründungen.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parseCode } from '../game/code.js';
import { generateFromSeed } from '../game/generator.js';
import { LogicSolver, rate } from '../game/logic.js';

const fixtures = readFileSync(new URL('./fixtures.tsv', import.meta.url), 'utf8')
  .trim().split('\n').map((line) => line.split('\t'));

for (const [code, rating, serialized, hintText, reasons] of fixtures) {
  test(`Rätsel ${code} wie in der App`, () => {
    const [difficulty, seed] = parseCode(code);
    const puzzle = generateFromSeed(difficulty, seed);
    assert.equal(puzzle.serialize(), serialized);
    assert.equal(rate(puzzle), Number(rating));
    const hint = new LogicSolver(puzzle, puzzle.givens).hint();
    assert.equal(hint?.step.text ?? '', hintText);
    assert.equal(hint ? hint.reasons.map((r) => r.text).join(' || ') : '', reasons ?? '');
  });
}
