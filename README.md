# Zadania Szachowe

Aplikacja na Androida z samymi zadaniami szachowymi. Zadania pobierane są z otwartego API [Lichess](https://lichess.org/api#tag/Puzzles) (baza zadań na licencji CC0).

## Funkcje

- ruch przez dotknięcie lub przeciągnięcie, premove, animacje ruchu, roszady i bicia
- dźwięki i wibracje (do wyłączenia)
- podpowiedź (drugie dotknięcie rysuje strzałkę), ponowienie, pokazanie rozwiązania
- przegląd rozwiązanego zadania ruch po ruchu i otwarcie go w analizie Lichess
- powtórki błędów: zawalone zadania wracają, aż zostaną rozwiązane bez pomocy
- tryb offline: zapas zadań pobierany z wyprzedzeniem, wyniki wysyłane po powrocie sieci
- tryb na czas (3 lub 5 minut, coraz trudniejsze zadania, trzy błędy kończą serię)
- zadanie dnia z Lichess
- logowanie do Lichess (OAuth, PKCE): wyniki trafiają do historii zadań na koncie
- postęp: wykres rankingu, skuteczność, średni czas, statystyki per motyw
- cel dzienny, seria dni i opcjonalne przypomnienie o 19:00
- wybór motywu, trudności, koloru szachownicy i zestawu figur
- układ pionowy i poziomy (telefony i tablety)

## Build

Każdy push na `main` buduje APK w GitHub Actions i publikuje go w zakładce **Releases** (oraz jako artefakt workflow).

Lokalnie: `gradle assembleRelease` (Gradle 8.9+, JDK 17).

## Licencje zasobów

Figury pochodzą z repozytorium Lichess:

- cburnett: Colin M.L. Burnett, GPLv2+
- merida: Armando Hernandez Marroquin, GPLv2+
- chessnut: Alexis Luengas, Apache 2.0
- celtic: Maurizio Monge, MIT

Dźwięki są wygenerowane syntetycznie na potrzeby tej aplikacji. Aplikacja nie jest powiązana z Lichess ani Chess.com.
