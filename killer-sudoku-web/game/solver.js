// Backtracking-Löser mit Propagation (wie Solver in der App). Prüft, ob ein Rätsel genau eine Lösung hat.
// Die Reihenfolge der Schritte muss exakt der App entsprechen, sonst erzeugen Codes andere Rätsel.
import { combinations, isPossible } from './cagesums.js';
import { ALL, box, col, ctz, popcount, row, units } from './grid.js';

export class Solver {
  constructor(cages, givens) {
    const groups = cages.map((cage) => [cage.sum, cage.cells]);
    const cageOf = new Array(81).fill(0);
    cages.forEach((cage, index) => cage.cells.forEach((c) => { cageOf[c] = index; }));
    for (const unit of units) {
      const unitSet = new Set(unit);
      const inside = [...new Set(unit.map((c) => cageOf[c]))].filter((c) => cages[c].cells.every((x) => unitSet.has(x)));
      const rest = unit.filter((c) => !inside.includes(cageOf[c]));
      if (inside.length > 0 && rest.length > 0) {
        groups.push([45 - inside.reduce((acc, c) => acc + cages[c].sum, 0), rest]);
      }
    }
    this.groupCells = groups.map((g) => g[1]);
    this.groupRemainingSum = groups.map((g) => g[0]);
    this.groupRemainingCells = groups.map((g) => g[1].length);
    this.groupUsed = new Array(groups.length).fill(0);
    this.groupsOfCell = Array.from({ length: 81 }, (_, cell) =>
      this.groupCells.map((_, i) => i).filter((i) => this.groupCells[i].includes(cell)));

    this.grid = new Int32Array(81);
    this.rowMask = new Int32Array(9);
    this.colMask = new Int32Array(9);
    this.boxMask = new Int32Array(9);
    this.basic = new Int32Array(81);
    this.candidates = new Int32Array(81);
    this.trail = new Int32Array(81);
    this.trailSize = 0;
    this.nodes = 0;
    this.found = [];
    this.consistent = true;

    for (let cell = 0; cell < 81; cell++) {
      const d = givens[cell];
      if (d !== 0 && !this.place(cell, d)) this.consistent = false;
    }
  }

  /** @returns {{count:number, solutions:Int32Array[], aborted:boolean}} */
  solve(limit = 2, nodeLimit = 200000, preferred = null) {
    this.limit = limit;
    this.nodeLimit = nodeLimit;
    this.preferred = preferred;
    this.nodes = 0;
    this.found = [];
    const completed = !this.consistent || this.search();
    return { count: this.found.length, solutions: this.found.slice(), aborted: !completed };
  }

  /** @returns false, wenn wegen des Knotenlimits abgebrochen wurde */
  search() {
    if (++this.nodes > this.nodeLimit) return false;
    const mark = this.trailSize;
    try {
      if (!this.propagate()) return true;

      let bestCell = -1;
      let bestCount = 10;
      for (let cell = 0; cell < 81; cell++) {
        if (this.grid[cell] !== 0) continue;
        const count = popcount(this.candidates[cell]);
        if (count < bestCount) {
          bestCount = count;
          bestCell = cell;
        }
      }
      if (bestCell === -1) {
        this.found.push(this.grid.slice());
        return true;
      }

      let cand = this.candidates[bestCell];
      const first = this.preferred ? 1 << (this.preferred[bestCell] - 1) : 0;
      while (cand !== 0) {
        const b = (cand & first) !== 0 ? first : cand & -cand;
        cand ^= b;
        const branchMark = this.trailSize;
        if (this.place(bestCell, ctz(b) + 1)) {
          if (!this.search()) return false;
        }
        this.undo(branchMark);
        if (this.found.length >= this.limit) return true;
      }
      return true;
    } finally {
      this.undo(mark);
    }
  }

  propagate() {
    const grid = this.grid;
    const candidates = this.candidates;
    for (;;) {
      if (!this.computeCandidates()) return false;
      let progress = false;

      for (let cell = 0; cell < 81; cell++) {
        if (grid[cell] !== 0) continue;
        const cand = candidates[cell];
        if (popcount(cand) === 1) {
          if (!this.place(cell, ctz(cand) + 1)) return false;
          progress = true;
        }
      }
      if (progress) continue;

      // Ziffern, die in jeder möglichen Kombination einer Gruppe vorkommen, aber nur in eine Zelle passen.
      for (let g = 0; g < this.groupCells.length; g++) {
        const remainingCells = this.groupRemainingCells[g];
        if (remainingCells === 0) continue;
        let union = 0;
        for (const cell of this.groupCells[g]) if (grid[cell] === 0) union |= candidates[cell];
        let required = ALL;
        let possible = 0;
        for (const combo of combinations(remainingCells, this.groupRemainingSum[g])) {
          if ((combo & ~union) === 0) {
            required &= combo;
            possible |= combo;
          }
        }
        if (possible === 0) return false;
        while (required !== 0) {
          const b = required & -required;
          required ^= b;
          let target = -1;
          let count = 0;
          for (const cell of this.groupCells[g]) {
            if (grid[cell] === 0 && (candidates[cell] & b) !== 0) {
              target = cell;
              count++;
            }
          }
          if (count === 0) return false;
          if (count === 1) {
            if (!this.place(target, ctz(b) + 1)) return false;
            progress = true;
          }
        }
        if (progress) break;
      }
      if (progress) continue;

      for (const unit of units) {
        let placed = 0;
        let once = 0;
        let twice = 0;
        for (const cell of unit) {
          if (grid[cell] !== 0) {
            placed |= 1 << (grid[cell] - 1);
          } else {
            const cand = candidates[cell];
            twice |= once & cand;
            once |= cand;
          }
        }
        if ((placed | once) !== ALL) return false;
        let single = once & ~twice & ~placed;
        while (single !== 0) {
          const b = single & -single;
          single ^= b;
          const cell = unit.find((c) => grid[c] === 0 && (candidates[c] & b) !== 0);
          if (cell === undefined) return false;
          if (!this.place(cell, ctz(b) + 1)) return false;
          progress = true;
        }
        if (progress) break;
      }
      if (!progress) return true;
    }
  }

  computeCandidates() {
    const { grid, basic, candidates } = this;
    for (let cell = 0; cell < 81; cell++) {
      if (grid[cell] !== 0) continue;
      let mask = ALL & ~(this.rowMask[row(cell)] | this.colMask[col(cell)] | this.boxMask[box(cell)]);
      for (const g of this.groupsOfCell[cell]) mask &= ~this.groupUsed[g];
      basic[cell] = mask;
    }
    for (let cell = 0; cell < 81; cell++) {
      if (grid[cell] !== 0) continue;
      let cand = basic[cell];
      for (const g of this.groupsOfCell[cell]) {
        let others = 0;
        for (const other of this.groupCells[g]) if (other !== cell && grid[other] === 0) others |= basic[other];
        const remainingCells = this.groupRemainingCells[g] - 1;
        const remainingSum = this.groupRemainingSum[g];
        let c = cand;
        while (c !== 0) {
          const b = c & -c;
          c ^= b;
          const d = ctz(b) + 1;
          if (!isPossible(others & ~b, remainingCells, remainingSum - d)) cand ^= b;
        }
      }
      if (cand === 0) return false;
      candidates[cell] = cand;
    }
    return true;
  }

  /** @returns false, wenn die Ziffer gegen eine Regel verstößt (der Zug wird trotzdem eingetragen) */
  place(cell, d) {
    const b = 1 << (d - 1);
    const r = row(cell);
    const c = col(cell);
    const x = box(cell);
    let ok = ((this.rowMask[r] | this.colMask[c] | this.boxMask[x]) & b) === 0;
    this.grid[cell] = d;
    this.trail[this.trailSize++] = cell;
    this.rowMask[r] |= b;
    this.colMask[c] |= b;
    this.boxMask[x] |= b;
    for (const g of this.groupsOfCell[cell]) {
      if ((this.groupUsed[g] & b) !== 0) ok = false;
      this.groupUsed[g] |= b;
      this.groupRemainingSum[g] -= d;
      this.groupRemainingCells[g]--;
      const sum = this.groupRemainingSum[g];
      if (sum < 0 || (this.groupRemainingCells[g] === 0 && sum !== 0)) ok = false;
    }
    return ok;
  }

  undo(mark) {
    while (this.trailSize > mark) {
      const cell = this.trail[--this.trailSize];
      const d = this.grid[cell];
      const b = ~(1 << (d - 1));
      this.grid[cell] = 0;
      this.rowMask[row(cell)] &= b;
      this.colMask[col(cell)] &= b;
      this.boxMask[box(cell)] &= b;
      for (const g of this.groupsOfCell[cell]) {
        if (!this.groupCells[g].some((o) => o !== cell && this.grid[o] === d)) this.groupUsed[g] &= b;
        this.groupRemainingSum[g] += d;
        this.groupRemainingCells[g]++;
      }
    }
  }
}
