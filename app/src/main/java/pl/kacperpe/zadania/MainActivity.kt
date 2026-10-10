package pl.kacperpe.zadania

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.horizontalScroll
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

private val DIFFS = listOf(
    "auto" to "Auto", "easiest" to "Najłatwiejsze", "easier" to "Łatwiejsze",
    "normal" to "Normalne", "harder" to "Trudniejsze", "hardest" to "Najtrudniejsze",
)

class MainActivity : ComponentActivity() {
    private val vm: PuzzleVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = SystemBarStyle.dark(0xFF262522.toInt())
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
        if (savedInstanceState == null) intent?.data?.let { vm.handleRedirect(it) }
        setContent { App(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { vm.handleRedirect(it) }
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
    var menu by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Panel).systemBarsPadding().background(Bg)) {
        Header(vm, onMenu = { menu = true }, onStats = { stats = true })
        Spacer(Modifier.weight(0.5f))
        Banner(vm)
        Board(vm)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = ANGLES.firstOrNull { it.first == vm.angle }?.second ?: vm.angle
            ChipText("$label  ▾", false) { menu = true }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                vm.puzzle?.let { Text("#${it.id} · trudność ${it.rating}", color = Muted, fontSize = 13.sp) }
                vm.syncError?.let { Text(it, color = Red, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.weight(1f))
        Actions(vm)
    }
    if (vm.promo != null) PromoDialog(vm)
    if (menu) SettingsDialog(vm) { menu = false }
    if (stats) ProgressDialog(vm) { stats = false }
}

@Composable
private fun Header(vm: PuzzleVm, onMenu: () -> Unit, onStats: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.wn), null, Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text("Zadania", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        if (vm.streak > 1) {
            Text("🔥 ${vm.streak}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
        }
        Column(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onStats).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${vm.rating}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                vm.delta?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(if (it > 0) "+$it" else "$it", color = if (it > 0) Green else Red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(if (vm.logged) "Lichess · postęp ›" else "ranking · postęp ›", color = Muted, fontSize = 11.sp)
        }
        Text(
            "☰", color = Color.White, fontSize = 22.sp,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onMenu).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ChipText(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = if (on) Color.White else Color(0xFFD6D5D3),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Green else Chip)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun Sheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.93f).heightIn(max = 640.dp).clip(RoundedCornerShape(14.dp)).background(Bg)) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("✕", color = Muted, fontSize = 18.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(14.dp))
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), content = content)
        }
    }
}

@Composable
private fun Label(t: String) {
    Text(t.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
}

@Composable
private fun SettingsDialog(vm: PuzzleVm, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Sheet("Ustawienia", onClose) {
        Label("Konto Lichess")
        if (vm.logged) {
            Text("Zalogowano jako ${vm.user ?: "konto Lichess"}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Wyniki zapisują się na Twoim koncie, a zadania są dobierane do Twojego rankingu Lichess i się nie powtarzają.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            Btn("Wyloguj", false, Modifier.fillMaxWidth()) { vm.logout() }
        } else {
            Text(
                "Po zalogowaniu wyniki zapisują się w historii zadań na Lichess, a zadania są dobierane do Twojego rankingu.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp),
            )
            Btn(if (vm.loggingIn) "Logowanie…" else "Zaloguj przez Lichess", true, Modifier.fillMaxWidth()) {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.loginUrl())))
                } catch (_: Exception) {
                }
            }
        }
        vm.loginMsg?.let { Text(it, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }

        Spacer(Modifier.height(12.dp))
        Label("Trudność")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((k, l) in DIFFS) ChipText(l, vm.diff == k) { vm.pickDiff(k) }
        }
        Text(
            "Liczona względem Twojego rankingu (około ±300 i ±600 punktów). Auto dopasowuje poziom samo. Działa od następnego zadania.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(12.dp))
        Label("Motyw")
        for (pair in ANGLES.chunked(2)) {
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, l) in pair) ChipText(l, vm.angle == k, Modifier.weight(1f)) { vm.pickAngle(k); onClose() }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProgressDialog(vm: PuzzleVm, onClose: () -> Unit) {
    val pts = vm.history.filter { it.l == vm.logged }
    val wins = pts.count { it.w }
    Sheet(if (vm.logged) "Postęp · Lichess" else "Postęp", onClose) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${vm.rating}", "ranking", Modifier.weight(1f))
            Stat("${pts.size}", "zadań", Modifier.weight(1f))
            Stat(if (pts.isEmpty()) "–" else "${wins * 100 / pts.size}%", "skuteczność", Modifier.weight(1f))
            Stat("${vm.best}", "rekord serii", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Label("Ranking")
        if (pts.size < 2) {
            Text("Rozwiąż kilka zadań, a pojawi się tu wykres rankingu.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp))
        } else {
            val shown = pts.takeLast(200).map { it.r }
            Chart(shown)
            Text("ostatnie ${shown.size} zadań", color = Muted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        if (pts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Label("Ostatnie wyniki")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (e in pts.takeLast(18)) {
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(if (e.w) Green else Red), contentAlignment = Alignment.Center) {
                        Text(if (e.w) "✓" else "✕", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Panel).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Muted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun Chart(v: List<Int>) {
    var lo = v.min()
    var hi = v.max()
    if (hi - lo < 40) { val mid = (hi + lo) / 2; lo = mid - 20; hi = mid + 20 }
    Row(Modifier.fillMaxWidth().height(190.dp)) {
        Column(Modifier.fillMaxHeight().padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            for (l in listOf(hi, (hi + lo) / 2, lo)) Text("$l", color = Muted, fontSize = 11.sp)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val pad = 8.dp.toPx()
            val h = size.height - 2 * pad
            val w = size.width - pad
            for (i in 0..2) {
                val y = pad + h * i / 2
                drawLine(Color(0x22FFFFFF), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            fun pt(i: Int) = Offset(w * i / (v.size - 1), pad + h * (hi - v[i]) / (hi - lo).toFloat())
            val line = Path().apply {
                moveTo(pt(0).x, pt(0).y)
                for (i in 1 until v.size) lineTo(pt(i).x, pt(i).y)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(pt(v.size - 1).x, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Green.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, Green, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(Green, 5.dp.toPx(), pt(v.size - 1))
            drawCircle(Color.White, 2.dp.toPx(), pt(v.size - 1))
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
