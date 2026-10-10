package pl.kacperpe.zadania

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow
import kotlin.math.roundToInt

data class Puzzle(
    val id: String,
    val rating: Int,
    val san: List<String>,
    val solution: List<String>,
)

enum class Status { LOADING, PLAYING, WRONG, SHOWING, SOLVED, ERROR }

/** Nowa instancja = nowa animacja ruchu na planszy. */
class Anim(val move: Move)

class PuzzleVm(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("zadania", 0)
    private val seen = HashSet<String>()
    private var job: Job? = null
    private var step = 0
    private var failed = false
    private var undoPos: Position? = null
    private var undoLast: Move? = null

    var pos by mutableStateOf(Position.start()); private set
    var status by mutableStateOf(Status.LOADING); private set
    var puzzle by mutableStateOf<Puzzle?>(null); private set
    var playerWhite by mutableStateOf(true); private set
    var lastMove by mutableStateOf<Move?>(null); private set
    var anim by mutableStateOf<Anim?>(null); private set
    var selected by mutableIntStateOf(-1); private set
    var hint by mutableIntStateOf(-1); private set
    var arrow by mutableStateOf<Move?>(null); private set
    var badge by mutableStateOf<Pair<Int, Boolean>?>(null); private set
    var promo by mutableStateOf<Pair<Int, Int>?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var rating by mutableIntStateOf(prefs.getInt("rating", 1200)); private set
    var streak by mutableIntStateOf(prefs.getInt("streak", 0)); private set
    var delta by mutableStateOf<Int?>(null); private set
    var angle by mutableStateOf(prefs.getString("angle", "mix") ?: "mix"); private set

    init { next() }

    val myTurn get() = status == Status.PLAYING && pos.white == playerWhite

    fun pickAngle(a: String) {
        if (a == angle) return
        angle = a
        prefs.edit().putString("angle", a).apply()
        next()
    }

    private fun difficulty() = when (rating - 1500) {
        in Int.MIN_VALUE until -450 -> "easiest"
        in -450 until -150 -> "easier"
        in -150 until 150 -> "normal"
        in 150 until 450 -> "harder"
        else -> "hardest"
    }

    private fun fetch(): Puzzle {
        val url = "https://lichess.org/api/puzzle/next?angle=$angle&difficulty=${difficulty()}"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 12000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "ZadaniaSzachowe/1.0 (github.com/Kacperpe)")
        try {
            val code = c.responseCode
            if (code == 429) throw IOException("Za dużo zapytań do Lichess. Odczekaj minutę.")
            if (code != 200) throw IOException("Lichess odpowiedział kodem $code")
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val p = j.getJSONObject("puzzle")
            val sol = p.getJSONArray("solution")
            return Puzzle(
                id = p.getString("id"),
                rating = p.optInt("rating", 1500),
                san = j.getJSONObject("game").getString("pgn").trim().split(Regex("\\s+")).filter { it.isNotEmpty() },
                solution = List(sol.length()) { sol.getString(it) },
            )
        } finally {
            c.disconnect()
        }
    }

    fun next() {
        job?.cancel()
        job = viewModelScope.launch {
            status = Status.LOADING
            error = null; badge = null; hint = -1; arrow = null; selected = -1; promo = null
            try {
                val pz = withContext(Dispatchers.IO) {
                    var p = fetch()
                    var tries = 0
                    while (p.id in seen && tries++ < 2) p = fetch()
                    p
                }
                seen.add(pz.id)
                var p = Position.start()
                var prev = p
                var last: Move? = null
                for (s in pz.san) {
                    val m = p.fromSan(s) ?: throw IOException("Nie udało się odczytać zadania ${pz.id}")
                    prev = p; last = m; p = p.make(m)
                }
                if (pz.solution.isEmpty() || p.fromUci(pz.solution[0]) == null) {
                    throw IOException("Uszkodzone zadanie ${pz.id}")
                }
                puzzle = pz
                step = 0; failed = false; delta = null
                playerWhite = p.white
                anim = null; lastMove = null
                pos = prev
                delay(650)
                if (last != null) play(last)
                status = Status.PLAYING
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = if (e is IOException && e.message != null && e !is java.net.UnknownHostException) e.message
                else "Brak połączenia z internetem"
                status = Status.ERROR
            }
        }
    }

    private fun play(m: Move, animate: Boolean = true) {
        pos = pos.make(m)
        lastMove = m
        anim = if (animate) Anim(m) else null
    }

    fun tap(sq: Int) {
        if (!myTurn) return
        val c = pos.b[sq]
        val own = c != '.' && c.isUpperCase() == playerWhite
        if (selected >= 0 && selected != sq && tryMove(selected, sq, true)) return
        selected = if (own && selected != sq) sq else -1
    }

    fun canPick(sq: Int): Boolean {
        val c = pos.b[sq]
        return myTurn && c != '.' && c.isUpperCase() == playerWhite
    }

    fun select(sq: Int) { selected = sq }

    fun targets(): List<Int> =
        if (selected >= 0 && myTurn) pos.legalMoves().filter { it.from == selected }.map { it.to }.distinct()
        else emptyList()

    fun tryMove(from: Int, to: Int, animate: Boolean): Boolean {
        if (!myTurn) return false
        val c = pos.legalMoves().filter { it.from == from && it.to == to }
        if (c.isEmpty()) return false
        selected = -1
        if (c.size > 1) promo = from to to else commit(c[0], animate)
        return true
    }

    fun promote(p: Char?) {
        val (f, t) = promo ?: return
        promo = null
        if (p != null) commit(Move(f, t, p), true)
    }

    private fun commit(m: Move, animate: Boolean) {
        val pz = puzzle ?: return
        val before = pos
        val beforeLast = lastMove
        val expected = before.fromUci(pz.solution[step])
        hint = -1; arrow = null
        play(m, animate)
        val mate = pos.isMate()
        if (m == expected || mate) {
            badge = m.to to true
            step++
            if (mate || step >= pz.solution.size) finish()
            else job = viewModelScope.launch {
                delay(600)
                pos.fromUci(pz.solution[step])?.let { play(it); step++ }
                badge = null
                if (step >= pz.solution.size) finish()
            }
        } else {
            badge = m.to to false
            undoPos = before; undoLast = beforeLast
            status = Status.WRONG
            if (!failed) { failed = true; rate(false) }
        }
    }

    private fun finish() {
        status = Status.SOLVED
        if (!failed) rate(true)
    }

    private fun rate(win: Boolean) {
        val pr = puzzle?.rating ?: return
        val e = 1.0 / (1.0 + 10.0.pow((pr - rating) / 400.0))
        var d = (24 * ((if (win) 1.0 else 0.0) - e)).roundToInt()
        d = if (win) maxOf(d, 1) else minOf(d, -1)
        rating += d
        delta = d
        streak = if (win) streak + 1 else 0
        prefs.edit().putInt("rating", rating).putInt("streak", streak).apply()
    }

    private fun revert() {
        undoPos?.let { pos = it; lastMove = undoLast; anim = null }
        undoPos = null
        badge = null; selected = -1
    }

    fun retry() {
        if (status != Status.WRONG) return
        revert()
        status = Status.PLAYING
    }

    fun showHint() {
        if (!myTurn) return
        val pz = puzzle ?: return
        val m = pos.fromUci(pz.solution[step]) ?: return
        if (hint == m.from) arrow = m
        hint = m.from
        selected = -1
        if (!failed) { failed = true; streak = 0; prefs.edit().putInt("streak", 0).apply() }
    }

    fun showSolution() {
        val pz = puzzle ?: return
        if (status == Status.WRONG) revert() else if (!myTurn) return
        if (!failed) { failed = true; rate(false) }
        hint = -1; arrow = null
        status = Status.SHOWING
        job = viewModelScope.launch {
            while (step < pz.solution.size) {
                delay(750)
                val m = pos.fromUci(pz.solution[step]) ?: break
                play(m); step++
            }
            status = Status.SOLVED
        }
    }
}
