package pl.kacperpe.zadania

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
private fun Sheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(0.94f).fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(14.dp)).background(Bg),
        ) {
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
    Text(t.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

@Composable
private fun Note(t: String) {
    Text(t, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Toggle(label: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(label, color = Color.White, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Green, checkedThumbColor = Color.White,
                uncheckedTrackColor = Chip, uncheckedThumbColor = Muted, uncheckedBorderColor = Chip,
            ),
        )
    }
}

@Composable
fun SettingsDialog(vm: PuzzleVm, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Sheet("Menu", onClose) {
        Text("TRYB", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipText("Trening", !vm.rushOn && vm.kind != Kind.DAILY, Modifier.weight(1f)) {
                if (vm.rushOn) vm.exitRush() else if (vm.kind == Kind.DAILY) vm.next()
                onClose()
            }
            ChipText("Zadanie dnia", !vm.rushOn && vm.kind == Kind.DAILY, Modifier.weight(1f)) { vm.loadDaily(); onClose() }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipText("Na czas · 3 min", vm.rushOn && vm.rushMin == 3, Modifier.weight(1f)) { vm.startRush(3); onClose() }
            ChipText("Na czas · 5 min", vm.rushOn && vm.rushMin == 5, Modifier.weight(1f)) { vm.startRush(5); onClose() }
        }
        Note("Na czas: coraz trudniejsze zadania, trzy błędy kończą serię. Nie wpływa na ranking.")

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

        Label("Trudność")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((k, l) in DIFFS) ChipText(l, vm.diff == k) { vm.pickDiff(k) }
        }
        Note("Liczona względem Twojego rankingu (około ±300 i ±600 punktów). Auto dopasowuje poziom samo. Działa od następnego zadania.")

        Label("Motyw")
        for (pair in ANGLES.chunked(2)) {
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, l) in pair) ChipText(l, vm.angle == k && !vm.rushOn, Modifier.weight(1f)) { vm.pickAngle(k); onClose() }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Label("Nauka")
        Toggle("Powtórki błędów", "Zawalone zadania wracają po 10 minutach, aż rozwiążesz je bez pomocy. W kolejce: ${vm.reviews.size}", vm.reviewOn) { vm.pickReview(it) }
        Text("Cel dzienny", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (g in listOf(5, 10, 20, 30)) ChipText("$g", vm.goal == g, Modifier.weight(1f)) { vm.pickGoal(g) }
        }
        Spacer(Modifier.height(8.dp))
        Toggle("Przypomnienie o 19:00", "Powiadomienie, jeśli cel dnia nie jest jeszcze zrobiony", vm.remind) {
            vm.pickRemind(it)
            if (it && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Note("Pobrane na zapas (offline): ${vm.cached} zadań. Zapas uzupełnia się sam, gdy jest sieć.")

        Label("Dźwięk")
        Toggle("Dźwięki", null, vm.sound) { vm.pickSound(it) }
        Toggle("Wibracje", null, vm.vib) { vm.pickVib(it) }

        Label("Szachownica")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BOARDS.forEachIndexed { i, b ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .border(2.dp, if (vm.boardTheme == i) Green else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { vm.pickBoard(i) }.padding(4.dp),
                ) {
                    Row { Box(Modifier.weight(1f).aspectRatio(1f).background(b.light)); Box(Modifier.weight(1f).aspectRatio(1f).background(b.dark)) }
                    Row { Box(Modifier.weight(1f).aspectRatio(1f).background(b.dark)); Box(Modifier.weight(1f).aspectRatio(1f).background(b.light)) }
                }
            }
        }

        Label("Figury")
        val board = BOARDS[vm.boardTheme.coerceIn(0, BOARDS.size - 1)]
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((k, l) in PIECES) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .border(2.dp, if (vm.pieceSet == k) Green else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { vm.pickPieces(k) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.clip(RoundedCornerShape(4.dp))) {
                        Image(painterResource(pieceRes(ctx, k, 'N')), null, Modifier.weight(1f).aspectRatio(1f).background(board.dark))
                        Image(painterResource(pieceRes(ctx, k, 'q')), null, Modifier.weight(1f).aspectRatio(1f).background(board.light))
                    }
                    Text(l, color = Muted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@Composable
fun ProgressDialog(vm: PuzzleVm, onClose: () -> Unit) {
    val pts = vm.history.filter { it.l == vm.logged }
    val wins = pts.count { it.w }
    val timed = pts.filter { it.s > 0 }
    Sheet(if (vm.logged) "Postęp · Lichess" else "Postęp", onClose) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${vm.rating}", "ranking", Modifier.weight(1f))
            Stat("${pts.size}", "zadań", Modifier.weight(1f))
            Stat(if (pts.isEmpty()) "–" else "${wins * 100 / pts.size}%", "skuteczność", Modifier.weight(1f))
            Stat("${vm.best}", "rekord serii", Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${vm.dayCount}/${vm.goal}", "dziś", Modifier.weight(1f))
            Stat("${vm.dayStreak}", "dni z rzędu", Modifier.weight(1f))
            Stat(if (timed.isEmpty()) "–" else mmss(timed.sumOf { it.s } / timed.size), "śr. czas", Modifier.weight(1f))
            Stat("${vm.reviews.size}", "do powtórki", Modifier.weight(1f))
        }

        Label("Ranking")
        if (pts.size < 2) {
            Text("Rozwiąż kilka zadań, a pojawi się tu wykres rankingu.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp))
        } else {
            val shown = pts.takeLast(200).map { it.r }
            Chart(shown)
            Text("ostatnie ${shown.size} zadań", color = Muted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }

        if (pts.isNotEmpty()) {
            Label("Ostatnie wyniki")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val lastOnes = pts.takeLast(18)
                for (e in lastOnes) {
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(if (e.w) Green else Red), contentAlignment = Alignment.Center) {
                        Text(if (e.w) "✓" else "✕", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
                repeat(18 - lastOnes.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Label("Motywy, od najsłabszego")
        val rows = THEME_LABELS.keys.mapNotNull { k ->
            val total = vm.themeTotal[k] ?: 0
            if (total >= 3) Triple(k, (vm.themeWins[k] ?: 0) * 100 / total, total) else null
        }.sortedBy { it.second }.take(10)
        if (rows.isEmpty()) {
            Text("Statystyki motywu pojawią się po 3 zadaniach z tym motywem.", color = Muted, fontSize = 13.sp)
        } else {
            for ((k, pct, total) in rows) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.pickAngle(k); onClose() }.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(themeLabel(k), color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.2f))
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Chip)) {
                        Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(if (pct < 50) Red else Green))
                    }
                    Text("$pct% · $total", color = Muted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(74.dp))
                    Text("ćwicz ›", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Label("Na czas, rekordy")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${vm.rushBest3}", "3 minuty", Modifier.weight(1f))
            Stat("${vm.rushBest5}", "5 minut", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Panel).padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
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
fun RushResultDialog(vm: PuzzleVm) {
    Dialog(onDismissRequest = { }) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Bg).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val best = if (vm.rushMin == 3) vm.rushBest3 else vm.rushBest5
            Text(if (vm.rushStrikes >= 3) "Trzy błędy" else "Koniec", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("${vm.rushScore}", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (vm.rushScore >= best && vm.rushScore > 0) "Nowy rekord na ${vm.rushMin} min!" else "Rekord na ${vm.rushMin} min: $best",
                color = if (vm.rushScore >= best && vm.rushScore > 0) Green else Muted, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 18.dp),
            )
            Btn("Jeszcze raz", true, Modifier.fillMaxWidth()) { vm.startRush(vm.rushMin) }
            Spacer(Modifier.height(8.dp))
            Btn("Wróć do treningu", false, Modifier.fillMaxWidth()) { vm.exitRush() }
        }
    }
}

@Composable
fun PromoDialog(vm: PuzzleVm) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = { vm.promote(null) }) {
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Panel).padding(10.dp)) {
            for (p in "qrbn") {
                val ch = if (vm.playerWhite) p.uppercaseChar() else p
                Image(
                    painterResource(pieceRes(ctx, vm.pieceSet, ch)), null,
                    Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.promote(p) }.padding(4.dp),
                )
            }
        }
    }
}
