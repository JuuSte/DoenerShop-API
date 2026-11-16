# Testbestellung in Swagger

`POST /api/orders` ist vorhanden. HTTP 404 mit „Kunde nicht gefunden“ bedeutet,
dass die übergebene `customerId` in der Datenbank nicht existiert. Swagger kennt
die vorhandenen Kunden nicht; eine Beispiel-ID ist kein automatisch angelegter Kunde.

1. Mit `POST /api/kunden` einen Kunden erstellen:

   ```json
   {"firstName": "Max", "lastName": "Mustermann"}
   ```

2. Die `id` aus der Antwort übernehmen (alternativ Kunden über `GET /api/kunden`
   abfragen). Wenn die zurückgegebene ID `1` ist, an `POST /api/orders` senden:

   ```json
   {"customerId": 1, "status": "OPEN"}
   ```

   Erwartet: HTTP 201 und die neu erstellte Bestellung. `status` darf entfallen;
   dann wird ebenfalls `OPEN` verwendet. Eine fehlende `customerId` ergibt 400,
   eine nicht vorhandene Kunden-ID weiterhin 404.

3. Für einen Zahlungstest zunächst über `GET /api/products` ein vorhandenes Produkt
   auswählen und über `POST /api/order-items` eine Position hinzufügen. Die IDs
   durch die tatsächliche Bestell- und Produkt-ID ersetzen:

   ```json
   {"orderId": 1, "productId": 1, "quantity": 1, "unitPrice": 5.5}
   ```

4. Anschließend `POST /api/payments` mit der tatsächlichen Bestell-ID aufrufen:

   ```json
   {"orderId": 1, "successful": true}
   ```

Eine Bestellung ohne Positionen lässt sich anlegen, aber nicht bezahlen.
