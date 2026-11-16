# Speisekarte und Lagerverwaltung

## Endpunkte

### Kategorien
- `GET /api/categories`
- `GET /api/categories/{id}`
- `POST /api/categories`
- `PUT /api/categories/{id}`
- `DELETE /api/categories/{id}`

### Produkte
- `GET /api/products`
- `GET /api/products/{id}`
- `POST /api/products`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`
- `GET /api/products/{id}/availability` – prüft anhand der Rezept-Zutaten und des Lagerbestands, ob ein Produkt verfügbar ist.

### Zutaten
- `GET /api/ingredients`
- `GET /api/ingredients/{id}`
- `POST /api/ingredients`
- `PUT /api/ingredients/{id}`
- `DELETE /api/ingredients/{id}`

### Produkt-Zutaten-Zuordnung
- `GET /api/product-ingredients`
- `GET /api/product-ingredients/product/{productId}`
- `POST /api/product-ingredients`
- `PUT /api/product-ingredients/{id}`
- `DELETE /api/product-ingredients/{id}`

### Lagerbestand
- `GET /api/inventory`
- `GET /api/inventory/{id}`
- `GET /api/inventory/ingredient/{ingredientId}`
- `POST /api/inventory`
- `PUT /api/inventory/{id}`
- `PATCH /api/inventory/{id}/quantity?quantity=25`
- `DELETE /api/inventory/{id}`

## Beispiel-Requests

Zutat anlegen:
```json
{"name":"Tomaten","unit":"Stück","active":true}
```

Lagerbestand für Zutat mit ID 1 anlegen:
```json
{"ingredient":{"id":1},"quantity":25}
```

Zutat einem Produkt zuordnen (Produkt ID 1, Zutat ID 1, benötigte Menge 2):
```json
{"product":{"id":1},"ingredient":{"id":1},"quantity":2}
```

Danach `GET /api/products/1/availability` aufrufen.

Der Verfügbarkeits-Endpunkt prüft die benötigten Mengen, zieht aber noch keinen Bestand ab.

## Lagerbestand bei Bestellung abbuchen

Eine Bestellung wird zuerst mit Status `OPEN` erstellt. Danach werden die normalen oder individuellen Bestellpositionen angelegt. Sobald die Bestellung fertig ist, wird sie bestätigt:

- `POST /api/orders/{id}/confirm`
- Das System liest die Rezept-Zutaten aller Bestellpositionen.
- Es prüft alle Lagerbestände. Wenn eine Zutat fehlt oder nicht ausreicht, wird nichts abgezogen und die API antwortet mit `409 Conflict`.
- Wenn alles vorhanden ist, werden die Mengen abgezogen und der Bestellstatus auf `CONFIRMED` gesetzt.
- Eine bereits bestätigte Bestellung kann nicht nochmals abgebucht oder nachträglich bearbeitet werden.

Beispiel: 2 Produkte benötigen je 150 g Fleisch. Bei Bestätigung werden 300 g vom Lager abgezogen.

**Wichtig:** Bei individuellen Bestellungen werden derzeit dieselben Rezept-Zutaten wie beim normalen Produkt verwendet. `specialInstructions` verändern die Zutatenberechnung noch nicht.

## Tests

Projekt-Tests starten mit `./mvnw test`; den vollständigen Build mit `./mvnw clean verify`.

Swagger UI: `http://localhost:8080/swagger-ui/index.html`
