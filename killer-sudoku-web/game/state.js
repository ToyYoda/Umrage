// Unveränderlicher Spielstand (wie GameState.kt in der App). Jede Aktion liefert einen neuen Zustand.
// Notizen sind Bitmasken (Bit 0 = Ziffer 1 … Bit 8 = Ziffer 9).
import { bit, peers, units, unitsOf } from './grid.js';
import { parseDigits, Puzzle } from './puzzle.js';

const VERSION = '2';

export class GameState {
  constructor(puzzle, values, notes, {
    mistakes = 0, hintsUsed = 0, elapsedSeconds = 0, dailyDay = -1, strict = false,
  } = {}) {
    this.puzzle = puzzle;
    this.values = values;
    this.notes = notes;
    this.mistakes = mistakes;
    this.hintsUsed = hintsUsed;
    this.elapsedSeconds = elapsedSeconds;
    /** Tag (Epoch-Day) des täglichen Rätsels, -1 für normale Spiele. */
    this.dailyDay = dailyDay;
    /** Strenger Modus: nur Regelverstöße markieren. Kommt aus den Einstellungen. */
    this.strict = strict;
    this._conflicts = null;
  }

  static create(puzzle, dailyDay = -1, strict = false) {
    return new GameState(puzzle, puzzle.givens.slice(), new Array(81).fill(0), { dailyDay, strict });
  }

  get isSolved() {
    return this.values.every((v, i) => v === this.puzzle.solution[i]);
  }

  get isDaily() {
    return this.dailyDay >= 0;
  }

  isGiven(cell) {
    return this.puzzle.givens[cell] !== 0;
  }

  isWrong(cell) {
    return this.values[cell] !== 0 && this.values[cell] !== this.puzzle.solution[cell];
  }

  /** Rot markieren: falsche Ziffer, im strengen Modus nur Regelverstöße. */
  isError(cell) {
    return this.strict ? this.conflicts[cell] : this.isWrong(cell);
  }

  /** Vorgaben und (außer im strengen Modus) richtige Ziffern sind fest. */
  isLocked(cell) {
    return this.isGiven(cell) || (!this.strict && this.values[cell] !== 0 && !this.isWrong(cell));
  }

  hasNote(cell, digit) {
    return (this.notes[cell] & bit(digit)) !== 0;
  }

  /** Wie oft eine Ziffer noch gesetzt werden muss (ohne strengen Modus: richtig gesetzt). */
  remaining(digit) {
    let count = 0;
    for (let c = 0; c < 81; c++) if (this.values[c] === digit && (this.strict || !this.isWrong(c))) count++;
    return 9 - count;
  }

  /** Felder mit Regelverstoß: doppelte Ziffer in Zeile, Spalte, Block oder Käfig, falsche Käfigsumme. */
  get conflicts() {
    if (this._conflicts) return this._conflicts;
    const { values, puzzle } = this;
    const result = new Array(81).fill(false);
    for (let cell = 0; cell < 81; cell++) {
      const v = values[cell];
      if (v === 0) continue;
      const cage = puzzle.cages[puzzle.cageOf[cell]].cells;
      if (peers[cell].some((p) => values[p] === v) || cage.some((c) => c !== cell && values[c] === v)) result[cell] = true;
    }
    for (const cage of puzzle.cages) {
      const sum = cage.cells.reduce((acc, c) => acc + values[c], 0);
      const full = cage.cells.every((c) => values[c] !== 0);
      if (sum > cage.sum || (full && sum !== cage.sum)) {
        cage.cells.forEach((c) => { if (values[c] !== 0 && !this.isGiven(c)) result[c] = true; });
      }
    }
    this._conflicts = result;
    return result;
  }

  /** Setzt eine Ziffer. Dieselbe Ziffer erneut tippen entfernt sie wieder. */
  enterDigit(cell, digit) {
    if (this.isLocked(cell)) return this;
    if (this.values[cell] === digit) return this.erase(cell);
    const values = this.values.slice();
    values[cell] = digit;
    const notes = this.notes.slice();
    const correct = this.puzzle.solution[cell] === digit;
    if (correct || this.strict) {
      notes[cell] = 0;
      for (const p of peers[cell]) notes[p] &= ~bit(digit);
    }
    return this.copy({ values, notes, mistakes: correct || this.strict ? this.mistakes : this.mistakes + 1 });
  }

  toggleNote(cell, digit) {
    if (this.values[cell] !== 0) return this;
    const notes = this.notes.slice();
    notes[cell] ^= bit(digit);
    return this.copy({ notes });
  }

  /** Entfernt eine (falsche) Ziffer bzw. alle Notizen des Feldes. */
  erase(cell) {
    if (this.isLocked(cell)) return this;
    if (this.values[cell] !== 0) {
      const values = this.values.slice();
      values[cell] = 0;
      return this.copy({ values });
    }
    if (this.notes[cell] !== 0) {
      const notes = this.notes.slice();
      notes[cell] = 0;
      return this.copy({ notes });
    }
    return this;
  }

  /** Setzt die richtige Ziffer (für Tipps). */
  reveal(cell) {
    if (this.values[cell] === this.puzzle.solution[cell]) return this;
    const values = this.values.slice();
    values[cell] = 0;
    const state = this.copy({ values, strict: false }).enterDigit(cell, this.puzzle.solution[cell]);
    return state.copy({ mistakes: this.mistakes, hintsUsed: this.hintsUsed + 1, strict: this.strict });
  }

  firstWrongCell() {
    for (let c = 0; c < 81; c++) if (this.isWrong(c)) return c;
    return -1;
  }

  /** Einheiten der Zelle, die vollständig und richtig ausgefüllt sind. */
  completedUnits(cell) {
    return unitsOf(cell).filter((u) => units[u].every((c) => this.values[c] === this.puzzle.solution[c]));
  }

  /** Bekannte richtige Ziffern – Grundlage für Tipps. */
  knownCorrect() {
    return this.values.map((v, c) => (this.isWrong(c) ? 0 : v));
  }

  copy(changes = {}) {
    return new GameState(changes.puzzle ?? this.puzzle, changes.values ?? this.values, changes.notes ?? this.notes, {
      mistakes: changes.mistakes ?? this.mistakes,
      hintsUsed: changes.hintsUsed ?? this.hintsUsed,
      elapsedSeconds: changes.elapsedSeconds ?? this.elapsedSeconds,
      dailyDay: this.dailyDay,
      strict: changes.strict ?? this.strict,
    });
  }

  serialize() {
    return [VERSION, this.puzzle.serialize(), this.values.join(''), this.notes.join(','), this.mistakes,
      this.hintsUsed, this.elapsedSeconds, this.dailyDay].join('|');
  }

  /** @returns null, wenn der Text kein gültiger Spielstand ist */
  static deserialize(text) {
    try {
      const parts = text.split('|');
      if (parts[0] !== VERSION || parts.length !== 8) return null;
      const notes = parts[3].split(',').map(Number);
      if (notes.length !== 81 || notes.some((n) => !Number.isInteger(n))) return null;
      return new GameState(Puzzle.deserialize(parts[1]), parseDigits(parts[2]), notes, {
        mistakes: Number(parts[4]), hintsUsed: Number(parts[5]), elapsedSeconds: Number(parts[6]), dailyDay: Number(parts[7]),
      });
    } catch {
      return null;
    }
  }
}
