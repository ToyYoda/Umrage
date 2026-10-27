package app.killersudoku.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.killersudoku.game.GameState
import app.killersudoku.game.Grid

/** Das Spielfeld: Käfige, Ziffern, Notizen und Markierungen. Antippen wählt ein Feld aus. */
@Composable
fun Board(game: GameState, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val currentOnSelect by rememberUpdatedState(onSelect)
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val cellSize = size.width / 9f
                val col = (offset.x / cellSize).toInt().coerceIn(0, 8)
                val row = (offset.y / cellSize).toInt().coerceIn(0, 8)
                currentOnSelect(row * 9 + col)
            }
        },
    ) {
        drawBoard(game, selected, scheme, textMeasurer)
    }
}

private fun DrawScope.drawBoard(game: GameState, selected: Int, scheme: ColorScheme, textMeasurer: TextMeasurer) {
    val puzzle = game.puzzle
    val cs = size.width / 9f
    val selectedDigit = if (selected >= 0) game.values[selected] else 0

    // Hintergründe: Auswahl, falsche Eingaben, gleiche Ziffer, gleiche Zeile/Spalte/Block.
    val background = Array(81) { cell ->
        val tint = when {
            cell == selected -> scheme.primary.copy(alpha = 0.35f)
            game.isWrong(cell) -> scheme.error.copy(alpha = 0.2f)
            selectedDigit != 0 && game.values[cell] == selectedDigit -> scheme.primary.copy(alpha = 0.22f)
            selected >= 0 && isPeer(cell, selected) -> scheme.primary.copy(alpha = 0.08f)
            else -> Color.Transparent
        }
        tint.compositeOver(scheme.surface)
    }
    for (cell in 0 until 81) {
        drawRect(background[cell], topLeft = cellOrigin(cell, cs), size = Size(cs, cs))
    }

    // Dünne Gitterlinien.
    val thin = 1.dp.toPx()
    for (i in 1 until 9) {
        if (i % 3 == 0) continue
        drawLine(scheme.outlineVariant, Offset(i * cs, 0f), Offset(i * cs, size.height), thin)
        drawLine(scheme.outlineVariant, Offset(0f, i * cs), Offset(size.width, i * cs), thin)
    }

    drawCages(game, cs, scheme.onSurfaceVariant)

    // Käfigsummen oben links im Käfig, mit Hintergrund, damit die gestrichelte Linie unterbrochen wird.
    val inset = cs * CAGE_INSET
    val sumStyle = TextStyle(fontSize = (cs * 0.21f).toSp(), fontWeight = FontWeight.SemiBold)
    puzzle.cages.forEachIndexed { index, cage ->
        val cell = puzzle.labelCellOfCage[index]
        val layout = textMeasurer.measure(cage.sum.toString(), sumStyle)
        val topLeft = cellOrigin(cell, cs) + Offset(inset * 0.5f, inset * 0.3f)
        drawRect(
            background[cell],
            topLeft = topLeft,
            size = Size(layout.size.width + inset * 0.4f, layout.size.height.toFloat()),
        )
        drawText(layout, color = scheme.onSurface, topLeft = topLeft)
    }

    // Dicke Linien für die 3×3-Blöcke und den Rand.
    val thick = 2.5.dp.toPx()
    for (i in 0..9 step 3) {
        val p = (i * cs).coerceIn(thick / 2, size.width - thick / 2)
        drawLine(scheme.onSurface, Offset(p, 0f), Offset(p, size.height), thick)
        drawLine(scheme.onSurface, Offset(0f, p), Offset(size.width, p), thick)
    }

    // Ziffern.
    val bigSize = (cs * 0.58f).toSp()
    val givenDigits = measureDigits(textMeasurer, TextStyle(fontSize = bigSize, fontWeight = FontWeight.Bold))
    val userDigits = measureDigits(textMeasurer, TextStyle(fontSize = bigSize, fontWeight = FontWeight.Normal))
    val noteDigits = measureDigits(textMeasurer, TextStyle(fontSize = (cs * 0.2f).toSp()))
    for (cell in 0 until 81) {
        val origin = cellOrigin(cell, cs)
        val value = game.values[cell]
        if (value != 0) {
            val (layout, color) = when {
                game.isGiven(cell) -> givenDigits[value - 1] to scheme.onSurface
                game.isWrong(cell) -> userDigits[value - 1] to scheme.error
                else -> userDigits[value - 1] to scheme.primary
            }
            drawText(layout, color = color, topLeft = origin + centered(layout, Offset(cs / 2, cs / 2)))
        } else if (game.notes[cell] != 0) {
            // Notizen als 3×3-Raster unterhalb der Käfigsumme.
            val left = cs * 0.12f
            val top = cs * 0.24f
            val w = cs * 0.82f / 3
            val h = cs * 0.72f / 3
            for (digit in 1..9) {
                if (!game.hasNote(cell, digit)) continue
                val i = digit - 1
                val center = origin + Offset(left + (i % 3 + 0.5f) * w, top + (i / 3 + 0.5f) * h)
                if (digit == selectedDigit) {
                    drawCircle(scheme.primary.copy(alpha = 0.3f), radius = minOf(w, h) * 0.5f, center = center)
                }
                val layout = noteDigits[i]
                drawText(layout, color = scheme.onSurfaceVariant, topLeft = center + centered(layout, Offset.Zero))
            }
        }
    }
}

/** Zeichnet die Käfigumrisse gestrichelt und leicht nach innen versetzt. */
private fun DrawScope.drawCages(game: GameState, cs: Float, color: Color) {
    val cageOf = game.puzzle.cageOf
    val d = cs * CAGE_INSET
    val stroke = 1.2.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(cs * 0.08f, cs * 0.06f))

    fun line(x0: Float, y0: Float, x1: Float, y1: Float) =
        drawLine(color, Offset(x0, y0), Offset(x1, y1), stroke, pathEffect = dash)

    for (cell in 0 until 81) {
        val r = Grid.row(cell)
        val c = Grid.col(cell)
        fun same(dr: Int, dc: Int): Boolean {
            val nr = r + dr
            val nc = c + dc
            return nr in 0..8 && nc in 0..8 && cageOf[nr * 9 + nc] == cageOf[cell]
        }
        val x0 = c * cs
        val y0 = r * cs
        val x1 = x0 + cs
        val y1 = y0 + cs
        val up = same(-1, 0)
        val down = same(1, 0)
        val left = same(0, -1)
        val right = same(0, 1)

        if (!up) line(if (left) x0 else x0 + d, y0 + d, if (right) x1 else x1 - d, y0 + d)
        if (!down) line(if (left) x0 else x0 + d, y1 - d, if (right) x1 else x1 - d, y1 - d)
        if (!left) line(x0 + d, if (up) y0 else y0 + d, x0 + d, if (down) y1 else y1 - d)
        if (!right) line(x1 - d, if (up) y0 else y0 + d, x1 - d, if (down) y1 else y1 - d)

        // Innere Ecken, an denen zwei Nachbarn zum Käfig gehören, das diagonale Feld aber nicht.
        if (up && left && !same(-1, -1)) {
            line(x0, y0 + d, x0 + d, y0 + d)
            line(x0 + d, y0, x0 + d, y0 + d)
        }
        if (up && right && !same(-1, 1)) {
            line(x1 - d, y0 + d, x1, y0 + d)
            line(x1 - d, y0, x1 - d, y0 + d)
        }
        if (down && left && !same(1, -1)) {
            line(x0, y1 - d, x0 + d, y1 - d)
            line(x0 + d, y1 - d, x0 + d, y1)
        }
        if (down && right && !same(1, 1)) {
            line(x1 - d, y1 - d, x1, y1 - d)
            line(x1 - d, y1 - d, x1 - d, y1)
        }
    }
}

private const val CAGE_INSET = 0.09f

private fun cellOrigin(cell: Int, cs: Float) = Offset(Grid.col(cell) * cs, Grid.row(cell) * cs)

private fun isPeer(a: Int, b: Int) =
    Grid.row(a) == Grid.row(b) || Grid.col(a) == Grid.col(b) || Grid.box(a) == Grid.box(b)

private fun measureDigits(textMeasurer: TextMeasurer, style: TextStyle): List<TextLayoutResult> =
    (1..9).map { textMeasurer.measure(it.toString(), style) }

/** Versatz, um einen Text um [center] zu zentrieren. */
private fun centered(layout: TextLayoutResult, center: Offset) =
    center - Offset(layout.size.width / 2f, layout.size.height / 2f)
