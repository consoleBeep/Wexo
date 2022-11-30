# Wexo

## Backend
Her er dokumentation af backend delen af Wexo projektet.

### Cache:
- Vi har et cache system der køre på en seperat thread fra resten af systemet.
  Denne cache opdateres hvert 15. minut og lagres i en JSON fil på disken samt ligger cachen også i memory når programmet køres og indlæses når programmet starter.
  Hvis den lokale cache (den på disken) er tom er vi nødt til at afvente en API respons før vi kan bruge systemet.
- Når programmet starter henter vi som sagt data fra den lokale cache, hvis den er tom henter vi et nyt data sæt fra API'en. Vi kan kun hente 1.000 ud af de 10.000 entries (film/serier) ad gangen, så vi køre det i "chunks" (bidder). Først henter vi 1 til 1.000, så henter vi 1.001 til 2.000, osv. indtil 10.000.

### REST:
- API'en er RESTful og selve REST klienten er programmeret fra bunden af [Casper Agerskov Madsen](https://github.com/consoleBeep) med lidt hjælp fra [Bastian Asmussen](https://github.com/BastianAsmussen).
- Når vi skal bruge nyt data henter vi et råt JSON objekt kompresset med GZIP for et hurtigere download som vi derefter dekomprimerer til original størrelse.

### Servering af data:
- En request til hjemmesiden kan se sådan her ud:
    - Hvis en bruger henter roden af webserveren `/` giver vi dem entries fra 1 til 100 (kan ændres med `/?start=x&end=y` hvor `x` er startindekset og `y` er slutindekset)
    - Brugeren kan også bruge `genre` parameteren (`/?genre=x` hvor `x` f.eks. er `Action`).
- Eksempler:
    - Kun Action film fra indeks 200 til 600:
        - `127.0.0.1:8080/?start=200&end=600&genre=Action&type=movie`
    - Kun Gyser serier fra indeks 1 til 100:
        - `127.0.0.1:8080/?genre=Gyser&type=series` (`1` er standard startindeks og `100` er standard slutindeks)