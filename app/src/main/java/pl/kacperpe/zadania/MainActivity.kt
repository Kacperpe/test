package pl.kacperpe.zadania

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

private val Bg = Color(0xFF302E2B)
private val Panel = Color(0xFF262522)
private val Chip = Color(0xFF3C3A37)
private val LightSq = Color(0xFFEBECD0)
private val DarkSq = Color(0xFF739552)
private val Green = Color(0xFF81B64C)
private val GreenDark = Color(0xFF5D9948)
private val Red = Color(0xFFE5483B)
private val Hl = Color(0x99F5F646)
private val HintCol = Color(0xB34FC3F7)
private val ArrowCol = Color(0xCCF7A62B)
private val Muted = Color(0xFFA9A8A6)

private val ANGLES = listOf(
    "mix" to "Wszystkie", "mateIn1" to "Mat w 1", "mateIn2" to "Mat w 2", "mateIn3" to "Mat w 3",
    "fork" to "Widełki", "pin" to "Związanie", "skewer" to "Rożen", "discoveredAttack" to "Atak z odsłony",
    "sacrifice" to "Poświęcenie", "hangingPiece" to "Wisząca figura", "backRankMate" to "Mat na ostatniej linii",
    "promotion" to "Promocja", "opening" to "Debiut", "middlegame" to "Gra środkowa", "endgame" to "Końcówka",
)

class MainActivity : ComponentActivity() {
    private val vm: PuzzleVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = SystemBarStyle.dark(0xFF262522.toInt())
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
        setContent { App(vm) }
    }
}

private fun pieceRes(c: Char): Int = when (c) {
    'K' -> R.drawable.wk; 'Q' -> R.drawable.wq; 'R' -> R.drawable.wr
    'B' -> R.drawable.wb; 'N' -> R.drawable.wn; 'P' -> R.drawable.wp
    'k' -> R.drawable.bk; 'q' -> R.drawable.bq; 'r' -> R.drawable.br
    'b' -> R.drawable.bb; 'n' -> R.drawable.bn; else -> R.drawable.bp
}

@Composable
fun App(vm: PuzzleVm) {
    Column(Modifier.fillMaxSize().background(Panel).systemBarsPadding().background(Bg)) {
        Header(vm)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 10.dp),
        ) {
            items(ANGLES) { (key, label) ->
                val on = vm.angle == key
                Text(
                    label,
                    color = if (on) Color.White else Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Green else Chip)
                        .clickable { vm.pickAngle(key) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Banner(vm)
        Board(vm)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val pz = vm.puzzle
            Text(if (pz != null) "Zadanie #${pz.id}" else "", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (pz != null) Text("Trudność ${pz.rating}", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        Actions(vm)
    }
    if (vm.promo != null) PromoDialog(vm)
}

@Composable
private fun Header(vm: PuzzleVm) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.wn), null, Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text("Zadania", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        if (vm.streak > 1) {
            Text("🔥 ${vm.streak}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${vm.rating}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                vm.delta?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(if (it > 0) "+$it" else "$it", color = if (it > 0) Green else Red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("ranking", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Banner(vm: PuzzleVm) {
    val wrong = vm.status == Status.WRONG
    val solved = vm.status == Status.SOLVED
    val color = when {
        wrong -> Red
        solved -> Green
        else -> Panel
    }
    Row(
        Modifier.fillMaxWidth().background(color).height(56.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (vm.status) {
            Status.LOADING -> {
                CircularProgressIndicator(Modifier.size(22.dp), color = Green, strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Title("Ładowanie zadania…", null)
            }
            Status.ERROR -> Title("Nie udało się pobrać zadania", vm.error)
            Status.WRONG -> { Mark("✕", Red); Title("Błędny ruch", "Spróbuj jeszcze raz") }
            Status.SOLVED -> { Mark("✓", Green); Title("Rozwiązane!", vm.delta?.let { if (it > 0) "Ranking +$it" else null }) }
            Status.SHOWING -> Title("Rozwiązanie", null)
            Status.PLAYING -> {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(5.dp))
                        .background(if (vm.playerWhite) Color.White else Color(0xFF1B1A18)),
                )
                Spacer(Modifier.width(12.dp))
                val ok = vm.badge?.second == true
                Title(
                    if (ok) "Dobry ruch!" else if (vm.playerWhite) "Ruch białych" else "Ruch czarnych",
                    if (ok) "Graj dalej" else "Znajdź najlepszy ruch",
                )
            }
        }
    }
}

@Composable
private fun Mark(s: String, c: Color) {
    Box(Modifier.size(28.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Text(s, color = c, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
    Spacer(Modifier.width(12.dp))
}

@Composable
private fun Title(t: String, sub: String?) {
    Column {
        Text(t, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Board(vm: PuzzleVm) {
    val flip = !vm.playerWhite
    val anim = vm.anim
    val progress = remember { Animatable(1f) }
    var started by remember { mutableStateOf<Anim?>(null) }
    var finished by remember { mutableStateOf<Anim?>(null) }
    LaunchedEffect(anim) {
        if (anim != null) {
            progress.snapTo(0f)
            started = anim
            progress.animateTo(1f, tween(230))
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

    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
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
                drawRect(if ((x + y) % 2 == 0) LightSq else DarkSq, Offset(x * c, y * c), Size(c, c))
            }
            fun mark(sq: Int, color: Color) = drawRect(color, Offset(col(sq) * c, row(sq) * c), Size(c, c))
            if (last != null) { mark(last.from, Hl); mark(last.to, Hl) }
            if (sel >= 0) mark(sel, Hl)
            if (hint >= 0) mark(hint, HintCol)
            for (sq in targets) {
                val center = Offset(col(sq) * c + c / 2, row(sq) * c + c / 2)
                if (board[sq] == '.') drawCircle(Color(0x30000000), c * 0.17f, center)
                else drawCircle(Color(0x30000000), c * 0.44f, center, style = Stroke(c * 0.09f))
            }
        }

        for (i in 0..7) {
            val rank = if (flip) i + 1 else 8 - i
            val file = if (flip) 'h' - i else 'a' + i
            Text(
                "$rank", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = if (i % 2 == 0) DarkSq else LightSq,
                modifier = Modifier.offset(3.dp, cell * i),
            )
            Text(
                "$file", fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                color = if (i % 2 == 0) LightSq else DarkSq,
                modifier = Modifier.offset(cell * i, cell * 8 - 15.dp).width(cell - 3.dp),
            )
        }

        for (sq in 0 until 64) {
            val ch = board[sq]
            if (ch == '.' || (sq == dragFrom && drag != null)) continue
            var x = col(sq).toFloat()
            var y = row(sq).toFloat()
            if (anim != null && t < 1f && anim.move.to == sq) {
                x = col(anim.move.from) + (x - col(anim.move.from)) * t
                y = row(anim.move.from) + (y - row(anim.move.from)) * t
            }
            Image(painterResource(pieceRes(ch)), null, Modifier.offset(cell * x, cell * y).size(cell))
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
                    val head = c * 0.42f
                    val base = to - u * head
                    val start = from + u * (c * 0.32f)
                    val w = c * 0.11f
                    val path = Path().apply {
                        moveTo((start + n * w).x, (start + n * w).y)
                        lineTo((base + n * w).x, (base + n * w).y)
                        lineTo((base + n * (c * 0.27f)).x, (base + n * (c * 0.27f)).y)
                        lineTo(to.x, to.y)
                        lineTo((base - n * (c * 0.27f)).x, (base - n * (c * 0.27f)).y)
                        lineTo((base - n * w).x, (base - n * w).y)
                        lineTo((start - n * w).x, (start - n * w).y)
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
                painterResource(pieceRes(board[dragFrom])), null,
                Modifier.offset { IntOffset((d.x - cellPx * 0.75f).roundToInt(), (d.y - cellPx * 1.1f).roundToInt()) }
                    .size(cell * 1.5f),
            )
        }
    }
}

@Composable
private fun Actions(vm: PuzzleVm) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (vm.status) {
            Status.PLAYING -> {
                Btn("💡 Podpowiedź", false, Modifier.weight(1f)) { vm.showHint() }
                Btn("Rozwiązanie", false, Modifier.weight(1f)) { vm.showSolution() }
                Btn("Pomiń", false, Modifier.weight(1f)) { vm.next() }
            }
            Status.WRONG -> {
                Btn("↺ Ponów", true, Modifier.weight(1f)) { vm.retry() }
                Btn("Rozwiązanie", false, Modifier.weight(1f)) { vm.showSolution() }
                Btn("Następne", false, Modifier.weight(1f)) { vm.next() }
            }
            Status.SOLVED -> Btn("Następne zadanie  ›", true, Modifier.weight(1f)) { vm.next() }
            Status.ERROR -> Btn("Spróbuj ponownie", true, Modifier.weight(1f)) { vm.next() }
            else -> Btn("…", false, Modifier.weight(1f)) {}
        }
    }
}

@Composable
private fun Btn(text: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(if (primary) GreenDark else Color(0xFF2B2A28))
            .clickable(onClick = onClick).padding(bottom = 4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (primary) Green else Chip)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = Color.White, fontSize = if (primary) 17.sp else 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun PromoDialog(vm: PuzzleVm) {
    Dialog(onDismissRequest = { vm.promote(null) }) {
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Panel).padding(10.dp)) {
            for (p in "qrbn") {
                val ch = if (vm.playerWhite) p.uppercaseChar() else p
                Image(
                    painterResource(pieceRes(ch)), null,
                    Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.promote(p) }.padding(4.dp),
                )
            }
        }
    }
}
