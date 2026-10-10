package pl.kacperpe.zadania

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
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

/** Wpis historii: czas, ranking po zadaniu, wynik, czy na koncie Lichess. */
class Entry(val t: Long, val r: Int, val w: Boolean, val l: Boolean)

class AuthException : IOException("Sesja Lichess wygasła. Zaloguj się ponownie.")

private const val API = "https://lichess.org"
private const val CLIENT = "pl.kacperpe.zadania"
private const val REDIRECT = "pl.kacperpe.zadania://oauth"
private const val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

class PuzzleVm(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("zadania", 0)
    private val seen = HashSet<String>()
    private var job: Job? = null
    private var step = 0
    private var scored = false
    private var hinted = false
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
    var streak by mutableIntStateOf(prefs.getInt("streak", 0)); private set
    var best by mutableIntStateOf(prefs.getInt("best", 0)); private set
    var delta by mutableStateOf<Int?>(null); private set
    var angle by mutableStateOf(prefs.getString("angle", "mix") ?: "mix"); private set
    var diff by mutableStateOf(prefs.getString("diff", "auto") ?: "auto"); private set

    private var localRating by mutableIntStateOf(prefs.getInt("rating", 1200))
    private var lichessRating by mutableIntStateOf(prefs.getInt("lrating", 1500))
    private var token by mutableStateOf(prefs.getString("token", null))
    var user by mutableStateOf(prefs.getString("user", null)); private set
    var loggingIn by mutableStateOf(false); private set
    var loginMsg by mutableStateOf<String?>(null); private set
    var syncError by mutableStateOf<String?>(null); private set
    val history = mutableStateListOf<Entry>()

    val logged get() = token != null
    val rating get() = if (logged) lichessRating else localRating
    val myTurn get() = status == Status.PLAYING && pos.white == playerWhite

    init {
        try {
            val a = JSONArray(prefs.getString("hist", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                history.add(Entry(o.getLong("t"), o.getInt("r"), o.getBoolean("w"), o.getBoolean("l")))
            }
        } catch (_: Exception) {
        }
        if (logged) refreshAccount()
        next()
    }

    // ---------- sieć ----------

    private fun http(method: String, url: String, body: String? = null, type: String? = null, auth: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 12000
        c.readTimeout = 12000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "ZadaniaSzachowe/1.1 (github.com/Kacperpe)")
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
            if (code !in 200..299) throw IOException("Lichess odpowiedział kodem $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

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

    private fun fetch(auth: String?): Puzzle {
        val j = JSONObject(http("GET", "$API/api/puzzle/next?angle=$angle&difficulty=${difficulty()}", auth = auth))
        val p = j.getJSONObject("puzzle")
        val sol = p.getJSONArray("solution")
        return Puzzle(
            id = p.getString("id"),
            rating = p.optInt("rating", 1500),
            san = j.getJSONObject("game").getString("pgn").trim().split(Regex("\\s+")).filter { it.isNotEmpty() },
            solution = List(sol.length()) { sol.getString(it) },
        )
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
                val tok = withContext(Dispatchers.IO) {
                    JSONObject(http("POST", "$API/api/token", form, "application/x-www-form-urlencoded"))
                        .getString("access_token")
                }
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
                val j = JSONObject(withContext(Dispatchers.IO) { http("GET", "$API/api/account", auth = tk) })
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
        prefs.edit().remove("token").remove("user").apply()
    }

    fun logout() {
        val tk = token ?: return
        clearLogin(null)
        viewModelScope.launch(Dispatchers.IO) {
            try { http("DELETE", "$API/api/token", auth = tk) } catch (_: Exception) {}
        }
    }

    // ---------- ustawienia ----------

    fun pickAngle(a: String) {
        if (a == angle) return
        angle = a
        prefs.edit().putString("angle", a).apply()
        next()
    }

    fun pickDiff(d: String) {
        diff = d
        prefs.edit().putString("diff", d).apply()
    }

    // ---------- zadanie ----------

    fun next() {
        job?.cancel()
        job = viewModelScope.launch {
            status = Status.LOADING
            error = null; badge = null; hint = -1; arrow = null; selected = -1; promo = null
            try {
                val tk = token
                var expired = false
                val pz = withContext(Dispatchers.IO) {
                    var auth = tk
                    var p = try { fetch(auth) } catch (e: AuthException) { expired = true; auth = null; fetch(null) }
                    var tries = 0
                    while (p.id in seen && tries++ < 2) p = fetch(auth)
                    p
                }
                if (expired) clearLogin(AuthException().message)
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
                step = 0; scored = false; hinted = false; delta = null; syncError = null
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
            record(false, true)
        }
    }

    private fun finish() {
        status = Status.SOLVED
        record(true, !hinted)
    }

    /** Zapisuje wynik zadania (raz na zadanie): lokalnie albo na koncie Lichess. */
    private fun record(win: Boolean, rated: Boolean) {
        if (scored) return
        scored = true
        val pz = puzzle ?: return
        if (!win) streak = 0 else if (rated) streak++
        if (streak > best) best = streak
        prefs.edit().putInt("streak", streak).putInt("best", best).apply()
        val tk = token
        if (tk == null) {
            if (rated) {
                val e = 1.0 / (1.0 + 10.0.pow((pz.rating - localRating) / 400.0))
                var d = (24 * ((if (win) 1.0 else 0.0) - e)).roundToInt()
                d = if (win) maxOf(d, 1) else minOf(d, -1)
                localRating += d
                delta = d
                prefs.edit().putInt("rating", localRating).apply()
            }
            addEntry(localRating, win, false)
            return
        }
        viewModelScope.launch {
            try {
                val body = JSONObject().put(
                    "solutions",
                    JSONArray().put(JSONObject().put("id", pz.id).put("win", win).put("rated", rated)),
                ).toString()
                val j = JSONObject(withContext(Dispatchers.IO) {
                    http("POST", "$API/api/puzzle/batch/$angle", body, "application/json", tk)
                })
                val d = j.optJSONArray("rounds")?.optJSONObject(0)?.optInt("ratingDiff", 0) ?: 0
                val g = j.optJSONObject("glicko")?.optDouble("rating", Double.NaN) ?: Double.NaN
                lichessRating = if (g.isNaN()) lichessRating + d else g.roundToInt()
                prefs.edit().putInt("lrating", lichessRating).apply()
                if (d != 0 && puzzle?.id == pz.id) delta = d
                syncError = null
            } catch (e: AuthException) {
                clearLogin(e.message)
                return@launch
            } catch (e: Exception) {
                syncError = "Nie zapisano wyniku na Lichess"
            }
            addEntry(lichessRating, win, true)
        }
    }

    private fun addEntry(r: Int, win: Boolean, lichess: Boolean) {
        history.add(Entry(System.currentTimeMillis(), r, win, lichess))
        while (history.size > 3000) history.removeAt(0)
        val a = JSONArray()
        for (e in history) a.put(JSONObject().put("t", e.t).put("r", e.r).put("w", e.w).put("l", e.l))
        prefs.edit().putString("hist", a.toString()).apply()
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
        hinted = true
    }

    fun showSolution() {
        val pz = puzzle ?: return
        if (status == Status.WRONG) revert() else if (!myTurn) return
        record(false, true)
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
