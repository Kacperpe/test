# Zadania Szachowe

Aplikacja na Androida z samymi zadaniami szachowymi. Zadania pobierane są z otwartego API [Lichess](https://lichess.org/api#tag/Puzzles) (baza zadań na licencji CC0).

- zielona szachownica i ciemny motyw, ruch przez dotknięcie lub przeciągnięcie
- przeciwnik wykonuje pierwszy ruch, potem szukasz najlepszej odpowiedzi
- podpowiedź (drugie dotknięcie rysuje strzałkę), ponowienie, pokazanie rozwiązania, lokalny ranking i seria
- wybór motywu (mat w 1/2/3, widełki, związanie, końcówki…)

## Build

Każdy push na `main` buduje APK w GitHub Actions i publikuje go w zakładce **Releases** (oraz jako artefakt workflow).

Lokalnie: `gradle assembleRelease` (Gradle 8.9+, JDK 17).

## Licencje

Figury: zestaw *cburnett* (Colin M.L. Burnett), GPLv2+. Aplikacja nie jest powiązana z Lichess ani Chess.com.
