# Zahlungen und Statistiken

Die neue Tabelle `payment` wird durch die vorhandene Einstellung `spring.jpa.hibernate.ddl-auto=update` in derselben H2-Datenbank angelegt. Bestehende Entities und deren API bleiben unverändert.

## Zahlungen

`POST /api/payments` simuliert einen Zahlungsversuch:

```json
{"orderId": 1, "successful": true}
```

`successful: false` speichert einen Fehlversuch. Der Betrag wird serverseitig aus Menge und gespeichertem Positionspreis aller normalen und Sonderpositionen berechnet, mit `BigDecimal.valueOf` aus den bestehenden `double`-Feldern übernommen und auf zwei Nachkommastellen gerundet (HALF_UP). Der Client gibt keinen Betrag vor. Der Zahlungszeitpunkt ist die lokale Serverzeit, wie beim bestehenden Bestelldatum.

Die Antwort enthält `id`, `orderId`, `amount`, `status` (`SUCCESSFUL` oder `FAILED`) und `paymentTime`. Erfolgreich bezahlte Bestellungen lehnen weitere Versuche mit HTTP 409 ab. Eine transaktionale Datenbanksperre auf der Bestellung verhindert auch parallele erfolgreiche Zahlungen über diesen Endpunkt. Fehlversuche dürfen wiederholt werden. Unbekannte Bestellungen ergeben 404, leere oder ungültige Bestellungen 400.

- `GET /api/payments`: alle Zahlungsversuche.
- `GET /api/payments?orderId=1`: Zahlungsversuche einer Bestellung.
- `GET /api/payments/{id}`: einzelner Zahlungsversuch, sonst 404.

## Statistiken

- `GET /api/statistics/revenue/daily?date=2026-11-16`: erfolgreicher Umsatz des Tages als Dezimalzahl.
- `GET /api/statistics/revenue/monthly?month=2026-11`: erfolgreicher Umsatz des Monats als Dezimalzahl.
- `GET /api/statistics/best-selling-products`: Produkte mit `productId`, `name` und summierter `quantity`, absteigend nach Menge, bei Gleichstand nach ID. Normale und Sonderpositionen bezahlter Bestellungen werden zusammengezählt. Fehlversuche vervielfachen keine Positionen.
- `GET /api/statistics/average-order-value?completedStatus=...`: durchschnittlicher erfolgreicher Zahlungsbetrag für Bestellungen mit genau diesem Abschlussstatus, auf Cent gerundet. Ohne Treffer `0.00`.

**Abschlussstatus:** `COMPLETED` ist die gemeinsame Konvention für abgeschlossene Bestellungen. Der bestehende String-Status bleibt erhalten; Person 2 kann weiter eigene Statuswerte verwenden. `PUT /api/orders/{id}` mit `{"status":"COMPLETED"}` setzt den Abschluss, ohne den Kunden ändern zu müssen. Zahlung und Bestellabschluss bleiben getrennte Vorgänge. Die Durchschnittsauswertung verwendet ohne Parameter `COMPLETED`; ein expliziter `completedStatus` bleibt für andere Konventionen möglich. `OPEN` ist kein Abschlussstatus.

Umsatz richtet sich nach dem gespeicherten Zahlungszeitpunkt, einschließlich Periodenbeginn und ausschließlich Beginn der Folgeperiode. Leere Zeiträume liefern `0.00`. Produktmengen beziehen sich auf die vorhandenen Bestellpositionen; Änderungen von Positionen nach Bezahlung wirken sich weiterhin auf die Produktauswertung aus, der gespeicherte Zahlungsbetrag bleibt bestehen. Die bestehenden Bearbeitungsendpunkte werden hierfür nicht verändert.

## Tests

`./mvnw test` führt vorhandene und neue Tests aus. Die Testkonfiguration verwendet ausschließlich eine separate H2-In-Memory-Datenbank. Geprüft werden Persistenz, Fehlversuche und Wiederholung, parallele Zahlungen, Dezimalberechnung, Sonderpositionen, Periodengrenzen, Produktmengen, Durchschnitt, leere Daten und REST-Anfragen.


## Präsentation: direkt starten

```bash
./mvnw spring-boot:run
```

Swagger: http://localhost:8080/swagger-ui/index.html

Die Demo wird beim ersten Start automatisch in der bestehenden H2-Dateidatenbank ergänzt. Eine eigene Markierung (`DemoSeed`) verhindert erneutes Anlegen, auch wenn Demo-Kunden später umbenannt werden. Alle Einfügungen und die Markierung werden gemeinsam transaktional gespeichert. Bestehende Daten werden weder gelöscht noch überschrieben; IDs werden automatisch vergeben. Optional lässt sich die Anlage mit `--app.demo.enabled=false` deaktivieren. Tests verwenden separate In-Memory-Datenbanken.

Die Zahlungsdaten liegen bewusst fest am **16.11.2026**. Für die Präsentation diese Parameter verwenden, auch nach einem späteren Neustart.

| Swagger-Endpunkt | Parameter | Erwarteter Demo-Beitrag |
| --- | --- | --- |
| `GET /api/payments` | keine | 5 Demo-Zahlungen: 3 SUCCESSFUL, 2 FAILED; eine Bestellung mit Fehlversuch und erfolgreicher Wiederholung |
| `GET /api/statistics/revenue/daily` | date: `2026-11-16` | `54.50` Euro |
| `GET /api/statistics/revenue/monthly` | month: `2026-11` | `54.50` Euro |
| `GET /api/statistics/best-selling-products` | keine | Demo Döner: 4, Demo Dürüm: 3, Demo Ayran: 3; Gleichstand nach Produkt-ID |
| `GET /api/statistics/average-order-value` | Standard `COMPLETED` beibehalten | `18.17` Euro (54,50 / 3) |
| `GET /api/orders` | keine | 5 Demo-Bestellungen: 3 COMPLETED, 2 OPEN |

Zusätzlich werden 2 Demo-Kunden, eine Demo-Kategorie und 3 Produkte angelegt: Döner 6,50 Euro, Dürüm 7,50 Euro, Ayran 2,00 Euro. Eine Dürüm-Position enthält den Sonderwunsch „ohne Zwiebel“. Die erfolglos bezahlte Bestellung über 65 Euro und die unbezahlte Bestellung über 10 Euro zählen nicht in die Statistiken. Es sind keine POST-Aufrufe oder manuellen Testdaten nötig.

**Vorhandene Daten zählen weiterhin mit:** Die genannten Zahlen sind die Demo-Beiträge. Bei bereits vorhandenen erfolgreichen Zahlungen können Gesamtumsatz, Rangliste und Durchschnitt abweichen. Für Details `GET /api/payments?orderId=…` mit einer ID aus der Bestellliste öffnen; keine festen IDs voraussetzen.
