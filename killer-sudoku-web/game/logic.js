// Löst Killer-Sudokus wie ein Mensch – Schritt für Schritt mit benannten Techniken (wie LogicSolver.kt).
// Wird für erklärende Tipps und die Schwierigkeitsbewertung benutzt. Reihenfolgen entsprechen der App.
import { analyze } from './combinations.js';
import {
  ALL, bit, capitalize, cellName, digitOf, digits, inUnit, peers, peerSets, popcount, units, unitSets, unitsOf,
} from './grid.js';

export const TECHNIQUES = {
  LAST_CELL: { level: 1, title: 'Letztes Feld im Käfig' },
  NAKED_SINGLE: { level: 1, title: 'Einzige Möglichkeit' },
  HIDDEN_SINGLE: { level: 1, title: 'Einziger Platz' },
  CAGE_COMBINATIONS: { level: 2, title: 'Käfig-Kombinationen' },
  RULE_45: { level: 3, title: '45er-Regel' },
  CAGE_UNIT_DIGIT: { level: 3, title: 'Ziffer muss in den Käfig' },
  CAGE_POINTING: { level: 3, title: 'Pflichtziffer im Käfig' },
  LOCKED_CANDIDATES: { level: 3, title: 'Block und Linie' },
  NAKED_SUBSET: { level: 4, title: 'Paare und Tripel' },
  HIDDEN_PAIR: { level: 4, title: 'Verstecktes Paar' },
  TRIAL: { level: 5, title: 'Ausprobieren' },
};

export const UNSOLVABLE = 6;
const MAX_STEPS = 1000;
const TRIAL_TECHNIQUES = 'einzige Möglichkeiten, Käfig-Kombinationen und 45er-Regel';

/**
 * Ein Schritt: Ziffer setzen (cell >= 0) oder Kandidaten streichen.
 * eliminations: Map Feld → Bitmaske der gestrichenen Kandidaten; focus: Felder der Begründung.
 */
function step(technique, text, { cell = -1, digit = 0, eliminations = new Map(), focus = [] } = {}) {
  return { technique, text, cell, digit, eliminations, focus, isPlacement: cell >= 0 };
}

export class LogicSolver {
  /** @param known bekannte, richtige Ziffern (Vorgaben und korrekte Eingaben), 0 = leer */
  constructor(puzzle, known, groups45 = buildGroups45(puzzle)) {
    this.puzzle = puzzle;
    this.groups45 = groups45;
    this.values = new Array(81).fill(0);
    this.candidates = new Array(81).fill(ALL);
    for (let cell = 0; cell < 81; cell++) if (known[cell] !== 0) this.place(cell, known[cell]);
  }

  copy() {
    const other = new LogicSolver(this.puzzle, this.values, this.groups45);
    other.candidates = this.candidates.slice();
    return other;
  }

  get isSolved() {
    return this.values.every((v) => v !== 0);
  }

  /** Der einfachste nächste Schritt oder null, wenn die Techniken (bis Stufe maxLevel) nicht weiterhelfen. */
  nextStep(maxLevel = 5) {
    const s = this.lastCell() ?? this.nakedSingle() ?? this.hiddenSingle() ??
      (maxLevel < 2 ? null : this.cageCombinations());
    if (s || maxLevel < 3) return s;
    return this.rule45() ?? this.cageUnitDigit() ?? this.cagePointing() ?? this.lockedCandidates() ??
      (maxLevel < 4 ? null : this.nakedSubset() ?? this.hiddenPair()) ??
      (maxLevel < 5 ? null : this.trial());
  }

  apply(s) {
    for (const [cell, mask] of s.eliminations) this.candidates[cell] &= ~mask;
    if (s.isPlacement) this.place(s.cell, s.digit);
  }

  /** Nächster Schritt, der eine Ziffer setzt, samt der dafür nötigen Streichungen. */
  hint() {
    const history = [];
    for (let i = 0; i < MAX_STEPS; i++) {
      const s = this.nextStep();
      if (!s) return null;
      if (s.isPlacement) return { step: s, reasons: history.filter((prior) => dependsOn(s, prior)).slice(-3) };
      this.apply(s);
      history.push(s);
    }
    return null;
  }

  place(cell, digit) {
    const b = bit(digit);
    this.values[cell] = digit;
    this.candidates[cell] = b;
    for (const p of peers[cell]) if (this.values[p] === 0) this.candidates[p] &= ~b;
    for (const o of this.cageCells(cell)) if (o !== cell && this.values[o] === 0) this.candidates[o] &= ~b;
  }

  cageCells(cell) {
    return this.puzzle.cages[this.puzzle.cageOf[cell]].cells;
  }

  // ---------------------------------------------------------------- Techniken

  lastCell() {
    for (const cage of this.puzzle.cages) {
      const empty = cage.cells.filter((c) => this.values[c] === 0);
      if (empty.length !== 1) continue;
      const placed = cage.cells.reduce((acc, c) => acc + this.values[c], 0);
      const cell = empty[0];
      const digit = cage.sum - placed;
      if (digit < 1 || digit > 9 || (this.candidates[cell] & bit(digit)) === 0) continue;
      const text = cage.cells.length === 1
        ? `Dieses Feld ist ein eigener Käfig mit der Summe ${cage.sum}, also steht hier die ${digit}.`
        : `Im Käfig mit der Summe ${cage.sum} ist nur noch dieses Feld frei: ${cage.sum} − ${placed} = ${digit}.`;
      return step(TECHNIQUES.LAST_CELL, text, { cell, digit, focus: cage.cells });
    }
    return null;
  }

  nakedSingle() {
    for (let cell = 0; cell < 81; cell++) {
      if (this.values[cell] !== 0 || popcount(this.candidates[cell]) !== 1) continue;
      const digit = digitOf(this.candidates[cell]);
      return step(TECHNIQUES.NAKED_SINGLE,
        `Für dieses Feld bleibt nur die ${digit} – alle anderen Ziffern sind durch Zeile, Spalte, Block ` +
        'oder Käfig ausgeschlossen.', { cell, digit });
    }
    return null;
  }

  hiddenSingle() {
    for (let u = 0; u < 27; u++) {
      const unit = units[u];
      for (let digit = 1; digit <= 9; digit++) {
        if (unit.some((c) => this.values[c] === digit)) continue;
        const places = unit.filter((c) => this.values[c] === 0 && (this.candidates[c] & bit(digit)) !== 0);
        if (places.length !== 1) continue;
        return step(TECHNIQUES.HIDDEN_SINGLE, `Die ${digit} kann ${inUnit(u)} nur noch in diesem Feld stehen.`,
          { cell: places[0], digit, focus: unit });
      }
    }
    return null;
  }

  cageCombinations() {
    for (const cage of this.puzzle.cages) {
      const group = this.analyzeGroup(cage.cells, cage.sum);
      if (!group) continue;
      const eliminations = this.eliminationsFrom(group.empty, group.analysis.allowed);
      if (eliminations.size === 0) continue;
      return step(TECHNIQUES.CAGE_COMBINATIONS, `${groupIntro(cage.sum, group)} ${eliminationText(eliminations)}`,
        { eliminations, focus: cage.cells });
    }
    return null;
  }

  rule45() {
    for (const group of this.groups45) {
      const empty = group.cells.filter((c) => this.values[c] === 0);
      if (empty.length === 0) continue;
      const placed = group.cells.filter((c) => this.values[c] !== 0).map((c) => this.values[c]);
      const remaining = group.sum - placed.reduce((a, b) => a + b, 0);
      if (empty.length === 1) {
        const cell = empty[0];
        if (remaining < 1 || remaining > 9 || (this.candidates[cell] & bit(remaining)) === 0) continue;
        const calc = placed.length === 0 ? ''
          : ` Abzüglich der schon gesetzten ${placed.join(' + ')} bleibt die ${remaining}.`;
        return step(TECHNIQUES.RULE_45, group.reason + calc, { cell, digit: remaining, focus: group.focus });
      }
      let allowed;
      if (group.distinct) {
        const placedMask = placed.reduce((acc, d) => acc | bit(d), 0);
        const result = analyze(empty.map((c) => this.candidates[c] & ~placedMask), remaining).allowed;
        allowed = result.some((m) => m !== 0) ? result : null;
      } else {
        allowed = this.allowedAssignments(empty, remaining);
      }
      if (!allowed) continue;
      const eliminations = this.eliminationsFrom(empty, allowed);
      if (eliminations.size === 0) continue;
      const rest = placed.length === 0 ? ''
        : ` Abzüglich der schon gesetzten ${placed.join(' + ')} bleiben für die freien Felder ${remaining}.`;
      return step(TECHNIQUES.RULE_45, `${group.reason}${rest} ${eliminationText(eliminations)}`,
        { eliminations, focus: group.focus });
    }
    return null;
  }

  /** Kann eine Ziffer in einer Einheit nur in Feldern eines Käfigs stehen, muss der Käfig sie enthalten. */
  cageUnitDigit() {
    const { cages, cageOf } = this.puzzle;
    for (let index = 0; index < cages.length; index++) {
      const cage = cages[index];
      const group = this.analyzeGroup(cage.cells, cage.sum);
      if (!group) continue;
      let must = 0;
      let reasonUnit = -1;
      let reasonDigit = 0;
      for (const u of [...new Set(cage.cells.flatMap(unitsOf))]) {
        const unit = units[u];
        for (let digit = 1; digit <= 9; digit++) {
          if ((must & bit(digit)) !== 0 || unit.some((c) => this.values[c] === digit)) continue;
          const places = unit.filter((c) => this.values[c] === 0 && (this.candidates[c] & bit(digit)) !== 0);
          if (places.length > 0 && places.every((c) => cageOf[c] === index)) {
            must |= bit(digit);
            reasonUnit = u;
            reasonDigit = digit;
          }
        }
      }
      if (must === 0 || group.analysis.viable.every((combo) => (combo & must) === must)) continue;
      const restricted = analyze(group.empty.map((c) => this.candidates[c] & ~group.placed), group.remainingSum, must);
      if (restricted.viable.length === 0) continue;
      const eliminations = this.eliminationsFrom(group.empty, restricted.allowed);
      if (eliminations.size === 0) continue;
      const text = `Die ${reasonDigit} kann ${inUnit(reasonUnit)} nur in Feldern des Käfigs mit der Summe ` +
        `${cage.sum} stehen – der Käfig muss sie also enthalten. Damit bleibt nur ` +
        `${combosText(restricted.viable)}. ${eliminationText(eliminations)}`;
      return step(TECHNIQUES.CAGE_UNIT_DIGIT, text, { eliminations, focus: cage.cells });
    }
    return null;
  }

  /** Erlaubte Ziffern je Feld für eine Summe; sich sehende Felder müssen verschieden sein. */
  allowedAssignments(cells, sum) {
    const k = cells.length;
    const allowed = new Array(k).fill(0);
    const current = new Array(k).fill(0);
    const { cageOf } = this.puzzle;
    const conflict = (a, b) => peerSets[a].has(b) || cageOf[a] === cageOf[b];
    const search = (i, remaining) => {
      if (i === k) {
        if (remaining !== 0) return false;
        for (let j = 0; j < k; j++) allowed[j] |= bit(current[j]);
        return true;
      }
      let found = false;
      for (let digit = 1; digit <= Math.min(9, remaining); digit++) {
        if ((this.candidates[cells[i]] & bit(digit)) === 0) continue;
        let clash = false;
        for (let j = 0; j < i; j++) if (current[j] === digit && conflict(cells[j], cells[i])) clash = true;
        if (clash) continue;
        current[i] = digit;
        if (search(i + 1, remaining - digit)) found = true;
      }
      return found;
    };
    return search(0, sum) ? allowed : null;
  }

  /** Eine Ziffer, die in jeder Kombination eines Käfigs vorkommt, aber nur in einer Einheit Platz hat. */
  cagePointing() {
    for (const cage of this.puzzle.cages) {
      const group = this.analyzeGroup(cage.cells, cage.sum);
      if (!group) continue;
      let required = group.analysis.required;
      while (required !== 0) {
        const b = required & -required;
        required ^= b;
        const places = group.empty.filter((c) => (this.candidates[c] & b) !== 0);
        for (const u of commonUnits(places)) {
          const eliminations = new Map();
          for (const c of units[u]) {
            if (this.values[c] === 0 && !cage.cells.includes(c) && (this.candidates[c] & b) !== 0) eliminations.set(c, b);
          }
          if (eliminations.size === 0) continue;
          const digit = digitOf(b);
          const text = `Jede mögliche Kombination im Käfig mit der Summe ${cage.sum} ` +
            `(${combosText(group.analysis.viable)}) enthält die ${digit}. Im Käfig kann sie nur ` +
            `${inUnit(u)} stehen – also nirgends sonst ${inUnit(u)}. ${eliminationText(eliminations)}`;
          return step(TECHNIQUES.CAGE_POINTING, text, { eliminations, focus: cage.cells });
        }
      }
    }
    return null;
  }

  lockedCandidates() {
    for (let u = 0; u < 27; u++) {
      const unit = units[u];
      for (let digit = 1; digit <= 9; digit++) {
        const b = bit(digit);
        const places = unit.filter((c) => this.values[c] === 0 && (this.candidates[c] & b) !== 0);
        if (places.length < 2) continue;
        for (const other of commonUnits(places)) {
          if (other === u || (u < 18) === (other < 18)) continue;
          const eliminations = new Map();
          for (const c of units[other]) {
            if (this.values[c] === 0 && !unitSets[u].has(c) && (this.candidates[c] & b) !== 0) eliminations.set(c, b);
          }
          if (eliminations.size === 0) continue;
          const text = `Die ${digit} kann ${inUnit(u)} nur in Feldern stehen, die auch ` +
            `${inUnit(other)} liegen. Also steht sie ${inUnit(other)} sicher dort ` +
            `und nirgends sonst. ${eliminationText(eliminations)}`;
          return step(TECHNIQUES.LOCKED_CANDIDATES, text, { eliminations, focus: unit });
        }
      }
    }
    return null;
  }

  nakedSubset() {
    for (let size = 2; size <= 3; size++) {
      for (let u = 0; u < 27; u++) {
        const empty = units[u].filter((c) => this.values[c] === 0);
        if (empty.length <= size) continue;
        for (const subset of subsets(empty, size)) {
          const union = subset.reduce((acc, c) => acc | this.candidates[c], 0);
          if (popcount(union) !== size) continue;
          const eliminations = new Map();
          for (const c of empty) {
            if (!subset.includes(c) && (this.candidates[c] & union) !== 0) eliminations.set(c, this.candidates[c] & union);
          }
          if (eliminations.size === 0) continue;
          const text = `${capitalize(inUnit(u))} kommen in ${subset.map(cellName).join(', ')} nur die Ziffern ` +
            `${digitsText(union)} infrage. Sie müssen genau dort stehen und fallen in den anderen Feldern weg. ` +
            eliminationText(eliminations);
          return step(TECHNIQUES.NAKED_SUBSET, text, { eliminations, focus: subset });
        }
      }
    }
    return null;
  }

  hiddenPair() {
    for (let u = 0; u < 27; u++) {
      const unit = units[u];
      const positions = new Array(10).fill(0);
      for (let digit = 1; digit <= 9; digit++) {
        unit.forEach((cell, i) => {
          if (this.values[cell] === 0 && (this.candidates[cell] & bit(digit)) !== 0) positions[digit] |= 1 << i;
        });
      }
      for (let d1 = 1; d1 <= 9; d1++) {
        for (let d2 = d1 + 1; d2 <= 9; d2++) {
          if (popcount(positions[d1]) !== 2 || positions[d1] !== positions[d2]) continue;
          const pair = bit(d1) | bit(d2);
          const cells = unit.filter((_, i) => (positions[d1] & (1 << i)) !== 0);
          const eliminations = new Map();
          for (const c of cells) if ((this.candidates[c] & ~pair) !== 0) eliminations.set(c, this.candidates[c] & ~pair);
          if (eliminations.size === 0) continue;
          const text = `Die ${d1} und die ${d2} können ${inUnit(u)} nur in ${cells.map(cellName).join(' und ')} ` +
            `stehen. Also stehen dort keine anderen Ziffern. ${eliminationText(eliminations)}`;
          return step(TECHNIQUES.HIDDEN_PAIR, text, { eliminations, focus: cells });
        }
      }
    }
    return null;
  }

  /** Annahme durchspielen: Führt eine Ziffer mit einfachen Schritten zum Widerspruch, fällt sie weg. */
  trial() {
    const cells = [];
    for (let c = 0; c < 81; c++) if (this.values[c] === 0) cells.push(c);
    cells.sort((a, b) => popcount(this.candidates[a]) - popcount(this.candidates[b]));
    for (const cell of cells) {
      if (popcount(this.candidates[cell]) > 3) break;
      let cand = this.candidates[cell];
      while (cand !== 0) {
        const b = cand & -cand;
        cand ^= b;
        const digit = digitOf(b);
        const contradiction = this.copy().contradictionAfter(cell, digit);
        if (contradiction === null) continue;
        return step(TECHNIQUES.TRIAL,
          `Angenommen, in ${cellName(cell)} stünde die ${digit}: Mit einfachen Schritten ` +
          `(${TRIAL_TECHNIQUES}) folgt dann, dass ${contradiction}. Also kann dort keine ${digit} stehen.`,
          { eliminations: new Map([[cell, b]]), focus: [cell] });
      }
    }
    return null;
  }

  contradictionAfter(cell, digit) {
    this.place(cell, digit);
    for (let i = 0; i < MAX_STEPS; i++) {
      const found = this.contradiction();
      if (found !== null) return found;
      const s = this.lastCell() ?? this.nakedSingle() ?? this.hiddenSingle() ?? this.cageCombinations() ??
        this.rule45() ?? this.cageUnitDigit() ?? this.cagePointing() ?? this.lockedCandidates();
      if (!s) return null;
      this.apply(s);
    }
    return null;
  }

  contradiction() {
    for (let cell = 0; cell < 81; cell++) {
      if (this.values[cell] === 0 && this.candidates[cell] === 0) return `für ${cellName(cell)} keine Ziffer mehr bleibt`;
    }
    for (let u = 0; u < 27; u++) {
      const seen = units[u].reduce((acc, c) => acc | (this.values[c] !== 0 ? bit(this.values[c]) : this.candidates[c]), 0);
      if (seen !== ALL) return `die ${digitOf(ALL & ~seen)} ${inUnit(u)} keinen Platz mehr hat`;
    }
    for (const cage of this.puzzle.cages) {
      const empty = cage.cells.filter((c) => this.values[c] === 0);
      const remaining = cage.sum - cage.cells.reduce((acc, c) => acc + this.values[c], 0);
      const placed = cage.cells.reduce((acc, c) => (this.values[c] !== 0 ? acc | bit(this.values[c]) : acc), 0);
      const impossible = empty.length === 0 ? remaining !== 0
        : analyze(empty.map((c) => this.candidates[c] & ~placed), remaining).viable.length === 0;
      if (impossible) return `sich der Käfig mit der Summe ${cage.sum} nicht mehr füllen lässt`;
    }
    return null;
  }

  // ---------------------------------------------------------------- Hilfsfunktionen

  /** Kombinationen für die freien Felder einer Gruppe. null, wenn sie voll ist oder keine passt. */
  analyzeGroup(cells, sum) {
    const empty = cells.filter((c) => this.values[c] === 0);
    if (empty.length === 0) return null;
    const placed = cells.reduce((acc, c) => (this.values[c] !== 0 ? acc | bit(this.values[c]) : acc), 0);
    const remainingSum = sum - cells.reduce((acc, c) => acc + this.values[c], 0);
    const analysis = analyze(empty.map((c) => this.candidates[c] & ~placed), remainingSum);
    if (analysis.viable.length === 0) return null;
    return { empty, placed, remainingSum, analysis };
  }

  eliminationsFrom(empty, allowed) {
    const result = new Map();
    empty.forEach((cell, i) => {
      const removed = this.candidates[cell] & ~allowed[i];
      if (removed !== 0) result.set(cell, removed);
    });
    return result;
  }
}

function dependsOn(s, prior) {
  for (const [cell, mask] of prior.eliminations) {
    if (s.technique === TECHNIQUES.HIDDEN_SINGLE) {
      if (s.focus.includes(cell) && (mask & bit(s.digit)) !== 0) return true;
    } else if (cell === s.cell) {
      return true;
    }
  }
  return false;
}

function groupIntro(sum, group) {
  const k = group.empty.length;
  const felder = k === 1 ? '1 Feld' : `${k} Felder`;
  const start = group.placed === 0
    ? `Der Käfig mit der Summe ${sum} hat ${felder}.`
    : `Im Käfig mit der Summe ${sum} steht schon ${digitsText(group.placed)}; die übrigen ${felder} ` +
      `ergeben zusammen ${group.remainingSum}.`;
  return `${start} Möglich ist nur ${combosText(group.analysis.viable)}.`;
}

/** Einheiten (Zeile, Spalte, Block), die alle Felder enthalten. */
function commonUnits(cells) {
  if (cells.length === 0) return [];
  return unitsOf(cells[0]).filter((u) => cells.every((c) => unitSets[u].has(c)));
}

/** Bereiche für die 45er-Regel: Name („In Zeile 3“) und Einheiten. */
function regions() {
  const result = [];
  for (let size = 1; size <= 4; size++) {
    for (let start = 0; start <= 9 - size; start++) {
      const range = size === 1 ? `${start + 1}` : `${start + 1} bis ${start + size}`;
      result.push([size === 1 ? `In Zeile ${range}` : `In den Zeilen ${range}`, Array.from({ length: size }, (_, i) => start + i)]);
      result.push([size === 1 ? `In Spalte ${range}` : `In den Spalten ${range}`, Array.from({ length: size }, (_, i) => 9 + start + i)]);
    }
  }
  for (let b = 0; b < 9; b++) result.push([capitalize(inUnit(18 + b)), [18 + b]]);
  return result;
}

function buildGroups45(puzzle) {
  const seen = new Set();
  const result = [];
  const distinct = (cells) =>
    commonUnits(cells).length > 0 || cells.every((c) => puzzle.cageOf[c] === puzzle.cageOf[cells[0]]);
  for (const [name, regionUnits] of regions()) {
    const region = [...new Set(regionUnits.flatMap((u) => units[u]))];
    const regionSet = new Set(region);
    const total = 45 * regionUnits.length;
    const touching = [...new Set(region.map((c) => puzzle.cageOf[c]))];
    const inside = touching.filter((k) => puzzle.cages[k].cells.every((c) => regionSet.has(c)));
    const insideSet = new Set(inside);
    const insideSum = inside.reduce((acc, k) => acc + puzzle.cages[k].sum, 0);
    const alle = `45er-Regel: ${name} ergeben alle Felder zusammen ${total}.`;

    const innies = region.filter((c) => !insideSet.has(puzzle.cageOf[c])).sort((a, b) => a - b);
    if (inside.length > 0 && innies.length >= 1 && innies.length <= 4 && !seen.has(innies.join(','))) {
      seen.add(innies.join(','));
      const n = innies.length === 1 ? 'Das übrige Feld ist' : `Die übrigen ${innies.length} Felder ergeben`;
      result.push({
        cells: innies,
        sum: total - insideSum,
        reason: `${alle} Die Käfige, die ganz darin liegen, ergeben ${insideSum}. ${n} daher ${total} − ${insideSum} = ` +
          `${total - insideSum}.`,
        focus: region,
        distinct: distinct(innies),
      });
    }

    const touchingSum = touching.reduce((acc, k) => acc + puzzle.cages[k].sum, 0);
    const outies = touching.flatMap((k) => puzzle.cages[k].cells.filter((c) => !regionSet.has(c))).sort((a, b) => a - b);
    if (outies.length >= 1 && outies.length <= 4 && !seen.has(outies.join(','))) {
      seen.add(outies.join(','));
      const n = outies.length === 1 ? 'Das eine herausragende Feld ist' : `Die ${outies.length} herausragenden Felder ergeben`;
      result.push({
        cells: outies,
        sum: touchingSum - total,
        reason: `${alle} Die Käfige, die dort liegen oder hineinragen, ergeben ${touchingSum}. ${n} daher ` +
          `${touchingSum} − ${total} = ${touchingSum - total}.`,
        focus: [...new Set([...region, ...outies])],
        distinct: distinct(outies),
      });
    }
  }
  return result;
}

/**
 * Schwierigkeit 1–5 nach der schwersten nötigen Technik, UNSOLVABLE, wenn die Techniken nicht reichen.
 * Mit maxLevel bricht die Bewertung ab, sobald mehr nötig ist, und liefert dann maxLevel + 1.
 */
export function rate(puzzle, maxLevel = 5) {
  const solver = new LogicSolver(puzzle, puzzle.givens);
  let level = 1;
  for (let i = 0; i < MAX_STEPS; i++) {
    if (solver.isSolved) return level;
    const s = solver.nextStep(maxLevel);
    if (!s) return maxLevel < 5 ? maxLevel + 1 : UNSOLVABLE;
    level = Math.max(level, s.technique.level);
    solver.apply(s);
  }
  return UNSOLVABLE;
}

/** „die 5“ bzw. „2, 5 und 7“. */
export function digitsText(mask) {
  const list = digits(mask);
  return list.length === 1 ? `die ${list[0]}` : `${list.slice(0, -1).join(', ')} und ${list[list.length - 1]}`;
}

export const comboText = (mask) => digits(mask).join('+');

function combosText(combos) {
  const shown = combos.slice(0, 6).map(comboText);
  if (combos.length > 6) return `${shown.join(', ')} …`;
  if (shown.length === 1) return shown[0];
  return `${shown.slice(0, -1).join(', ')} oder ${shown[shown.length - 1]}`;
}

function eliminationText(eliminations) {
  const entries = [...eliminations.entries()];
  if (entries.length === 1 && popcount(entries[0][1]) === 1) {
    return `Damit fällt die ${digitOf(entries[0][1])} in ${cellName(entries[0][0])} weg.`;
  }
  const groups = new Map();
  for (const [cell, mask] of entries) {
    if (!groups.has(mask)) groups.set(mask, []);
    groups.get(mask).push(cell);
  }
  const parts = [...groups].map(([mask, cells]) =>
    `${listText(digits(mask).map(String))} in ${listText(cells.slice().sort((a, b) => a - b).map(cellName))}`);
  return `Damit fallen weg: ${parts.join('; ')}.`;
}

const listText = (items) =>
  items.length === 1 ? items[0] : `${items.slice(0, -1).join(', ')} und ${items[items.length - 1]}`;

function subsets(items, size) {
  if (size === 0) return [[]];
  if (items.length < size) return [];
  const [first, ...rest] = items;
  return [...subsets(rest, size - 1).map((s) => [first, ...s]), ...subsets(rest, size)];
}
