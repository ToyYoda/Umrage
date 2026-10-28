// Rechenintensive Arbeit außerhalb des UI-Threads: Rätsel erzeugen und Tipps suchen.
import { generateFromSeed } from './game/generator.js';
import { LogicSolver } from './game/logic.js';
import { difficultyByName, Puzzle } from './game/puzzle.js';

self.onmessage = ({ data }) => {
  const { id, type } = data;
  try {
    if (type === 'generate') {
      const puzzle = generateFromSeed(difficultyByName(data.difficulty), data.seed);
      self.postMessage({ id, puzzle: puzzle.serialize() });
    } else if (type === 'hint') {
      const puzzle = Puzzle.deserialize(data.puzzle);
      const hint = new LogicSolver(puzzle, data.known).hint();
      const plain = (s) => ({ title: s.technique.title, text: s.text, cell: s.cell, digit: s.digit, focus: s.focus });
      self.postMessage({ id, hint: hint && { step: plain(hint.step), reasons: hint.reasons.map(plain) } });
    }
  } catch (error) {
    self.postMessage({ id, error: String(error) });
  }
};
