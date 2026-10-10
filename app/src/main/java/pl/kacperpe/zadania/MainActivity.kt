package pl.kacperpe.zadania

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

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

@Composable
fun App(vm: PuzzleVm) {
    var menu by rememberSaveable { mutableStateOf(false) }
    var stats by rememberSaveable { mutableStateOf(false) }
    val onMenu = { menu = true }
    val onStats = { stats = true }
    BoxWithConstraints(Modifier.fillMaxSize().background(Panel).systemBarsPadding().background(Bg)) {
        if (maxWidth > maxHeight) {
            // poziomo / tablet: plansza po lewej, panel po prawej
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().padding(6.dp), contentAlignment = Alignment.Center) {
                    Board(vm, Modifier.size(min(maxHeight - 12.dp, maxWidth * 0.6f)))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Header(vm, onMenu, onStats)
                    DayBar(vm, onStats)
                    Banner(vm)
                    Info(vm, onMenu)
                    Spacer(Modifier.weight(1f))
                    Actions(vm)
                }
            }
        } else {
            val side: Dp = min(maxWidth, maxHeight - 300.dp).coerceAtLeast(180.dp)
            Column(Modifier.fillMaxSize()) {
                Header(vm, onMenu, onStats)
                DayBar(vm, onStats)
                Spacer(Modifier.weight(0.4f))
                Banner(vm)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Board(vm, Modifier.size(side)) }
                Info(vm, onMenu)
                Spacer(Modifier.weight(1f))
                Actions(vm)
            }
        }
    }
    if (vm.promo != null) PromoDialog(vm)
    if (menu) SettingsDialog(vm) { menu = false }
    if (stats) ProgressDialog(vm) { stats = false }
    if (vm.rushOver) RushResultDialog(vm)
}

@Composable
private fun Header(vm: PuzzleVm, onMenu: () -> Unit, onStats: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.wn), null, Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (vm.rushOn) "Na czas" else "Zadania", color = Color.White, fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (vm.streak > 1 && !vm.rushOn) {
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
private fun DayBar(vm: PuzzleVm, onStats: () -> Unit) {
    if (vm.rushOn) return
    val done = vm.dayCount
    val goal = vm.goal
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onStats).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (done >= goal) "Cel dnia zrobiony ✓" else "Dziś $done/$goal", color = if (done >= goal) Green else Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Chip)) {
            Box(Modifier.fillMaxWidth((done.toFloat() / goal).coerceIn(0f, 1f)).fillMaxHeight().background(Green))
        }
        if (vm.dayStreak > 0) {
            Spacer(Modifier.width(10.dp))
            Text("${vm.dayStreak} ${if (vm.dayStreak == 1) "dzień" else "dni"} z rzędu", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Banner(vm: PuzzleVm) {
    val wrong = vm.status == Status.WRONG
    val solved = vm.status == Status.SOLVED && !vm.rushOn
    val color = when {
        wrong -> Red
        solved -> Green
        else -> Panel
    }
    Row(
        Modifier.fillMaxWidth().background(color).height(56.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.rushOn) {
            if (vm.status == Status.LOADING) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Green, strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Title("Pobieranie zadań…", null)
            } else {
                Text("⏱ ${mmss(vm.rushLeft)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("✓ ${vm.rushScore}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(16.dp))
                for (i in 0 until 3) {
                    Text("✕", color = if (i < vm.rushStrikes) (if (wrong) Color.White else Red) else Color(0x55FFFFFF), fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 3.dp))
                }
            }
        } else {
        val tag = when (vm.kind) {
            Kind.REVIEW -> "Powtórka · "
            Kind.DAILY -> "Zadanie dnia · "
            else -> ""
        }
        when (vm.status) {
            Status.LOADING -> {
                CircularProgressIndicator(Modifier.size(22.dp), color = Green, strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Title("Ładowanie zadania…", null)
            }
            Status.ERROR -> Title("Nie udało się pobrać zadania", vm.error)
            Status.WRONG -> { Mark("✕", Red); Title("Błędny ruch", "Spróbuj jeszcze raz") }
            Status.SOLVED -> {
                Mark("✓", Green)
                Title(
                    if (vm.reviewing) "Przegląd ${vm.viewIdx}/${vm.lineSize - 1}" else "Rozwiązane!",
                    tag + (vm.delta?.let { if (it > 0) "Ranking +$it" else "Ranking $it" } ?: "Strzałkami przejrzysz rozwiązanie"),
                )
            }
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
                    tag + if (ok) "Graj dalej" else "Znajdź najlepszy ruch",
                )
            }
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
        Text(t, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Info(vm: PuzzleVm, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!vm.rushOn) ChipText("${themeLabel(vm.angle)}  ▾", false, onClick = onMenu)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            vm.puzzle?.let {
                val time = if (!vm.rushOn && vm.status != Status.LOADING) " · ⏱ ${mmss(vm.seconds)}" else ""
                Text("#${it.id} · ${it.rating}$time", color = Muted, fontSize = 13.sp, maxLines = 1)
            }
            vm.syncError?.let { Text(it, color = Red, fontSize = 11.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun Actions(vm: PuzzleVm) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (vm.rushOn) {
            Btn("Zakończ", false, Modifier.weight(1f)) { vm.stopRush() }
        } else when (vm.status) {
            Status.PLAYING -> {
                Btn("💡 Podpowiedź", false, Modifier.weight(1f)) { vm.showHint() }
                Btn("Rozwiązanie", false, Modifier.weight(1f)) { vm.showSolution() }
                Btn("Pomiń", false, Modifier.weight(0.7f)) { vm.next() }
            }
            Status.WRONG -> {
                Btn("↺ Ponów", true, Modifier.weight(1f)) { vm.retry() }
                Btn("Rozwiązanie", false, Modifier.weight(1f)) { vm.showSolution() }
                Btn("Następne", false, Modifier.weight(1f)) { vm.next() }
            }
            Status.SOLVED -> {
                Btn("◀", false, Modifier.weight(0.6f)) { vm.stepView(-1) }
                Btn("▶", false, Modifier.weight(0.6f)) { vm.stepView(1) }
                Btn("Lichess ↗", false, Modifier.weight(1f)) {
                    vm.puzzle?.let {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lichess.org/training/${it.id}")))
                        } catch (_: Exception) {
                        }
                    }
                }
                Btn("Następne ›", true, Modifier.weight(1.5f)) { vm.next() }
            }
            Status.ERROR -> Btn("Spróbuj ponownie", true, Modifier.weight(1f)) { vm.next() }
            else -> Btn("…", false, Modifier.weight(1f)) {}
        }
    }
}

@Composable
fun Btn(text: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(if (primary) GreenDark else Color(0xFF2B2A28))
            .clickable(onClick = onClick).padding(bottom = 4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (primary) Green else Chip)
                .padding(vertical = 13.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ChipText(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = if (on) Color.White else Color(0xFFD6D5D3),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Green else Chip)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    )
}
