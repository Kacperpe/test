package pl.kacperpe.zadania

import android.content.Context
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF302E2B)
val Panel = Color(0xFF262522)
val Chip = Color(0xFF3C3A37)
val Green = Color(0xFF81B64C)
val GreenDark = Color(0xFF5D9948)
val Red = Color(0xFFE5483B)
val Hl = Color(0x99F5F646)
val HintCol = Color(0xB34FC3F7)
val PreCol = Color(0x99E5483B)
val ArrowCol = Color(0xCCF7A62B)
val Muted = Color(0xFFA9A8A6)

class BoardTheme(val name: String, val light: Color, val dark: Color)

val BOARDS = listOf(
    BoardTheme("Zielona", Color(0xFFEBECD0), Color(0xFF739552)),
    BoardTheme("Brązowa", Color(0xFFF0D9B5), Color(0xFFB58863)),
    BoardTheme("Niebieska", Color(0xFFDEE3E6), Color(0xFF8CA2AD)),
    BoardTheme("Szara", Color(0xFFDCDCDC), Color(0xFF8C8C8C)),
    BoardTheme("Fioletowa", Color(0xFFE9E1F2), Color(0xFF8E7BB8)),
)

val PIECES = listOf("cburnett" to "Klasyczne", "merida" to "Merida", "chessnut" to "Chessnut", "celtic" to "Celtic")

val ANGLES = listOf(
    "mix" to "Wszystkie", "mateIn1" to "Mat w 1", "mateIn2" to "Mat w 2", "mateIn3" to "Mat w 3",
    "fork" to "Widełki", "pin" to "Związanie", "skewer" to "Rożen", "discoveredAttack" to "Atak z odsłony",
    "sacrifice" to "Poświęcenie", "hangingPiece" to "Wisząca figura", "backRankMate" to "Mat na ostatniej linii",
    "promotion" to "Promocja", "opening" to "Debiut", "middlegame" to "Gra środkowa", "endgame" to "Końcówka",
)

val DIFFS = listOf(
    "auto" to "Auto", "easiest" to "Najłatwiejsze", "easier" to "Łatwiejsze",
    "normal" to "Normalne", "harder" to "Trudniejsze", "hardest" to "Najtrudniejsze",
)

/** Motywy pokazywane w statystykach (klucze Lichess, da się je też ćwiczyć osobno). */
val THEME_LABELS = linkedMapOf(
    "mateIn1" to "Mat w 1", "mateIn2" to "Mat w 2", "mateIn3" to "Mat w 3", "mateIn4" to "Mat w 4",
    "fork" to "Widełki", "pin" to "Związanie", "skewer" to "Rożen", "discoveredAttack" to "Atak z odsłony",
    "doubleCheck" to "Podwójny szach", "sacrifice" to "Poświęcenie", "hangingPiece" to "Wisząca figura",
    "trappedPiece" to "Złapana figura", "attraction" to "Zwabienie", "deflection" to "Odciągnięcie",
    "capturingDefender" to "Zbicie obrońcy", "interference" to "Przesłona", "clearance" to "Oczyszczenie linii",
    "intermezzo" to "Ruch pośredni", "quietMove" to "Cichy ruch", "defensiveMove" to "Ruch obronny",
    "xRayAttack" to "Atak rentgenowski", "zugzwang" to "Zugzwang", "backRankMate" to "Mat na ostatniej linii",
    "smotheredMate" to "Mat duszony", "exposedKing" to "Odsłonięty król", "kingsideAttack" to "Atak na skrzydle królewskim",
    "queensideAttack" to "Atak na skrzydle hetmańskim", "advancedPawn" to "Zaawansowany pion", "promotion" to "Promocja",
    "pawnEndgame" to "Końcówka pionowa", "rookEndgame" to "Końcówka wieżowa", "queenEndgame" to "Końcówka hetmańska",
    "bishopEndgame" to "Końcówka gońcowa", "knightEndgame" to "Końcówka skoczkowa",
    "opening" to "Debiut", "middlegame" to "Gra środkowa", "endgame" to "Końcówka",
)

fun themeLabel(k: String): String = ANGLES.firstOrNull { it.first == k }?.second ?: THEME_LABELS[k] ?: k

fun mmss(s: Int) = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

private val resCache = HashMap<String, Int>()

fun pieceRes(ctx: Context, set: String, c: Char): Int {
    val name = (if (set == "cburnett") "" else set + "_") + (if (c.isUpperCase()) "w" else "b") + c.lowercaseChar()
    return resCache.getOrPut(name) {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (id != 0) id else R.drawable.wp
    }
}
