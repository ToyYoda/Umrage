// Oberfläche der Web-Version: Zustand, Spielfeld, Bedienung per Maus, Touch und Tastatur.
import { cageCombinations } from './game/cagehelp.js';
import { daily, epochDay, formatCode, parseCode, SEEDS } from './game/code.js';
import { assignCageColors } from './game/colors.js';
import { box, cellName, col, row, units } from './game/grid.js';
import { comboText } from './game/logic.js';
import { DIFFICULTIES, difficultyByName, Puzzle } from './game/puzzle.js';
import { GameState } from './game/state.js';
import { Stats } from './game/stats.js';

// ---------------------------------------------------------------- Speicher

const store = {
  get(key) {
    try { return localStorage.getItem(`killer-sudoku.${key}`); } catch { return null; }
  },
  set(key, value) {
    try {
      if (value === null) localStorage.removeItem(`killer-sudoku.${key}`);
      else localStorage.setItem(`killer-sudoku.${key}`, value);
    } catch { /* privater Modus o. Ä.: dann eben ohne Speichern */ }
  },
};

function loadSettings() {
  try {
    return { strict: false, coloredCages: false, animations: true, ...JSON.parse(store.get('settings') ?? '{}') };
  } catch {
    return { strict: false, coloredCages: false, animations: true };
  }
}

// ---------------------------------------------------------------- Zustand

const ui = {
  screen: 'home',
  settings: loadSettings(),
  game: null,
  stats: Stats.deserialize(store.get('stats')),
  selected: -1,
  notesMode: false,
  history: [],
  generating: null,
  hint: null,
  hintLoading: false,
  flash: null,
  pendingStart: null,
  cageColors: null,
};

const saved = store.get('game');
if (saved) ui.game = GameState.deserialize(saved)?.copy({ strict: ui.settings.strict }) ?? null;

function save() {
  store.set('game', ui.game ? ui.game.serialize() : null);
  store.set('stats', ui.stats.serialize());
}

const today = () => epochDay(new Date());

// ---------------------------------------------------------------- Rechenarbeit im Hintergrund

function createWorker() {
  const worker = new Worker(new URL('./worker.js', import.meta.url), { type: 'module' });
  const pending = new Map();
  let nextId = 1;
  worker.onmessage = ({ data }) => {
    const entry = pending.get(data.id);
    pending.delete(data.id);
    if (data.error) entry.reject(new Error(data.error));
    else entry.resolve(data);
  };
  return (message) => new Promise((resolve, reject) => {
    const id = nextId++;
    pending.set(id, { resolve, reject });
    worker.postMessage({ ...message, id });
  });
}

// Ein Worker für Anfragen des Spielers, einer für das Vorab-Erzeugen – so wartet niemand auf den anderen.
const work = createWorker();
const prefetchWork = createWorker();

async function generatePuzzle(difficulty, seed, worker = work) {
  const { puzzle } = await worker({ type: 'generate', difficulty: difficulty.name, seed });
  return Puzzle.deserialize(puzzle);
}

function takePrefetched(difficulty) {
  const text = store.get(`prefetch.${difficulty.name}`);
  if (!text) return null;
  store.set(`prefetch.${difficulty.name}`, null);
  try { return Puzzle.deserialize(text); } catch { return null; }
}

function cachedDaily(day) {
  const text = store.get('daily');
  if (!text) return null;
  const [storedDay, puzzle] = text.split('#');
  if (Number(storedDay) !== day) return null;
  try { return Puzzle.deserialize(puzzle); } catch { return null; }
}

let prefetching = false;

/** Erzeugt das heutige tägliche Rätsel und je Schwierigkeitsgrad eines vorab. */
async function prefetch() {
  if (prefetching) return;
  prefetching = true;
  try {
    const day = today();
    if (!cachedDaily(day)) {
      const [difficulty, seed] = daily(day);
      const puzzle = await generatePuzzle(difficulty, seed, prefetchWork);
      store.set('daily', `${day}#${puzzle.serialize()}`);
    }
    for (;;) {
      const missing = DIFFICULTIES.find((d) => !store.get(`prefetch.${d.name}`));
      if (!missing) break;
      const puzzle = await generatePuzzle(missing, Math.floor(Math.random() * SEEDS), prefetchWork);
      store.set(`prefetch.${missing.name}`, puzzle.serialize());
    }
  } catch (error) {
    console.warn('Vorab-Erzeugung fehlgeschlagen', error);
  } finally {
    prefetching = false;
  }
}

// ---------------------------------------------------------------- Spiel starten

function inProgress() {
  const game = ui.game;
  return game && !game.isSolved &&
    game.values.some((v, i) => v !== game.puzzle.givens[i] || game.notes[i] !== 0);
}

function confirmThen(action) {
  if (inProgress()) {
    ui.pendingStart = action;
    document.getElementById('confirm-dialog').showModal();
  } else {
    action();
  }
}

async function start(label, create, dailyDay = -1) {
  if (ui.generating) return;
  ui.generating = label;
  render();
  try {
    const puzzle = await create();
    ui.history = [];
    ui.selected = -1;
    ui.notesMode = false;
    ui.hint = null;
    ui.game = GameState.create(puzzle, dailyDay, ui.settings.strict);
    ui.cageColors = null;
    ui.stats = ui.stats.withStarted(puzzle.difficulty);
    save();
    ui.screen = 'game';
  } catch (error) {
    showToast('Das Rätsel konnte nicht erzeugt werden.');
    console.error(error);
  } finally {
    ui.generating = null;
    render();
    prefetch();
  }
}

function newGame(difficulty) {
  confirmThen(() => start(`Rätsel (${difficulty.label})`, async () =>
    takePrefetched(difficulty) ?? generatePuzzle(difficulty, Math.floor(Math.random() * SEEDS))));
}

function startDaily() {
  const day = today();
  if (ui.game && ui.game.dailyDay === day && !ui.game.isSolved) {
    showScreen('game');
    return;
  }
  const [difficulty, seed] = daily(day);
  confirmThen(() => start(`Tägliches Rätsel (${difficulty.label})`,
    async () => cachedDaily(day) ?? generatePuzzle(difficulty, seed), day));
}

/** @returns false bei ungültigem Code */
function startCode(text) {
  const parsed = parseCode(text);
  if (!parsed) return false;
  const [difficulty, seed] = parsed;
  if (ui.game && ui.game.puzzle.code === formatCode(difficulty, seed) && !ui.game.isSolved) {
    showScreen('game');
    return true;
  }
  confirmThen(() => start(`Rätsel ${formatCode(difficulty, seed)}`, () => generatePuzzle(difficulty, seed)));
  return true;
}

// ---------------------------------------------------------------- Spielzüge

function update(next) {
  const current = ui.game;
  if (!current || next === current) return;
  ui.history.push(current);
  if (ui.history.length > 500) ui.history.shift();
  ui.game = next;
  onChanged(current, next);
  save();
  renderGame();
}

function input(digit, asNote = ui.notesMode) {
  const game = ui.game;
  if (!game || ui.selected < 0 || game.isSolved) return;
  ui.hint = null;
  update(asNote ? game.toggleNote(ui.selected, digit) : game.enterDigit(ui.selected, digit));
}

function erase() {
  if (!ui.game || ui.selected < 0) return;
  ui.hint = null;
  update(ui.game.erase(ui.selected));
}

function undo() {
  const current = ui.game;
  const previous = ui.history.pop();
  if (!current || !previous) return;
  ui.hint = null;
  // Fehler, Tipps und Zeit werden durch Rückgängig nicht zurückgesetzt.
  ui.game = previous.copy({
    mistakes: current.mistakes, hintsUsed: current.hintsUsed, elapsedSeconds: current.elapsedSeconds, strict: current.strict,
  });
  save();
  renderGame();
}

function select(cell) {
  ui.selected = cell;
  renderGame();
}

function onChanged(before, after) {
  if (after.isSolved && !before.isSolved) {
    ui.stats = ui.stats.withRecord({
      difficulty: after.puzzle.difficulty.name,
      seconds: after.elapsedSeconds,
      mistakes: after.mistakes,
      hints: after.hintsUsed,
      day: today(),
      dailyDay: after.dailyDay,
    });
    if (ui.settings.animations) {
      startFlash(Array.from({ length: 81 }, (_, i) => i).sort((a, b) => row(a) + col(a) - row(b) - col(b)));
    }
    return;
  }
  const cell = ui.selected;
  if (!ui.settings.animations || after.strict || cell < 0) return;
  if (after.values[cell] === before.values[cell] || after.isWrong(cell)) return;
  const done = after.completedUnits(cell).filter((u) => !before.completedUnits(cell).includes(u));
  if (done.length === 0) return;
  const cells = [...new Set(done.flatMap((u) => units[u]))]
    .sort((a, b) => distance(a, cell) - distance(b, cell));
  startFlash(cells);
}

const distance = (a, b) => Math.max(Math.abs(row(a) - row(b)), Math.abs(col(a) - col(b)));

// ---------------------------------------------------------------- Tipps

async function requestHint() {
  const game = ui.game;
  if (!game || game.isSolved || ui.hintLoading) return;
  const wrong = game.firstWrongCell();
  if (wrong >= 0) {
    ui.selected = wrong;
    showHint({
      title: 'Falsche Ziffer',
      text: `Die ${game.values[wrong]} in ${cellName(wrong)} passt nicht zur Lösung. Entferne sie zuerst – ` +
        'darauf aufbauende Schlüsse wären sonst falsch.',
      reasons: [], action: 'Entfernen', cell: wrong, focus: [], kind: 'remove',
    });
    return;
  }
  ui.hintLoading = true;
  renderGame();
  try {
    const { hint } = await work({ type: 'hint', puzzle: game.puzzle.serialize(), known: game.knownCorrect() });
    ui.hintLoading = false;
    // Der Timer ersetzt den Zustand jede Sekunde; verworfen wird der Tipp nur, wenn sich Ziffern geändert haben.
    if (!ui.game || ui.game.values.join('') !== game.values.join('')) {
      renderGame();
      return;
    }
    if (hint) {
      ui.selected = hint.step.cell;
      showHint({
        title: hint.step.title,
        text: hint.step.text,
        reasons: hint.reasons.map((r) => `${r.title}: ${r.text}`),
        action: `Die ${hint.step.digit} setzen`,
        cell: hint.step.cell,
        focus: hint.step.focus,
        kind: 'place',
      });
    } else {
      const empty = [];
      for (let c = 0; c < 81; c++) if (ui.game.values[c] === 0) empty.push(c);
      const cell = ui.selected >= 0 && ui.game.values[ui.selected] === 0
        ? ui.selected : empty[Math.floor(Math.random() * empty.length)];
      ui.selected = cell;
      showHint({
        title: 'Kein einfacher Schritt',
        text: 'Hier kommen die eingebauten Techniken nicht weiter – es wären längere Gedankenketten nötig. ' +
          `Soll ich ${cellName(cell)} aufdecken?`,
        reasons: [], action: 'Aufdecken', cell, focus: [], kind: 'reveal',
      });
    }
  } catch (error) {
    ui.hintLoading = false;
    showToast('Der Tipp konnte nicht berechnet werden.');
    console.error(error);
    renderGame();
  }
}

function showHint(hint) {
  ui.hint = hint;
  // Schon das Anzeigen zählt als Tipp.
  ui.game = ui.game.copy({ hintsUsed: ui.game.hintsUsed + 1 });
  save();
  renderGame();
}

function applyHint() {
  const hint = ui.hint;
  const game = ui.game;
  if (!hint || !game) return;
  ui.hint = null;
  ui.selected = hint.cell;
  if (hint.kind === 'remove') update(game.erase(hint.cell));
  else update(game.reveal(hint.cell).copy({ hintsUsed: game.hintsUsed }));
}

// ---------------------------------------------------------------- Teilen

function shareText() {
  const game = ui.game;
  if (!game || !game.puzzle.code) return null;
  return `Löse dieses Killer-Sudoku${game.isDaily ? ' (das tägliche Rätsel)' : ''}: Code ${game.puzzle.code}`;
}

async function share(prefix = '') {
  const text = shareText();
  if (!text) return;
  const url = `${location.origin}${location.pathname}#${ui.game.puzzle.code}`;
  const message = `${prefix}${text}`;
  try {
    if (navigator.share) {
      await navigator.share({ title: 'Killer Sudoku', text: message, url });
      return;
    }
  } catch (error) {
    if (error.name === 'AbortError') return;
  }
  try {
    await navigator.clipboard.writeText(`${message} – ${url}`);
    showToast('Link kopiert');
  } catch {
    showToast(url);
  }
}

let toastTimer;
function showToast(text) {
  const toast = document.getElementById('toast');
  toast.textContent = text;
  toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toast.hidden = true; }, 2500);
}

// ---------------------------------------------------------------- Darstellung

const $ = (id) => document.getElementById(id);
const escapeHtml = (text) => String(text).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

function formatTime(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  const pad = (n) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}

function showScreen(screen) {
  ui.screen = screen;
  if (screen !== 'game') ui.hint = null;
  save();
  render();
}

function render() {
  const screen = ui.screen === 'game' && !ui.game ? 'home' : ui.screen;
  for (const id of ['home', 'game', 'stats', 'settings']) $(id).hidden = id !== screen;
  if (screen === 'home') renderHome();
  else if (screen === 'game') renderGame();
  else if (screen === 'stats') renderStats();
  else renderSettings();
}

function renderHome() {
  const content = $('home-content');
  if (ui.generating) {
    content.innerHTML = `<div class="home-column"><div class="spinner"></div>
      <p class="help">${escapeHtml(ui.generating)} wird erzeugt …</p></div>`;
    return;
  }
  const day = today();
  const [difficulty, seed] = daily(day);
  const record = ui.stats.dailyRecord(day);
  const current = ui.game && ui.game.dailyDay === day && !ui.game.isSolved ? ui.game : null;
  const streak = ui.stats.dailyStreak(day);
  const date = new Date().toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long' });
  const status = record ? `Gelöst in ${formatTime(record.seconds)} ✓`
    : current ? `Angefangen · ${formatTime(current.elapsedSeconds)}` : 'Noch offen';
  const game = ui.game;
  content.innerHTML = `
    <div class="home-column">
      <div class="card daily">
        <h3>Tägliches Rätsel</h3>
        <p class="small">${escapeHtml(date)} · ${difficulty.label} · Code ${formatCode(difficulty, seed)}</p>
        <p>${escapeHtml(status)}</p>
        ${streak > 0 ? `<p class="small">Serie: ${streak} ${streak === 1 ? 'Tag' : 'Tage'} in Folge</p>` : ''}
        <button class="filled-button" data-action="daily">${current ? 'Fortsetzen' : record ? 'Nochmal spielen' : 'Spielen'}</button>
      </div>
      ${game && !game.isSolved ? `<button class="filled-button wide" data-action="continue">
        Fortsetzen · ${game.puzzle.difficulty.label} · ${formatTime(game.elapsedSeconds)}</button>` : ''}
      <h3 style="text-align:center">Neues Spiel</h3>
      ${DIFFICULTIES.map((d) => `<button class="difficulty-button" data-action="new" data-difficulty="${d.name}">
        <strong>${d.label}</strong><span>${escapeHtml(d.description)}</span></button>`).join('')}
      <button class="text-button" data-action="enter-code">Rätsel-Code eingeben</button>
    </div>`;
}

function renderGame() {
  const game = ui.game;
  if (!game || ui.screen !== 'game') return;
  $('game-title').textContent = game.isDaily ? `Tägliches Rätsel · ${game.puzzle.difficulty.label}` : game.puzzle.difficulty.label;
  $('game-code').textContent = game.puzzle.code ? `Code ${game.puzzle.code}` : '';
  $('share-button').hidden = !game.puzzle.code;
  const mistakes = $('mistakes');
  mistakes.hidden = game.strict;
  mistakes.textContent = `Fehler: ${game.mistakes}`;
  mistakes.classList.toggle('has-mistakes', game.mistakes > 0);
  $('timer').textContent = formatTime(game.elapsedSeconds);

  $('controls').hidden = game.isSolved;
  $('solved').hidden = !game.isSolved;
  if (game.isSolved) renderSolved();
  $('undo-button').disabled = ui.history.length === 0;
  $('notes-button').classList.toggle('active', ui.notesMode);
  $('notes-label').textContent = ui.notesMode ? 'Notizen: an' : 'Notizen: aus';
  $('hint-button').disabled = ui.hintLoading;
  renderNumpad();
  renderInfo();
  drawBoard();
}

function renderNumpad() {
  const pad = $('numpad');
  if (pad.children.length === 0) {
    for (let d = 1; d <= 9; d++) {
      const key = document.createElement('button');
      key.className = 'key';
      key.dataset.digit = d;
      key.innerHTML = `<span class="digit">${d}</span><span class="count"></span>`;
      pad.append(key);
    }
  }
  pad.classList.toggle('notes', ui.notesMode);
  for (const key of pad.children) {
    const d = Number(key.dataset.digit);
    const remaining = ui.game.remaining(d);
    key.disabled = remaining <= 0;
    key.querySelector('.count').textContent = remaining <= 0 ? '✓' : remaining;
    key.setAttribute('aria-label', `Ziffer ${d}, noch ${Math.max(remaining, 0)}`);
  }
}

const BULB = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 21c0 .5.4 1 1 1h4c.6 0 1-.5 1-1v-1H9zm3-19a7 7 0 0 0-4 12.7V17c0 .5.4 1 1 1h6c.6 0 1-.5 1-1v-2.3A7 7 0 0 0 12 2z"/></svg>';

function renderInfo() {
  const info = $('info');
  const game = ui.game;
  if (game.isSolved) {
    info.innerHTML = '';
  } else if (ui.hintLoading) {
    info.innerHTML = '<div class="loading"><div class="spinner small"></div>Suche den nächsten Schritt …</div>';
  } else if (ui.hint) {
    const hint = ui.hint;
    info.innerHTML = `<div class="hint-card">
      <h4>${BULB}${escapeHtml(hint.title)}</h4>
      ${hint.reasons.length ? `<div class="reasons"><strong>Vorher:</strong><ul>${hint.reasons.map((r) =>
        `<li>${escapeHtml(r)}</li>`).join('')}</ul><strong>Daraus folgt:</strong></div>` : ''}
      <p>${escapeHtml(hint.text)}</p>
      <div class="buttons">
        <button class="text-button" data-action="dismiss-hint">Schließen</button>
        <button class="filled-button" data-action="apply-hint">${escapeHtml(hint.action)}</button>
      </div></div>`;
  } else if (ui.selected >= 0) {
    const cageIndex = game.puzzle.cageOf[ui.selected];
    const cage = game.puzzle.cages[cageIndex];
    const combos = cageCombinations(game, cageIndex);
    const possible = combos.filter((c) => c.possible).length;
    info.innerHTML = `<div class="combos-title">Käfig ${cage.sum} in ${cage.cells.length} Feldern · ${possible} von
      ${combos.length} Kombinationen möglich</div>
      <div class="combos">${combos.map((c) => `<span class="combo${c.possible ? '' : ' impossible'}">${comboText(c.digits)}</span>`).join('')}</div>`;
  } else {
    info.innerHTML = '<p class="hint-placeholder">Feld antippen – hier erscheinen die möglichen Zahlenkombinationen seines Käfigs.</p>';
  }
}

function renderSolved() {
  const game = ui.game;
  const best = ui.stats.of(game.puzzle.difficulty).bestSeconds;
  const newBest = game.hintsUsed === 0 && best === game.elapsedSeconds;
  $('solved').innerHTML = `
    <h3>Geschafft! 🎉</h3>
    ${newBest ? '<div class="best">Neue Bestzeit!</div>' : ''}
    <div>Zeit ${formatTime(game.elapsedSeconds)}${game.strict ? '' : ` · Fehler ${game.mistakes}`} · Tipps ${game.hintsUsed}</div>
    <div class="buttons">
      ${game.puzzle.code ? '<button class="outlined-button" data-action="share-solved">Teilen</button>' : ''}
      <button class="filled-button" data-action="show-home">Neues Spiel</button>
    </div>`;
}

function renderStats() {
  const day = today();
  const stats = ui.stats;
  const item = (label, value) => `<div><div class="stat-value">${value}</div><div class="stat-label">${label}</div></div>`;
  $('stats-content').innerHTML = `<div class="stats-column">
    <div class="card"><h3>Tägliches Rätsel</h3><div class="stat-grid">
      ${item('Serie (Tage)', stats.dailyStreak(day))}${item('Gelöst', stats.dailySolved)}
      ${item('Heute', stats.dailyRecord(day) ? '✓' : '–')}</div></div>
    ${DIFFICULTIES.map((d) => {
      const s = stats.of(d);
      return `<div class="card"><h3>${d.label}</h3><div class="stat-grid">
        ${item('Gespielt', s.started)}${item('Gelöst', s.solved)}${item('Ohne Fehler', s.flawless)}
        ${item('Bestzeit', s.bestSeconds === null ? '–' : formatTime(s.bestSeconds))}
        ${item('Ø Zeit', s.averageSeconds === null ? '–' : formatTime(s.averageSeconds))}
        ${item('Serie ohne Fehler', `${s.currentFlawlessStreak} (beste ${s.bestFlawlessStreak})`)}</div>
        ${s.recentSeconds.length >= 2 ? timesChart(s.recentSeconds) : ''}</div>`;
    }).join('')}
    <p class="help">Bestzeiten zählen nur Spiele ohne Tipps. „Ohne Fehler“ heißt: keine falsche Ziffer und kein Tipp.</p>
  </div>`;
}

/** Säulen der letzten Lösezeiten (älteste links), mit Nulllinie und Tooltip je Säule. */
function timesChart(seconds) {
  const width = 400;
  const height = 72;
  const max = Math.max(...seconds, 1);
  const slot = width / seconds.length;
  const barWidth = Math.min(slot - 2, 24);
  const radius = Math.min(4, barWidth / 2);
  const bars = seconds.map((value, i) => {
    const h = Math.max((value / max) * height, radius);
    const x = i * slot + (slot - barWidth) / 2;
    const y = height - h;
    // Oben abgerundet, unten gerade auf der Nulllinie.
    const path = `M${x},${height}V${y + radius}Q${x},${y} ${x + radius},${y}H${x + barWidth - radius}` +
      `Q${x + barWidth},${y} ${x + barWidth},${y + radius}V${height}Z`;
    return `<path d="${path}" fill="var(--primary)"><title>Spiel ${i + 1}: ${formatTime(value)}</title></path>`;
  }).join('');
  return `<div class="chart-caption" style="margin-top:10px">Lösezeiten der letzten ${seconds.length} Spiele</div>
    <svg class="chart" viewBox="0 0 ${width} ${height + 1}" preserveAspectRatio="none" role="img"
      aria-label="Lösezeiten: ${seconds.map(formatTime).join(', ')}">${bars}
      <line x1="0" x2="${width}" y1="${height + 0.5}" y2="${height + 0.5}" stroke-width="1"/></svg>
    <div class="chart-caption">Schnellste ${formatTime(Math.min(...seconds))} · Langsamste ${formatTime(Math.max(...seconds))}
      · Letzte ${formatTime(seconds[seconds.length - 1])}</div>`;
}

const SETTINGS = [
  ['strict', 'Strenge Rückmeldung', 'Nicht verraten, ob eine Ziffer richtig ist. Markiert werden nur Regelverstöße: ' +
    'doppelte Ziffern und falsche Käfigsummen. Fehler werden nicht gezählt, richtige Ziffern bleiben änderbar.'],
  ['coloredCages', 'Farbige Käfige', 'Käfige mit Farben statt mit gestrichelten Linien darstellen.'],
  ['animations', 'Animationen', 'Fertige Zeilen, Spalten und Blöcke kurz aufleuchten lassen.'],
];

function renderSettings() {
  $('settings-content').innerHTML = `<div class="settings-column">${SETTINGS.map(([key, title, text]) => `
    <label class="setting"><div><strong>${title}</strong><span>${text}</span></div>
      <input type="checkbox" class="switch" data-setting="${key}" ${ui.settings[key] ? 'checked' : ''}></label>`).join('')}
    </div>`;
}

function updateSetting(key, value) {
  ui.settings = { ...ui.settings, [key]: value };
  store.set('settings', JSON.stringify(ui.settings));
  if (ui.game) ui.game = ui.game.copy({ strict: ui.settings.strict });
  ui.cageColors = null;
}

// ---------------------------------------------------------------- Spielfeld zeichnen

const canvas = $('board');
const ctx = canvas.getContext('2d');

function cssColor(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

function rgba(hex, alpha) {
  const value = hex.replace('#', '');
  const n = parseInt(value.length === 3 ? value.replace(/./g, '$&$&') : value, 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`;
}

const LIGHT_CAGE_COLORS = ['#ffe3c2', '#d3edd0', '#d2e4fa', '#f8d5e4', '#f6edb5', '#e2d9f3'];
const DARK_CAGE_COLORS = ['#4a3923', '#2b4430', '#263b54', '#4b2c3b', '#45421f', '#372f4d'];
/** Dunkles Theme erkennen – an der tatsächlichen Hintergrundfarbe, damit auch eine ausdrückliche Wahl zählt. */
function dark() {
  const n = parseInt(cssColor('--surface').replace('#', ''), 16);
  return ((n >> 16) & 255) * 0.299 + ((n >> 8) & 255) * 0.587 + (n & 255) * 0.114 < 128;
}

function resizeCanvas() {
  const size = Math.round(canvas.clientWidth * devicePixelRatio);
  if (size > 0 && canvas.width !== size) {
    canvas.width = size;
    canvas.height = size;
  }
}

function drawBoard() {
  const game = ui.game;
  if (!game) return;
  resizeCanvas();
  const size = canvas.width;
  if (size === 0) return;
  const cs = size / 9;
  const px = devicePixelRatio;
  const puzzle = game.puzzle;
  const colors = {
    surface: cssColor('--surface'), onSurface: cssColor('--on-surface'), onSurfaceVariant: cssColor('--on-surface-variant'),
    outlineVariant: cssColor('--outline-variant'), primary: cssColor('--primary'), error: cssColor('--error'),
    tertiary: cssColor('--tertiary'),
  };
  if (ui.settings.coloredCages && !ui.cageColors) {
    const palette = dark() ? DARK_CAGE_COLORS : LIGHT_CAGE_COLORS;
    ui.cageColors = assignCageColors(puzzle).map((c) => palette[c]);
  }
  const cageColors = ui.settings.coloredCages ? ui.cageColors : null;
  const selected = ui.selected;
  const selectedDigit = selected >= 0 ? game.values[selected] : 0;
  const hint = ui.hint;
  const focus = new Set(hint ? hint.focus : []);
  const isPeer = (a, b) => row(a) === row(b) || col(a) === col(b) || box(a) === box(b);

  // Hintergrund eines Feldes (oder eines Ausschnitts davon): Käfigfarbe plus Markierung.
  const tintOf = (cell) => {
    if (cell === selected) return rgba(colors.primary, 0.35);
    if (game.isError(cell)) return rgba(colors.error, 0.25);
    if (focus.has(cell)) return rgba(colors.tertiary, 0.22);
    if (selectedDigit !== 0 && game.values[cell] === selectedDigit) return rgba(colors.primary, 0.22);
    if (selected >= 0 && isPeer(cell, selected)) return rgba(colors.primary, 0.08);
    return null;
  };
  const fillBackground = (cell, x, y, w, h) => {
    ctx.fillStyle = cageColors ? cageColors[puzzle.cageOf[cell]] : colors.surface;
    ctx.fillRect(x, y, w, h);
    const tint = tintOf(cell);
    if (tint) {
      ctx.fillStyle = tint;
      ctx.fillRect(x, y, w, h);
    }
  };

  ctx.clearRect(0, 0, size, size);
  for (let cell = 0; cell < 81; cell++) fillBackground(cell, col(cell) * cs, row(cell) * cs, cs, cs);

  // Aufleuchten fertiger Einheiten: eine Welle über die Felder.
  if (ui.flash) {
    const progress = Math.min((performance.now() - ui.flash.start) / 900, 1);
    const spread = 0.6;
    const n = ui.flash.cells.length;
    ui.flash.cells.forEach((cell, i) => {
      const t = Math.min(Math.max((progress - (spread * i) / n) / (1 - spread), 0), 1);
      const alpha = Math.sin(Math.PI * t) * 0.45;
      if (alpha > 0.01) {
        ctx.fillStyle = rgba(colors.primary, alpha);
        ctx.fillRect(col(cell) * cs, row(cell) * cs, cs, cs);
      }
    });
  }

  // Dünne Gitterlinien.
  ctx.strokeStyle = colors.outlineVariant;
  ctx.lineWidth = px;
  ctx.setLineDash([]);
  ctx.beginPath();
  for (let i = 1; i < 9; i++) {
    if (i % 3 === 0) continue;
    ctx.moveTo(i * cs, 0);
    ctx.lineTo(i * cs, size);
    ctx.moveTo(0, i * cs);
    ctx.lineTo(size, i * cs);
  }
  ctx.stroke();

  const inset = cs * 0.09;
  if (!cageColors) drawCages(puzzle, cs, inset, colors.onSurfaceVariant, px);

  // Käfigsummen oben links, mit Hintergrund, damit die gestrichelte Linie unterbrochen wird.
  const sumSize = cs * 0.21;
  ctx.font = `600 ${sumSize}px system-ui, sans-serif`;
  ctx.textBaseline = 'top';
  ctx.textAlign = 'left';
  puzzle.cages.forEach((cage, index) => {
    const cell = puzzle.labelCellOfCage[index];
    const x = col(cell) * cs + inset * 0.5;
    const y = row(cell) * cs + inset * 0.3;
    const text = String(cage.sum);
    const width = ctx.measureText(text).width;
    fillBackground(cell, x, y, width + inset * 0.4, sumSize * 1.15);
    ctx.fillStyle = colors.onSurface;
    ctx.fillText(text, x, y + sumSize * 0.08);
  });

  // Dicke Linien für Blöcke und Rand.
  ctx.strokeStyle = colors.onSurface;
  ctx.lineWidth = 2.5 * px;
  ctx.beginPath();
  for (let i = 0; i <= 9; i += 3) {
    const p = Math.min(Math.max(i * cs, ctx.lineWidth / 2), size - ctx.lineWidth / 2);
    ctx.moveTo(p, 0);
    ctx.lineTo(p, size);
    ctx.moveTo(0, p);
    ctx.lineTo(size, p);
  }
  ctx.stroke();

  // Ziffern und Notizen.
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  for (let cell = 0; cell < 81; cell++) {
    const x0 = col(cell) * cs;
    const y0 = row(cell) * cs;
    const value = game.values[cell];
    if (value !== 0) {
      ctx.font = `${game.isGiven(cell) ? 700 : 400} ${cs * 0.58}px system-ui, sans-serif`;
      ctx.fillStyle = game.isGiven(cell) ? colors.onSurface : game.isError(cell) ? colors.error : colors.primary;
      ctx.fillText(String(value), x0 + cs / 2, y0 + cs / 2 + cs * 0.03);
    } else if (game.notes[cell] !== 0) {
      // 3×3-Raster unterhalb der Käfigsumme.
      const left = cs * 0.12;
      const top = cs * 0.22;
      const w = (cs * 0.82) / 3;
      const h = (cs * 0.68) / 3;
      ctx.font = `${cs * 0.2}px system-ui, sans-serif`;
      for (let d = 1; d <= 9; d++) {
        if (!game.hasNote(cell, d)) continue;
        const cx = x0 + left + (((d - 1) % 3) + 0.5) * w;
        const cy = y0 + top + (Math.floor((d - 1) / 3) + 0.5) * h;
        if (d === selectedDigit) {
          ctx.fillStyle = rgba(colors.primary, 0.3);
          ctx.beginPath();
          ctx.arc(cx, cy, Math.min(w, h) * 0.5, 0, Math.PI * 2);
          ctx.fill();
        }
        ctx.fillStyle = colors.onSurfaceVariant;
        ctx.fillText(String(d), cx, cy + cs * 0.01);
      }
    }
  }

  // Zielfeld eines Tipps.
  if (hint && hint.cell >= 0) {
    const stroke = 3 * px;
    ctx.strokeStyle = colors.tertiary;
    ctx.lineWidth = stroke;
    ctx.strokeRect(col(hint.cell) * cs + stroke / 2, row(hint.cell) * cs + stroke / 2, cs - stroke, cs - stroke);
  }
}

/** Käfigumrisse gestrichelt und leicht nach innen versetzt. */
function drawCages(puzzle, cs, d, color, px) {
  const { cageOf } = puzzle;
  ctx.strokeStyle = color;
  ctx.lineWidth = 1.2 * px;
  ctx.setLineDash([cs * 0.08, cs * 0.06]);
  ctx.beginPath();
  const line = (x0, y0, x1, y1) => {
    ctx.moveTo(x0, y0);
    ctx.lineTo(x1, y1);
  };
  for (let cell = 0; cell < 81; cell++) {
    const r = row(cell);
    const c = col(cell);
    const same = (dr, dc) => {
      const nr = r + dr;
      const nc = c + dc;
      return nr >= 0 && nr <= 8 && nc >= 0 && nc <= 8 && cageOf[nr * 9 + nc] === cageOf[cell];
    };
    const x0 = c * cs;
    const y0 = r * cs;
    const x1 = x0 + cs;
    const y1 = y0 + cs;
    const up = same(-1, 0);
    const down = same(1, 0);
    const left = same(0, -1);
    const right = same(0, 1);
    if (!up) line(left ? x0 : x0 + d, y0 + d, right ? x1 : x1 - d, y0 + d);
    if (!down) line(left ? x0 : x0 + d, y1 - d, right ? x1 : x1 - d, y1 - d);
    if (!left) line(x0 + d, up ? y0 : y0 + d, x0 + d, down ? y1 : y1 - d);
    if (!right) line(x1 - d, up ? y0 : y0 + d, x1 - d, down ? y1 : y1 - d);
    // Innere Ecken, an denen zwei Nachbarn zum Käfig gehören, das diagonale Feld aber nicht.
    if (up && left && !same(-1, -1)) { line(x0, y0 + d, x0 + d, y0 + d); line(x0 + d, y0, x0 + d, y0 + d); }
    if (up && right && !same(-1, 1)) { line(x1 - d, y0 + d, x1, y0 + d); line(x1 - d, y0, x1 - d, y0 + d); }
    if (down && left && !same(1, -1)) { line(x0, y1 - d, x0 + d, y1 - d); line(x0 + d, y1 - d, x0 + d, y1); }
    if (down && right && !same(1, 1)) { line(x1 - d, y1 - d, x1, y1 - d); line(x1 - d, y1 - d, x1 - d, y1); }
  }
  ctx.stroke();
  ctx.setLineDash([]);
}

function startFlash(cells) {
  ui.flash = { cells, start: performance.now() };
  const step = () => {
    if (!ui.flash) return;
    drawBoard();
    if (performance.now() - ui.flash.start < 900) requestAnimationFrame(step);
    else {
      ui.flash = null;
      drawBoard();
    }
  };
  requestAnimationFrame(step);
}

// ---------------------------------------------------------------- Eingaben

canvas.addEventListener('pointerdown', (event) => {
  const rect = canvas.getBoundingClientRect();
  const c = Math.min(8, Math.max(0, Math.floor(((event.clientX - rect.left) / rect.width) * 9)));
  const r = Math.min(8, Math.max(0, Math.floor(((event.clientY - rect.top) / rect.height) * 9)));
  select(r * 9 + c);
});

// Ziffernfeld: Tippen setzt die Ziffer, langer Druck (oder Umschalttaste/Rechtsklick) eine Notiz.
let pressTimer = null;
let longPressed = false;
const numpad = $('numpad');
numpad.addEventListener('pointerdown', (event) => {
  const key = event.target.closest('.key');
  if (!key || key.disabled || event.button === 2) return;
  longPressed = false;
  clearTimeout(pressTimer);
  pressTimer = setTimeout(() => {
    longPressed = true;
    navigator.vibrate?.(20);
    input(Number(key.dataset.digit), !ui.notesMode);
  }, 450);
});
for (const type of ['pointerup', 'pointerleave', 'pointercancel']) {
  numpad.addEventListener(type, () => clearTimeout(pressTimer));
}
numpad.addEventListener('click', (event) => {
  const key = event.target.closest('.key');
  if (!key || key.disabled) return;
  if (longPressed) {
    longPressed = false;
    return;
  }
  input(Number(key.dataset.digit), event.shiftKey ? !ui.notesMode : ui.notesMode);
});
numpad.addEventListener('contextmenu', (event) => {
  const key = event.target.closest('.key');
  if (!key) return;
  event.preventDefault();
  if (!key.disabled && !longPressed) input(Number(key.dataset.digit), !ui.notesMode);
});

const actions = {
  'show-home': () => showScreen('home'),
  'show-stats': () => showScreen('stats'),
  'show-settings': () => showScreen('settings'),
  continue: () => showScreen('game'),
  daily: startDaily,
  new: (el) => newGame(difficultyByName(el.dataset.difficulty)),
  'enter-code': () => {
    $('code-input').value = '';
    $('code-error').hidden = true;
    $('code-dialog').showModal();
  },
  'close-code': () => $('code-dialog').close(),
  'confirm-start': () => {
    $('confirm-dialog').close();
    const action = ui.pendingStart;
    ui.pendingStart = null;
    action?.();
  },
  'cancel-start': () => {
    ui.pendingStart = null;
    $('confirm-dialog').close();
  },
  undo,
  erase,
  'toggle-notes': () => {
    ui.notesMode = !ui.notesMode;
    renderGame();
  },
  hint: requestHint,
  'apply-hint': applyHint,
  'dismiss-hint': () => {
    ui.hint = null;
    renderGame();
  },
  share: () => share(),
  'share-solved': () => share(`Geschafft in ${formatTime(ui.game.elapsedSeconds)}! `),
};

document.addEventListener('click', (event) => {
  const el = event.target.closest('[data-action]');
  if (el && actions[el.dataset.action]) actions[el.dataset.action](el);
});

document.addEventListener('change', (event) => {
  const key = event.target.dataset?.setting;
  if (key) updateSetting(key, event.target.checked);
});

$('code-form').addEventListener('submit', (event) => {
  event.preventDefault();
  if (startCode($('code-input').value)) $('code-dialog').close();
  else $('code-error').hidden = false;
});

document.addEventListener('keydown', (event) => {
  if (ui.screen !== 'game' || !ui.game || document.querySelector('dialog[open]')) return;
  if (event.ctrlKey || event.metaKey || event.altKey) {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
      event.preventDefault();
      undo();
    }
    return;
  }
  const digit = /^Digit([1-9])$/.exec(event.code)?.[1] ?? /^Numpad([1-9])$/.exec(event.code)?.[1];
  if (digit) {
    event.preventDefault();
    input(Number(digit), event.shiftKey ? !ui.notesMode : ui.notesMode);
    return;
  }
  const moves = { ArrowUp: -9, ArrowDown: 9, ArrowLeft: -1, ArrowRight: 1 };
  if (event.key in moves) {
    event.preventDefault();
    if (ui.selected < 0) return select(40);
    const r = row(ui.selected);
    const c = col(ui.selected);
    const next = event.key === 'ArrowUp' ? ((r + 8) % 9) * 9 + c : event.key === 'ArrowDown' ? ((r + 1) % 9) * 9 + c
      : event.key === 'ArrowLeft' ? r * 9 + ((c + 8) % 9) : r * 9 + ((c + 1) % 9);
    select(next);
    return;
  }
  const key = event.key.toLowerCase();
  if (['backspace', 'delete', '0'].includes(key)) {
    event.preventDefault();
    erase();
  } else if (key === 'n') {
    ui.notesMode = !ui.notesMode;
    renderGame();
  } else if (key === 'h') {
    requestHint();
  } else if (key === 'escape') {
    ui.hint = null;
    renderGame();
  }
});

// Spielzeit zählt nur, solange das Spiel sichtbar ist.
setInterval(() => {
  const game = ui.game;
  if (!game || ui.screen !== 'game' || document.hidden || game.isSolved) return;
  ui.game = game.copy({ elapsedSeconds: game.elapsedSeconds + 1 });
  $('timer').textContent = formatTime(ui.game.elapsedSeconds);
  if (ui.game.elapsedSeconds % 15 === 0) save();
}, 1000);

document.addEventListener('visibilitychange', () => { if (document.hidden) save(); });
addEventListener('pagehide', save);
new ResizeObserver(() => drawBoard()).observe(canvas);
const onThemeChange = () => {
  ui.cageColors = null;
  drawBoard();
};
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', onThemeChange);
new MutationObserver(onThemeChange).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

// Ein Code im Link (…#M-123456) startet direkt dieses Rätsel.
function openCodeFromLink() {
  const code = decodeURIComponent(location.hash.slice(1));
  if (!code) return;
  history.replaceState(null, '', location.pathname + location.search);
  if (!startCode(code)) showToast('Ungültiger Rätsel-Code im Link');
}
addEventListener('hashchange', openCodeFromLink);

render();
openCodeFromLink();
prefetch();
