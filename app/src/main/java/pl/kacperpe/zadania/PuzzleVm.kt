package pl.kacperpe.zadania

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToInt

class Puzzle(
    val id: String,
    val rating: Int,
    val san: List<String>,
    val solution: List<String>,
    val themes: List<String>,
    val raw: String,
)

fun parsePuzzle(raw: String): Puzzle {
    val j = JSONObject(raw)
    val p = j.getJSONObject("puzzle")
    val sol = p.getJSONArray("solution")
    val th = p.optJSONArray("themes") ?: JSONArray()
    return Puzzle(
        id = p.getString("id"),
        rating = p.optInt("rating", 1500),
        san = j.getJSONObject("game").getString("pgn").trim().split(Regex("\\s+")).filter { it.isNotEmpty() },
        solution = List(sol.length()) { sol.getString(it) },
        themes = List(th.length()) { th.getString(it) },
        raw = raw,
    )
}

enum class Status { LOADING, PLAYING, WRONG, SHOWING, SOLVED, ERROR }
enum class Kind { NORMAL, REVIEW, DAILY, RUSH }

/** Nowa instancja = nowa animacja: przesuwane figury (z, do) i ewentualnie zbita figura. */
class Anim(val moves: List<Pair<Int, Int>>, val captured: Pair<Int, Char>?)

/** Wpis historii: czas, ranking po zadaniu, wynik, czy konto Lichess, sekundy namysłu. */
data class Entry(val t: Long, val r: Int, val w: Boolean, val l: Boolean, val s: Int)

class Review(val id: String, val raw: String, val due: Long)

class AuthException : IOException("Sesja Lichess wygasła. Zaloguj się ponownie.")

class HttpException(val code: Int) : IOException("Lichess odpowiedział kodem $code")

private const val API = "https://lichess.org"
private const val CLIENT = "pl.kacperpe.zadania"
private const val REDIRECT = "pl.kacperpe.zadania://oauth"
private const val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
private const val REVIEW_DELAY = 10 * 60 * 1000L

class PuzzleVm(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("zadania", 0)
    private val sfx = Sfx(app)
    private val net = Mutex()
    private val seen = HashSet<String>()
    private var job: Job? = null
    private var rushJob: Job? = null
    private var step = 0
    private var scored = false
    private var hinted = false
    private var undoPos: Position? = null
    private var undoLast: Move? = null
    private var solveStart = 0L
    private var lastWasReview = false
    private var line: List<Position> = emptyList()
    private var lineMoves: List<Move> = emptyList()
    private val cache = ArrayList<String>()
    private var refilling = false
    private var flushing = false
    private val pending = ArrayList<JSONObject>()
    private val rushQueue = ArrayList<Puzzle>()
    private var rushEnd = 0L

    var pos by mutableStateOf(Position.start()); private set
    var status by mutableStateOf(Status.LOADING); private set
    var kind by mutableStateOf(Kind.NORMAL); private set
    var puzzle by mutableStateOf<Puzzle?>(null); private set
    var playerWhite by mutableStateOf(true); private set
    var lastMove by mutableStateOf<Move?>(null); private set
    var anim by mutableStateOf<Anim?>(null); private set
    var selected by mutableIntStateOf(-1); private set
    var hint by mutableIntStateOf(-1); private set
    var arrow by mutableStateOf<Move?>(null); private set
    var badge by mutableStateOf<Pair<Int, Boolean>?>(null); private set
    var promo by mutableStateOf<Pair<Int, Int>?>(null); private set
    var premove by mutableStateOf<Pair<Int, Int>?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var seconds by mutableIntStateOf(0); private set
    var viewIdx by mutableIntStateOf(-1); private set
    var streak by mutableIntStateOf(prefs.getInt("streak", 0)); private set
    var best by mutableIntStateOf(prefs.getInt("best", 0)); private set
    var delta by mutableStateOf<Int?>(null); private set

    // ustawienia
    var angle by mutableStateOf(prefs.getString("angle", "mix") ?: "mix"); private set
    var diff by mutableStateOf(prefs.getString("diff", "auto") ?: "auto"); private set
    var sound by mutableStateOf(prefs.getBoolean("sound", true)); private set
    var vib by mutableStateOf(prefs.getBoolean("vib", true)); private set
    var reviewOn by mutableStateOf(prefs.getBoolean("reviewOn", true)); private set
    var remind by mutableStateOf(prefs.getBoolean("remind", false)); private set
    var goal by mutableIntStateOf(prefs.getInt("goal", 10)); private set
    var boardTheme by mutableIntStateOf(prefs.getInt("board", 0)); private set
    var pieceSet by mutableStateOf(prefs.getString("pieces", "cburnett") ?: "cburnett"); private set

    // konto
    private var localRating by mutableIntStateOf(prefs.getInt("rating", 1200))
    private var lichessRating by mutableIntStateOf(prefs.getInt("lrating", 1500))
    private var token by mutableStateOf(prefs.getString("token", null))
    var user by mutableStateOf(prefs.getString("user", null)); private set
    var loggingIn by mutableStateOf(false); private set
    var loginMsg by mutableStateOf<String?>(null); private set
    var syncError by mutableStateOf<String?>(null); private set

    // postęp
    val history = mutableStateListOf<Entry>()
    val themeWins = mutableStateMapOf<String, Int>()
    val themeTotal = mutableStateMapOf<String, Int>()
    val reviews = mutableStateListOf<Review>()
    private var day = prefs.getLong("day", 0)
    private var lastGoalDay by mutableStateOf(prefs.getLong("lastGoalDay", 0))
    private var dayStreakRaw by mutableIntStateOf(prefs.getInt("dayStreak", 0))
    var dayCount by mutableIntStateOf(prefs.getInt("dayCount", 0)); private set
    var cached by mutableIntStateOf(0); private set

    // tryb na czas
    var rushOn by mutableStateOf(false); private set
    var rushOver by mutableStateOf(false); private set
    var rushScore by mutableIntStateOf(0); private set
    var rushStrikes by mutableIntStateOf(0); private set
    var rushLeft by mutableIntStateOf(0); private set
    var rushMin by mutableIntStateOf(3); private set
    var rushBest3 by mutableIntStateOf(prefs.getInt("rushBest3", 0)); private set
    var rushBest5 by mutableIntStateOf(prefs.getInt("rushBest5", 0)); private set

    val logged get() = token != null
    val rating get() = if (logged) lichessRating else localRating
    val myTurn get() = status == Status.PLAYING && pos.white == playerWhite && !rushOver
    private val awaiting get() = status == Status.PLAYING && pos.white != playerWhite && !rushOver
    val dayStreak get() = if (lastGoalDay >= today() - 1) dayStreakRaw else 0
    val reviewing get() = status == Status.SOLVED && viewIdx >= 0
    val lineSize get() = line.size

    init {
        sfx.sound = sound; sfx.vib = vib
        try {
            val a = JSONArray(prefs.getString("hist", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                history.add(Entry(o.getLong("t"), o.getInt("r"), o.getBoolean("w"), o.getBoolean("l"), o.optInt("s", 0)))
            }
            val th = JSONObject(prefs.getString("themes", "{}") ?: "{}")
            for (k in th.keys()) {
                val v = th.getJSONArray(k)
                themeWins[k] = v.getInt(0); themeTotal[k] = v.getInt(1)
            }
            val rv = JSONArray(prefs.getString("reviews", "[]"))
            for (i in 0 until rv.length()) {
                val o = rv.getJSONObject(i)
                reviews.add(Review(o.getString("id"), o.getString("raw"), o.getLong("due")))
            }
            val pd = JSONArray(prefs.getString("pending", "[]"))
            for (i in 0 until pd.length()) pending.add(pd.getJSONObject(i))
            if (prefs.getString("cacheKey", null) == cacheKey()) {
                val c = JSONArray(prefs.getString("cache", "[]"))
                for (i in 0 until c.length()) cache.add(c.getString(i))
            }
        } catch (_: Exception) {
        }
        cached = cache.size
        rollDay()
        if (remind) Reminder.schedule(app)
        if (logged) { refreshAccount(); flush() }
        viewModelScope.launch {
            while (true) {
                delay(250)
                val now = SystemClock.elapsedRealtime()
                if (rushOn && !rushOver && rushEnd > 0) {
                    rushLeft = ((rushEnd - now + 999) / 1000).toInt().coerceAtLeast(0)
                    if (rushLeft == 0) endRush()
                } else if (status == Status.PLAYING || status == Status.WRONG) {
                    seconds = ((now - solveStart) / 1000).toInt()
                }
            }
        }
        next()
    }

    override fun onCleared() { sfx.release() }

    // ---------- sieć ----------

    private fun http(method: String, url: String, body: String?, type: String?, auth: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 12000
        c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "ZadaniaSzachowe/1.2 (github.com/Kacperpe)")
        if (auth != null) c.setRequestProperty("Authorization", "Bearer $auth")
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            if (code == 429) throw IOException("Za dużo zapytań do Lichess. Odczekaj minutę.")
            if (code == 401 && auth != null) throw AuthException()
            if (code !in 200..299) throw HttpException(code)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Lichess prosi o jedno zapytanie naraz, więc wszystko idzie przez jedną kolejkę. */
    private suspend fun call(method: String, path: String, body: String? = null, type: String? = null, auth: String? = null): String =
        net.withLock { withContext(Dispatchers.IO) { http(method, API + path, body, type, auth) } }

    private fun difficulty(): String = when {
        diff != "auto" -> diff
        logged -> "normal"
        else -> when (localRating - 1500) {
            in Int.MIN_VALUE until -450 -> "easiest"
            in -450 until -150 -> "easier"
            in -150 until 150 -> "normal"
            in 150 until 450 -> "harder"
            else -> "hardest"
        }
    }

    private fun cacheKey() = "$angle|${difficulty()}|$logged"

    private fun saveCache() {
        cached = cache.size
        prefs.edit().putString("cache", JSONArray(cache).toString()).putString("cacheKey", cacheKey()).apply()
    }

    private suspend fun batch(angle: String, difficulty: String, nb: Int): List<String> {
        val path = "/api/puzzle/batch/$angle?nb=$nb&difficulty=$difficulty"
        try {
            val res = try {
                call("GET", path, auth = token)
            } catch (e: AuthException) {
                clearLogin(e.message)
                call("GET", path)
            }
            val arr = JSONObject(res).getJSONArray("puzzles")
            if (arr.length() > 0) return List(arr.length()) { arr.getJSONObject(it).toString() }
        } catch (e: HttpException) {
            // paczka niedostępna: niżej pobieramy pojedynczo
        }
        val one = "/api/puzzle/next?angle=$angle&difficulty=$difficulty"
        val out = ArrayList<String>()
        for (i in 0 until minOf(nb, 5)) {
            try {
                out.add(call("GET", one, auth = token))
            } catch (e: IOException) {
                if (out.isEmpty()) throw e
                break
            }
        }
        return out
    }

    private suspend fun refill() {
        if (refilling) return
        refilling = true
        try {
            val key = cacheKey()
            val got = batch(angle, difficulty(), 30)
            if (key == cacheKey()) {
                val have = cache.map { parsePuzzle(it).id }.toHashSet()
                for (raw in got) {
                    val id = parsePuzzle(raw).id
                    if (id !in seen && have.add(id)) cache.add(raw)
                }
                saveCache()
            }
        } finally {
            refilling = false
        }
    }

    private suspend fun takeCached(): Puzzle {
        if (prefs.getString("cacheKey", null) != cacheKey()) { cache.clear(); saveCache() }
        cache.removeAll { parsePuzzle(it).id in seen }
        if (cache.isEmpty()) refill()
        if (cache.isEmpty()) throw IOException("Brak nowych zadań dla tego motywu")
        val pz = parsePuzzle(cache.removeAt(0))
        saveCache()
        if (cache.size < 10) viewModelScope.launch { try { refill() } catch (_: Exception) {} }
        return pz
    }

    // ---------- konto Lichess ----------

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun loginUrl(): String {
        val rnd = SecureRandom()
        fun rand(n: Int) = Base64.encodeToString(ByteArray(n).also { rnd.nextBytes(it) }, B64)
        val verifier = rand(48)
        val state = rand(12)
        prefs.edit().putString("pkce", verifier).putString("state", state).apply()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)), B64,
        )
        loginMsg = null
        return "$API/oauth?response_type=code&client_id=$CLIENT&redirect_uri=${enc(REDIRECT)}" +
            "&code_challenge_method=S256&code_challenge=$challenge" +
            "&scope=${enc("puzzle:read puzzle:write")}&state=$state"
    }

    fun handleRedirect(uri: Uri) {
        if (uri.scheme != CLIENT) return
        val verifier = prefs.getString("pkce", null) ?: return
        val code = uri.getQueryParameter("code")
        val ok = code != null && uri.getQueryParameter("state") == prefs.getString("state", null)
        prefs.edit().remove("pkce").remove("state").apply()
        if (!ok) { loginMsg = "Logowanie anulowane"; return }
        loggingIn = true
        viewModelScope.launch {
            try {
                val form = "grant_type=authorization_code&code=${enc(code!!)}&code_verifier=$verifier" +
                    "&redirect_uri=${enc(REDIRECT)}&client_id=$CLIENT"
                val tok = JSONObject(call("POST", "/api/token", form, "application/x-www-form-urlencoded"))
                    .getString("access_token")
                token = tok
                prefs.edit().putString("token", tok).apply()
                delta = null
                loginMsg = null
                refreshAccount()
            } catch (e: Exception) {
                loginMsg = "Logowanie nie powiodło się: ${e.message ?: "błąd sieci"}"
            } finally {
                loggingIn = false
            }
        }
    }

    private fun refreshAccount() {
        val tk = token ?: return
        viewModelScope.launch {
            try {
                val j = JSONObject(call("GET", "/api/account", auth = tk))
                user = j.optString("username").ifEmpty { null }
                j.optJSONObject("perfs")?.optJSONObject("puzzle")?.let {
                    if (it.has("rating")) lichessRating = it.getInt("rating")
                }
                prefs.edit().putString("user", user).putInt("lrating", lichessRating).apply()
            } catch (e: AuthException) {
                clearLogin(e.message)
            } catch (_: Exception) {
            }
        }
    }

    private fun clearLogin(msg: String?) {
        token = null; user = null; delta = null; syncError = null
        loginMsg = msg
        pending.clear()
        prefs.edit().remove("token").remove("user").remove("pending").apply()
    }

    fun logout() {
        val tk = token ?: return
        clearLogin(null)
        viewModelScope.launch {
            try { call("DELETE", "/api/token", auth = tk) } catch (_: Exception) {}
        }
    }

    /** Wysyła na Lichess wyniki czekające w kolejce (także te zebrane offline). */
    private fun flush() {
        val tk = token ?: return
        if (flushing || pending.isEmpty()) return
        flushing = true
        viewModelScope.launch {
            try {
                val sent = pending.toList()
                for ((ang, items) in sent.groupBy { it.optString("angle", "mix") }) {
                    val sol = JSONArray()
                    for (o in items) sol.put(JSONObject().put("id", o.getString("id")).put("win", o.getBoolean("win")).put("rated", o.getBoolean("rated")))
                    val j = JSONObject(call("POST", "/api/puzzle/batch/$ang", JSONObject().put("solutions", sol).toString(), "application/json", tk))
                    pending.removeAll(items)
                    savePending()
                    val rounds = j.optJSONArray("rounds")
                    var total = 0
                    if (rounds != null) for (i in 0 until rounds.length()) {
                        val r = rounds.getJSONObject(i)
                        val d = r.optInt("ratingDiff", 0)
                        total += d
                        if (d != 0 && r.optString("id") == puzzle?.id) delta = d
                    }
                    val g = j.optJSONObject("glicko")?.optDouble("rating", Double.NaN) ?: Double.NaN
                    lichessRating = if (g.isNaN()) lichessRating + total else g.roundToInt()
                    prefs.edit().putInt("lrating", lichessRating).apply()
                    val i = history.indexOfLast { it.l }
                    if (i >= 0) { history[i] = history[i].copy(r = lichessRating); saveHistory() }
                }
                syncError = null
            } catch (e: AuthException) {
                clearLogin(e.message)
            } catch (e: Exception) {
                syncError = "Wynik zapisze się na Lichess po powrocie sieci"
            } finally {
                flushing = false
            }
        }
    }

    private fun savePending() = prefs.edit().putString("pending", JSONArray(pending).toString()).apply()

    // ---------- ustawienia ----------

    fun pickAngle(a: String) {
        if (a == angle && !rushOn) return
        angle = a
        prefs.edit().putString("angle", a).apply()
        exitRush(false)
        next()
    }

    fun pickDiff(d: String) { diff = d; prefs.edit().putString("diff", d).apply() }
    fun pickSound(v: Boolean) { sound = v; sfx.sound = v; prefs.edit().putBoolean("sound", v).apply(); if (v) sfx.play("move") }
    fun pickVib(v: Boolean) { vib = v; sfx.vib = v; prefs.edit().putBoolean("vib", v).apply(); if (v) sfx.buzz(30) }
    fun pickReview(v: Boolean) { reviewOn = v; prefs.edit().putBoolean("reviewOn", v).apply() }
    fun pickGoal(g: Int) { goal = g; prefs.edit().putInt("goal", g).apply() }
    fun pickBoard(i: Int) { boardTheme = i; prefs.edit().putInt("board", i).apply() }
    fun pickPieces(s: String) { pieceSet = s; prefs.edit().putString("pieces", s).apply() }

    fun pickRemind(v: Boolean) {
        remind = v
        prefs.edit().putBoolean("remind", v).apply()
        if (v) Reminder.schedule(getApplication<Application>()) else Reminder.cancel(getApplication<Application>())
    }

    // ---------- cel dnia ----------

    private fun today() = LocalDate.now().toEpochDay()

    private fun rollDay() {
        val d = today()
        if (d != day) {
            day = d; dayCount = 0
            prefs.edit().putLong("day", d).putInt("dayCount", 0).apply()
        }
    }

    private fun countDay() {
        rollDay()
        dayCount++
        if (dayCount >= goal && lastGoalDay != day) {
            dayStreakRaw = if (lastGoalDay == day - 1) dayStreakRaw + 1 else 1
            lastGoalDay = day
        }
        prefs.edit().putInt("dayCount", dayCount).putInt("dayStreak", dayStreakRaw).putLong("lastGoalDay", lastGoalDay).apply()
    }

    // ---------- ładowanie zadania ----------

    private fun resetBoardState() {
        error = null; badge = null; hint = -1; arrow = null; selected = -1; promo = null; premove = null
    }

    fun next() {
        if (rushOn) return
        job?.cancel()
        job = viewModelScope.launch {
            status = Status.LOADING
            resetBoardState()
            try {
                val now = System.currentTimeMillis()
                val due = if (reviewOn && !lastWasReview) reviews.firstOrNull { it.due <= now } else null
                lastWasReview = due != null
                if (due != null) start(parsePuzzle(due.raw), Kind.REVIEW)
                else start(takeCached(), Kind.NORMAL)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
        flush()
    }

    fun loadDaily() {
        exitRush(false)
        job?.cancel()
        job = viewModelScope.launch {
            status = Status.LOADING
            resetBoardState()
            try {
                start(parsePuzzle(call("GET", "/api/puzzle/daily")), Kind.DAILY)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private fun fail(e: Exception) {
        error = if (e is IOException && e.message != null && e !is java.net.UnknownHostException &&
            e !is java.net.SocketTimeoutException && e !is java.net.ConnectException
        ) e.message else "Brak połączenia z internetem i brak pobranych zadań"
        status = Status.ERROR
    }

    private suspend fun start(pz: Puzzle, k: Kind) {
        var p = Position.start()
        var prev = p
        var last: Move? = null
        for (s in pz.san) {
            val m = p.fromSan(s) ?: throw IOException("Nie udało się odczytać zadania ${pz.id}")
            prev = p; last = m; p = p.make(m)
        }
        val ps = ArrayList<Position>()
        val ms = ArrayList<Move>()
        if (last != null) { ps.add(prev); ms.add(last) }
        ps.add(p)
        var q = p
        for (u in pz.solution) {
            val m = q.fromUci(u) ?: throw IOException("Uszkodzone zadanie ${pz.id}")
            ms.add(m); q = q.make(m); ps.add(q)
        }
        if (pz.solution.isEmpty()) throw IOException("Uszkodzone zadanie ${pz.id}")
        line = ps; lineMoves = ms
        puzzle = pz; kind = k
        step = 0; scored = false; hinted = false; delta = null; viewIdx = -1
        undoPos = null
        resetBoardState()
        playerWhite = p.white
        anim = null; lastMove = null
        pos = ps[0]
        seen.add(pz.id)
        delay(if (k == Kind.RUSH) 250 else 650)
        if (last != null) play(last)
        solveStart = SystemClock.elapsedRealtime()
        seconds = 0
        status = Status.PLAYING
    }

    private fun animFor(before: Position, m: Move): Anim {
        val moves = ArrayList<Pair<Int, Int>>()
        moves.add(m.from to m.to)
        val c = before.b[m.from]
        if ((c == 'K' || c == 'k') && kotlin.math.abs(m.to - m.from) == 2) {
            moves.add(if (m.to > m.from) (m.to + 1) to (m.to - 1) else (m.to - 2) to (m.to + 1))
        }
        var cap: Pair<Int, Char>? = null
        if (before.b[m.to] != '.') cap = m.to to before.b[m.to]
        else if ((c == 'P' || c == 'p') && m.from % 8 != m.to % 8) {
            val sq = m.to + if (c == 'P') -8 else 8
            cap = sq to before.b[sq]
        }
        return Anim(moves, cap)
    }

    private fun play(m: Move, animate: Boolean = true, quiet: Boolean = false) {
        val a = animFor(pos, m)
        pos = pos.make(m)
        lastMove = m
        anim = if (animate) a else null
        if (!quiet) sfx.play(if (pos.inCheck()) "check" else if (a.captured != null) "capture" else "move")
    }

    // ---------- ruchy gracza ----------

    private fun own(sq: Int): Boolean {
        val c = pos.b[sq]
        return c != '.' && c.isUpperCase() == playerWhite
    }

    fun tap(sq: Int) {
        if (awaiting) {
            if (selected >= 0 && selected != sq) { premove = selected to sq; selected = -1 }
            else if (own(sq) && selected != sq) { selected = sq; premove = null }
            else { selected = -1; premove = null }
            return
        }
        if (!myTurn) return
        if (selected >= 0 && selected != sq && tryMove(selected, sq, true)) return
        selected = if (own(sq) && selected != sq) sq else -1
    }

    fun canPick(sq: Int) = (myTurn || awaiting) && own(sq)

    fun select(sq: Int) { selected = sq }

    fun targets(): List<Int> =
        if (selected >= 0 && myTurn) pos.legalMoves().filter { it.from == selected }.map { it.to }.distinct()
        else emptyList()

    fun tryMove(from: Int, to: Int, animate: Boolean): Boolean {
        if (awaiting) {
            selected = -1
            if (from != to) premove = from to to
            return true
        }
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

    private fun runPremove() {
        val (f, t) = premove ?: return
        premove = null
        if (!myTurn) return
        val c = pos.legalMoves().filter { it.from == f && it.to == t }
        if (c.isEmpty()) return
        commit(c.firstOrNull { it.promo == 'q' } ?: c[0], true)
    }

    private fun commit(m: Move, animate: Boolean) {
        val pz = puzzle ?: return
        val before = pos
        val beforeLast = lastMove
        val expected = before.fromUci(pz.solution[step])
        val mate = before.make(m).isMate()
        val good = m == expected || mate
        hint = -1; arrow = null
        play(m, animate, quiet = !good)
        if (good) {
            badge = m.to to true
            step++
            if (mate || step >= pz.solution.size) finish()
            else job = viewModelScope.launch {
                delay(if (kind == Kind.RUSH) 350 else 600)
                if (rushOver) return@launch
                pos.fromUci(pz.solution[step])?.let { play(it); step++ }
                badge = null
                if (step >= pz.solution.size) finish() else runPremove()
            }
        } else {
            badge = m.to to false
            sfx.play("wrong"); sfx.buzz(70)
            status = Status.WRONG
            if (kind == Kind.RUSH) {
                rushStrikes++
                if (rushStrikes >= 3) endRush()
                else job = viewModelScope.launch { delay(750); nextRush() }
            } else {
                undoPos = before; undoLast = beforeLast
                record(false)
            }
        }
    }

    private fun finish() {
        status = Status.SOLVED
        premove = null
        sfx.play("ok"); sfx.buzz(25)
        if (kind == Kind.RUSH) {
            rushScore++
            job = viewModelScope.launch { delay(450); nextRush() }
        } else {
            record(true)
        }
    }

    // ---------- wynik ----------

    /** Zapisuje wynik zadania (raz na zadanie): ranking, historia, motywy, powtórki, Lichess. */
    private fun record(win: Boolean) {
        if (scored) return
        scored = true
        val pz = puzzle ?: return
        val rated = kind == Kind.NORMAL && !(win && hinted)
        val clean = win && !hinted
        val secs = ((SystemClock.elapsedRealtime() - solveStart) / 1000).toInt()

        if (!win) streak = 0 else if (clean) streak++
        if (streak > best) best = streak
        prefs.edit().putInt("streak", streak).putInt("best", best).apply()

        for (t in pz.themes) {
            themeTotal[t] = (themeTotal[t] ?: 0) + 1
            if (clean) themeWins[t] = (themeWins[t] ?: 0) + 1
        }
        val th = JSONObject()
        for ((k, v) in themeTotal) th.put(k, JSONArray().put(themeWins[k] ?: 0).put(v))
        prefs.edit().putString("themes", th.toString()).apply()

        reviews.removeAll { it.id == pz.id }
        if (!clean) reviews.add(Review(pz.id, pz.raw, System.currentTimeMillis() + REVIEW_DELAY))
        val rv = JSONArray()
        for (r in reviews.takeLast(200)) rv.put(JSONObject().put("id", r.id).put("raw", r.raw).put("due", r.due))
        prefs.edit().putString("reviews", rv.toString()).apply()

        countDay()

        if (!logged) {
            if (rated) {
                val e = 1.0 / (1.0 + 10.0.pow((pz.rating - localRating) / 400.0))
                var d = (24 * ((if (win) 1.0 else 0.0) - e)).roundToInt()
                d = if (win) maxOf(d, 1) else minOf(d, -1)
                localRating += d
                delta = d
                prefs.edit().putInt("rating", localRating).apply()
            }
            history.add(Entry(System.currentTimeMillis(), localRating, clean, false, secs))
        } else {
            history.add(Entry(System.currentTimeMillis(), lichessRating, clean, true, secs))
            if (kind != Kind.REVIEW) {
                pending.add(JSONObject().put("id", pz.id).put("win", win).put("rated", rated).put("angle", angle))
                savePending()
                flush()
            }
        }
        while (history.size > 3000) history.removeAt(0)
        saveHistory()
    }

    private fun saveHistory() {
        val a = JSONArray()
        for (e in history) a.put(JSONObject().put("t", e.t).put("r", e.r).put("w", e.w).put("l", e.l).put("s", e.s))
        prefs.edit().putString("hist", a.toString()).apply()
    }

    // ---------- po zadaniu ----------

    private fun revert() {
        undoPos?.let { pos = it; lastMove = undoLast; anim = null }
        undoPos = null
        badge = null; selected = -1
    }

    fun retry() {
        if (status != Status.WRONG || kind == Kind.RUSH) return
        revert()
        status = Status.PLAYING
    }

    fun showHint() {
        if (!myTurn || kind == Kind.RUSH) return
        val pz = puzzle ?: return
        val m = pos.fromUci(pz.solution[step]) ?: return
        if (hint == m.from) arrow = m
        hint = m.from
        selected = -1
        hinted = true
    }

    fun showSolution() {
        val pz = puzzle ?: return
        if (kind == Kind.RUSH) return
        if (status == Status.WRONG) revert() else if (!myTurn) return
        record(false)
        hint = -1; arrow = null; premove = null
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

    /** Przegląd rozwiązanego zadania ruch po ruchu. */
    fun stepView(d: Int) {
        if (status != Status.SOLVED || kind == Kind.RUSH || line.isEmpty()) return
        val n = line.size - 1
        val cur = if (viewIdx < 0) n else viewIdx
        val ni = (cur + d).coerceIn(0, n)
        if (ni == cur && viewIdx >= 0) return
        viewIdx = ni
        badge = null
        anim = if (d > 0 && ni > 0 && ni == cur + 1) animFor(line[ni - 1], lineMoves[ni - 1]) else null
        pos = line[ni]
        lastMove = if (ni > 0) lineMoves[ni - 1] else null
        if (ni != cur) sfx.play("move")
    }

    // ---------- tryb na czas ----------

    fun startRush(minutes: Int) {
        job?.cancel(); rushJob?.cancel()
        rushOn = true; rushOver = false
        rushScore = 0; rushStrikes = 0
        rushMin = minutes; rushLeft = minutes * 60; rushEnd = 0
        rushQueue.clear()
        kind = Kind.RUSH
        status = Status.LOADING
        resetBoardState()
        rushJob = viewModelScope.launch {
            try {
                for (d in listOf("easiest", "easier", "normal", "harder", "hardest")) {
                    val got = batch("mix", d, 15).map { parsePuzzle(it) }.sortedBy { it.rating }
                    rushQueue.addAll(got)
                    if (rushEnd == 0L) {
                        rushEnd = SystemClock.elapsedRealtime() + minutes * 60000L
                        nextRush()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (rushEnd == 0L) { rushOn = false; kind = Kind.NORMAL; fail(e) }
            }
        }
    }

    private fun nextRush() {
        if (!rushOn || rushOver) return
        val pz = rushQueue.removeFirstOrNull()
        if (pz == null) { endRush(); return }
        job?.cancel()
        job = viewModelScope.launch {
            try {
                start(pz, Kind.RUSH)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                nextRush()
            }
        }
    }

    private fun endRush() {
        if (!rushOn || rushOver) return
        rushOver = true
        premove = null; selected = -1
        rushJob?.cancel()
        if (rushMin == 3 && rushScore > rushBest3) { rushBest3 = rushScore; prefs.edit().putInt("rushBest3", rushScore).apply() }
        if (rushMin == 5 && rushScore > rushBest5) { rushBest5 = rushScore; prefs.edit().putInt("rushBest5", rushScore).apply() }
    }

    fun stopRush() = endRush()

    fun exitRush(loadNext: Boolean = true) {
        if (!rushOn) return
        job?.cancel(); rushJob?.cancel()
        rushOn = false; rushOver = false
        kind = Kind.NORMAL
        if (loadNext) next()
    }
}
