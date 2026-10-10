package pl.kacperpe.zadania

/** Pole = rank * 8 + file (a1 = 0, h8 = 63). Białe figury wielkimi literami, '.' = puste. */
data class Move(val from: Int, val to: Int, val promo: Char? = null)

fun sqName(s: Int) = "${'a' + s % 8}${'1' + s / 8}"

class Position(
    val b: CharArray,
    val white: Boolean,
    val castle: String,
    val ep: Int,
) {
    companion object {
        fun start() = fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

        fun fromFen(fen: String): Position {
            val p = fen.trim().split(" ")
            val b = CharArray(64) { '.' }
            var r = 7
            var f = 0
            for (c in p[0]) {
                when {
                    c == '/' -> { r--; f = 0 }
                    c.isDigit() -> f += c - '0'
                    else -> { b[r * 8 + f] = c; f++ }
                }
            }
            val ep = if (p.size > 3 && p[3] != "-") (p[3][0] - 'a') + (p[3][1] - '1') * 8 else -1
            return Position(b, p.getOrElse(1) { "w" } == "w", p.getOrElse(2) { "-" }, ep)
        }

        private val KNIGHT = arrayOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
        private val KING = arrayOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
        private val DIAG = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val ORTHO = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    }

    private fun own(c: Char, w: Boolean) = c != '.' && c.isUpperCase() == w

    fun attacked(sq: Int, byWhite: Boolean): Boolean {
        val f = sq % 8
        val r = sq / 8
        fun at(df: Int, dr: Int): Char {
            val nf = f + df
            val nr = r + dr
            return if (nf in 0..7 && nr in 0..7) b[nr * 8 + nf] else ' '
        }
        val pawn = if (byWhite) 'P' else 'p'
        val pr = if (byWhite) -1 else 1
        if (at(-1, pr) == pawn || at(1, pr) == pawn) return true
        val n = if (byWhite) 'N' else 'n'
        for ((df, dr) in KNIGHT) if (at(df, dr) == n) return true
        val k = if (byWhite) 'K' else 'k'
        for ((df, dr) in KING) if (at(df, dr) == k) return true
        fun ray(dirs: Array<Pair<Int, Int>>, a: Char, q: Char): Boolean {
            for ((df, dr) in dirs) {
                var i = 1
                while (true) {
                    val c = at(df * i, dr * i)
                    if (c == ' ') break
                    if (c != '.') { if (c == a || c == q) return true; break }
                    i++
                }
            }
            return false
        }
        val q = if (byWhite) 'Q' else 'q'
        return ray(DIAG, if (byWhite) 'B' else 'b', q) || ray(ORTHO, if (byWhite) 'R' else 'r', q)
    }

    private fun kingSq(w: Boolean) = b.indexOf(if (w) 'K' else 'k')

    fun inCheck(): Boolean = kingSq(white).let { it >= 0 && attacked(it, !white) }

    private fun pseudo(): List<Move> {
        val out = ArrayList<Move>(48)
        for (s in 0 until 64) {
            val c = b[s]
            if (!own(c, white)) continue
            val f = s % 8
            val r = s / 8
            fun step(dirs: Array<Pair<Int, Int>>, slide: Boolean) {
                for ((df, dr) in dirs) {
                    var nf = f + df
                    var nr = r + dr
                    while (nf in 0..7 && nr in 0..7) {
                        val t = b[nr * 8 + nf]
                        if (own(t, white)) break
                        out.add(Move(s, nr * 8 + nf))
                        if (t != '.' || !slide) break
                        nf += df; nr += dr
                    }
                }
            }
            when (c.uppercaseChar()) {
                'N' -> step(KNIGHT, false)
                'B' -> step(DIAG, true)
                'R' -> step(ORTHO, true)
                'Q' -> { step(DIAG, true); step(ORTHO, true) }
                'K' -> {
                    step(KING, false)
                    val home = if (white) 4 else 60
                    if (s == home && !attacked(s, !white)) {
                        val (ks, qs) = if (white) 'K' to 'Q' else 'k' to 'q'
                        val rook = if (white) 'R' else 'r'
                        if (ks in castle && b[s + 1] == '.' && b[s + 2] == '.' && b[s + 3] == rook &&
                            !attacked(s + 1, !white)) out.add(Move(s, s + 2))
                        if (qs in castle && b[s - 1] == '.' && b[s - 2] == '.' && b[s - 3] == '.' &&
                            b[s - 4] == rook && !attacked(s - 1, !white)) out.add(Move(s, s - 2))
                    }
                }
                'P' -> {
                    val d = if (white) 1 else -1
                    val nr = r + d
                    if (nr !in 0..7) continue
                    fun add(to: Int) {
                        if (nr == 0 || nr == 7) for (p in "qrbn") out.add(Move(s, to, p))
                        else out.add(Move(s, to))
                    }
                    if (b[nr * 8 + f] == '.') {
                        add(nr * 8 + f)
                        val startRank = if (white) 1 else 6
                        if (r == startRank && b[(r + 2 * d) * 8 + f] == '.') out.add(Move(s, (r + 2 * d) * 8 + f))
                    }
                    for (df in intArrayOf(-1, 1)) {
                        val nf = f + df
                        if (nf !in 0..7) continue
                        val to = nr * 8 + nf
                        val t = b[to]
                        if ((t != '.' && !own(t, white)) || to == ep) add(to)
                    }
                }
            }
        }
        return out
    }

    fun make(m: Move): Position {
        val n = b.copyOf()
        val c = n[m.from]
        val isPawn = c == 'P' || c == 'p'
        var newEp = -1
        if (isPawn && m.to == ep) n[m.to + if (white) -8 else 8] = '.'
        if (isPawn && kotlin.math.abs(m.to - m.from) == 16) newEp = (m.from + m.to) / 2
        if ((c == 'K' || c == 'k') && kotlin.math.abs(m.to - m.from) == 2) {
            if (m.to > m.from) { n[m.to - 1] = n[m.to + 1]; n[m.to + 1] = '.' }
            else { n[m.to + 1] = n[m.to - 2]; n[m.to - 2] = '.' }
        }
        n[m.to] = m.promo?.let { if (white) it.uppercaseChar() else it.lowercaseChar() } ?: c
        n[m.from] = '.'
        var cs = castle
        fun drop(s: String) { cs = cs.filter { it !in s } }
        if (c == 'K') drop("KQ")
        if (c == 'k') drop("kq")
        for (s in intArrayOf(m.from, m.to)) when (s) {
            0 -> drop("Q"); 7 -> drop("K"); 56 -> drop("q"); 63 -> drop("k")
        }
        return Position(n, !white, cs, newEp)
    }

    fun legalMoves(): List<Move> = pseudo().filter { m ->
        val n = make(m)
        val k = n.kingSq(white)
        k < 0 || !n.attacked(k, !white)
    }

    fun isMate() = inCheck() && legalMoves().isEmpty()

    /** UCI -> ruch; obsługuje też roszadę zapisaną jako "król bije wieżę" (e1h1). */
    fun fromUci(u: String): Move? {
        if (u.length < 4) return null
        val from = (u[0] - 'a') + (u[1] - '1') * 8
        var to = (u[2] - 'a') + (u[3] - '1') * 8
        val promo = u.getOrNull(4)?.lowercaseChar()
        val c = b.getOrNull(from) ?: return null
        if ((c == 'K' || c == 'k') && own(b[to], white) && b[to].uppercaseChar() == 'R') {
            to = if (to > from) from + 2 else from - 2
        }
        return legalMoves().firstOrNull { it.from == from && it.to == to && it.promo == promo }
    }

    fun fromSan(sanRaw: String): Move? {
        var san = sanRaw.trim().trimEnd('+', '#', '!', '?')
        val legal = legalMoves()
        if (san.startsWith("O-O") || san.startsWith("0-0")) {
            val long = san.count { it == 'O' || it == '0' } == 3
            val k = kingSq(white)
            return legal.firstOrNull { it.from == k && it.to == k + if (long) -2 else 2 }
        }
        var promo: Char? = null
        val eq = san.indexOf('=')
        if (eq >= 0) { promo = san[eq + 1].lowercaseChar(); san = san.substring(0, eq) }
        else if (san.last() in "QRBN" && san.length >= 3 && san[san.length - 2].isDigit()) {
            promo = san.last().lowercaseChar(); san = san.dropLast(1)
        }
        if (san.length < 2) return null
        val piece = if (san[0] in "NBRQK") san[0] else 'P'
        val to = (san[san.length - 2] - 'a') + (san[san.length - 1] - '1') * 8
        val mid = san.substring(if (piece == 'P') 0 else 1, san.length - 2).replace("x", "")
        return legal.firstOrNull { m ->
            m.to == to && m.promo == promo && b[m.from].uppercaseChar() == piece &&
                mid.all { ch -> if (ch.isDigit()) m.from / 8 == ch - '1' else m.from % 8 == ch - 'a' }
        }
    }
}
