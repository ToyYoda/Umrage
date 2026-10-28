// Schwierigkeitsgrade und Rätsel (wie Puzzle.kt in der App).

export const DIFFICULTIES = [
  { name: 'EASY', label: 'Leicht', letter: 'L', weights: [0, 0, 45, 40, 15], givens: 6, rating: [1, 2],
    description: 'Kleine Käfige, einige Ziffern vorgegeben' },
  { name: 'MEDIUM', label: 'Mittel', letter: 'M', weights: [0, 0, 30, 40, 22, 8], givens: 0, rating: [3, 3],
    description: 'Braucht die 45er-Regel und Käfig-Kombinationen' },
  { name: 'HARD', label: 'Schwer', letter: 'S', weights: [0, 0, 15, 30, 30, 15, 10], givens: 0, rating: [3, 5],
    description: 'Größere Käfige, manchmal Ausprobieren nötig' },
  { name: 'EXPERT', label: 'Experte', letter: 'E', weights: [0, 0, 8, 20, 25, 22, 15, 10], givens: 0, rating: [5, 5],
    description: 'Große Käfige, nur mit Ausprobieren lösbar' },
];

export const difficultyByName = (name) => DIFFICULTIES.find((d) => d.name === name);

export const maxCageSize = (difficulty) => difficulty.weights.length - 1;

export class Puzzle {
  /**
   * @param difficulty Eintrag aus DIFFICULTIES
   * @param solution 81 Ziffern
   * @param cages [{sum, cells}]
   * @param givens 81 Ziffern, 0 = leer
   * @param code z. B. „M-123456“
   */
  constructor(difficulty, solution, cages, givens, code = '') {
    this.difficulty = difficulty;
    this.solution = Array.from(solution);
    this.cages = cages;
    this.givens = Array.from(givens);
    this.code = code;
    this.cageOf = new Array(81).fill(0);
    cages.forEach((cage, index) => cage.cells.forEach((c) => { this.cageOf[c] = index; }));
    this.labelCellOfCage = cages.map((cage) => Math.min(...cage.cells));
  }

  serialize() {
    return [
      this.difficulty.name,
      this.solution.join(''),
      this.givens.join(''),
      this.cages.map((c) => `${c.sum}:${c.cells.join('.')}`).join(';'),
      this.code,
    ].join('/');
  }

  static deserialize(text) {
    const parts = text.split('/');
    if (parts.length !== 5) throw new Error('ungültiges Rätsel');
    const difficulty = difficultyByName(parts[0]);
    if (!difficulty) throw new Error('unbekannter Schwierigkeitsgrad');
    return new Puzzle(difficulty, parseDigits(parts[1]), parseCages(parts[3]), parseDigits(parts[2]), parts[4]);
  }
}

export function parseDigits(text) {
  if (!/^[0-9]{81}$/.test(text)) throw new Error('ungültige Ziffern');
  return Array.from(text, Number);
}

export function parseCages(text) {
  const cages = text.split(';').map((cage) => {
    const [sum, cells] = cage.split(':');
    return { sum: Number(sum), cells: cells.split('.').map(Number) };
  });
  if (cages.reduce((acc, c) => acc + c.cells.length, 0) !== 81) throw new Error('ungültige Käfige');
  return cages;
}
