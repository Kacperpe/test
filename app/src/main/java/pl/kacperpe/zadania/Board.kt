package pl.kacperpe.zadania

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun Board(vm: PuzzleVm, modifier: Modifier) {
    val ctx = LocalContext.current
    val theme = BOARDS[vm.boardTheme.coerceIn(0, BOARDS.size - 1)]
    val set = vm.pieceSet
    val flip = !vm.playerWhite
    val anim = vm.anim
    val progress = remember { Animatable(1f) }
    var started by remember { mutableStateOf<Anim?>(null) }
    var finished by remember { mutableStateOf<Anim?>(null) }
    LaunchedEffect(anim) {
        if (anim != null) {
            progress.snapTo(0f)
            started = anim
            progress.animateTo(1f, tween(220))
            finished = anim
        }
    }
    val t = when {
        anim == null || finished === anim -> 1f
        started === anim -> progress.value
        else -> 0f
    }
    var drag by remember { mutableStateOf<Offset?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(modifier) {
        val cell = maxWidth / 8
        val cellPx = with(LocalDensity.current) { cell.toPx() }
        fun col(sq: Int) = if (flip) 7 - sq % 8 else sq % 8
        fun row(sq: Int) = if (flip) sq / 8 else 7 - sq / 8
        fun sqAt(o: Offset): Int {
            val x = (o.x / cellPx).toInt().coerceIn(0, 7)
            val y = (o.y / cellPx).toInt().coerceIn(0, 7)
            return (if (flip) y else 7 - y) * 8 + (if (flip) 7 - x else x)
        }
        val board = vm.pos.b
        val targets = vm.targets()
        val sel = vm.selected
        val last = vm.lastMove
        val hint = vm.hint
        val pre = vm.premove

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(flip, cellPx) { detectTapGestures { vm.tap(sqAt(it)) } }
                .pointerInput(flip, cellPx) {
                    detectDragGestures(
                        onDragStart = { o ->
                            val s = sqAt(o)
                            if (vm.canPick(s)) { dragFrom = s; drag = o; vm.select(s) }
                        },
                        onDrag = { change, d ->
                            change.consume()
                            if (dragFrom >= 0) drag = drag?.plus(d)
                        },
                        onDragEnd = {
                            val d = drag
                            if (dragFrom >= 0 && d != null) vm.tryMove(dragFrom, sqAt(d), false)
                            dragFrom = -1; drag = null
                        },
                        onDragCancel = { dragFrom = -1; drag = null },
                    )
                },
        ) {
            val c = size.width / 8
            for (y in 0..7) for (x in 0..7) {
                drawRect(if ((x + y) % 2 == 0) theme.light else theme.dark, Offset(x * c, y * c), Size(c, c))
            }
            fun mark(sq: Int, color: Color) = drawRect(color, Offset(col(sq) * c, row(sq) * c), Size(c, c))
            if (last != null) { mark(last.from, Hl); mark(last.to, Hl) }
            if (sel >= 0) mark(sel, Hl)
            if (hint >= 0) mark(hint, HintCol)
            if (pre != null) { mark(pre.first, PreCol); mark(pre.second, PreCol) }
            for (sq in targets) {
                val center = Offset(col(sq) * c + c / 2, row(sq) * c + c / 2)
                if (board[sq] == '.') drawCircle(Color(0x30000000), c * 0.17f, center)
                else drawCircle(Color(0x30000000), c * 0.44f, center, style = Stroke(c * 0.09f))
            }
        }

        if (cell > 30.dp) for (i in 0..7) {
            val rank = if (flip) i + 1 else 8 - i
            val file = if (flip) 'h' - i else 'a' + i
            Text(
                "$rank", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = if (i % 2 == 0) theme.dark else theme.light,
                modifier = Modifier.offset(3.dp, cell * i),
            )
            Text(
                "$file", fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                color = if (i % 2 == 0) theme.light else theme.dark,
                modifier = Modifier.offset(cell * i, cell * 8 - 15.dp).width(cell - 3.dp),
            )
        }

        val cap = anim?.captured
        if (cap != null && t < 1f) {
            Image(
                painterResource(pieceRes(ctx, set, cap.second)), null,
                Modifier.offset(cell * col(cap.first), cell * row(cap.first)).size(cell).alpha(1f - t),
            )
        }

        for (sq in 0 until 64) {
            val ch = board[sq]
            if (ch == '.' || (sq == dragFrom && drag != null)) continue
            var x = col(sq).toFloat()
            var y = row(sq).toFloat()
            val mv = if (anim != null && t < 1f) anim.moves.firstOrNull { it.second == sq } else null
            if (mv != null) {
                x = col(mv.first) + (x - col(mv.first)) * t
                y = row(mv.first) + (y - row(mv.first)) * t
            }
            Image(painterResource(pieceRes(ctx, set, ch)), null, Modifier.offset(cell * x, cell * y).size(cell))
        }

        vm.arrow?.let { a ->
            Canvas(Modifier.fillMaxSize()) {
                val c = size.width / 8
                val from = Offset(col(a.from) * c + c / 2, row(a.from) * c + c / 2)
                val to = Offset(col(a.to) * c + c / 2, row(a.to) * c + c / 2)
                val v = to - from
                val len = v.getDistance()
                if (len > 0f) {
                    val u = v / len
                    val n = Offset(-u.y, u.x)
                    val base = to - u * (c * 0.42f)
                    val start = from + u * (c * 0.32f)
                    val w = c * 0.11f
                    val hw = c * 0.27f
                    val pts = listOf(start + n * w, base + n * w, base + n * hw, to, base - n * hw, base - n * w, start - n * w)
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                        close()
                    }
                    drawPath(path, ArrowCol)
                }
            }
        }

        vm.badge?.let { (sq, ok) ->
            val bx = (cell * col(sq) + cell * 0.6f).coerceAtMost(cell * 7.55f)
            val by = (cell * row(sq) - cell * 0.15f).coerceAtLeast(0.dp)
            Box(
                Modifier.offset(bx, by).size(cell * 0.45f).background(if (ok) Green else Red, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(if (ok) "✓" else "✕", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black) }
        }

        val d = drag
        if (d != null && dragFrom >= 0 && board[dragFrom] != '.') {
            Image(
                painterResource(pieceRes(ctx, set, board[dragFrom])), null,
                Modifier.offset { IntOffset((d.x - cellPx * 0.75f).roundToInt(), (d.y - cellPx * 1.1f).roundToInt()) }
                    .size(cell * 1.5f),
            )
        }
    }
}
