package at.htl.donershop.Controllers;

import at.htl.donershop.Entities.Customer;
import at.htl.donershop.Entities.Order;
import at.htl.donershop.Entities.Order_Item;
import at.htl.donershop.Entities.Order_Item_Custom;
import at.htl.donershop.Entities.ProductIngredient;
import at.htl.donershop.Entities.Inventory;
import at.htl.donershop.Entities.Ingredient;
import jakarta.persistence.LockModeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PersistenceContext
    private EntityManager em;

    public record OrderRequest(Long customerId, String status) {}

    @GetMapping
    public List<Order> getAllOrders() {
        return em.createQuery(
                "SELECT o FROM Order o", Order.class
        ).getResultList();
    }

    @GetMapping("/{id}")
    public Order getOrderById(@PathVariable Long id) {
        Order order = em.find(Order.class, id);

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Bestellung nicht gefunden"
            );
        }

        return order;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Order createOrder(@RequestBody OrderRequest request) {

        Customer customer = em.find(Customer.class, request.customerId());

        if (customer == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Kunde nicht gefunden"
            );
        }

        Order order = new Order(customer);

        if (request.status() != null && !request.status().isBlank()
                && !"OPEN".equalsIgnoreCase(request.status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Eine neue Bestellung muss mit dem Status OPEN erstellt werden");
        }

        em.persist(order);
        em.flush();

        return order;
    }

    @PutMapping("/{id}")
    @Transactional
    public Order updateOrder(
            @PathVariable Long id,
            @RequestBody OrderRequest request
    ) {
        Order order = getOrderById(id);

        if (!"OPEN".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Eine bestätigte Bestellung kann nicht mehr geändert werden");
        }

        if ("CONFIRMED".equalsIgnoreCase(request.status())) {
            if (request.customerId() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Kunde und Bestellstatus bitte getrennt ändern");
            }
            confirm(order);
            return order;
        }

        if (request.status() != null && !"OPEN".equalsIgnoreCase(request.status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Zum Bestätigen bitte POST /api/orders/{id}/confirm verwenden");
        }

        if (request.customerId() != null) {
            Customer customer = em.find(Customer.class, request.customerId());
            if (customer == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Kunde nicht gefunden");
            }
            order.setKunde(customer);
        }

        return order;
    }

    /** Bestätigt eine Bestellung und zieht die benötigten Zutaten einmalig vom Lager ab. */
    @PostMapping("/{id}/confirm")
    @Transactional
    public Order confirmOrder(@PathVariable Long id) {
        Order order = getOrderById(id);
        confirm(order);
        return order;
    }

    private void confirm(Order order) {
        // Sperrt die Bestellung selbst, damit parallele Bestätigungen nicht doppelt abbuchen.
        em.lock(order, LockModeType.PESSIMISTIC_WRITE);
        em.refresh(order);

        if (!"OPEN".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Die Bestellung ist nicht mehr offen oder wurde bereits bestätigt");
        }

        // Benötigte Mengen je Zutat sammeln (auch wenn mehrere Produkte dieselbe Zutat verwenden).
        Map<Long, Double> required = new LinkedHashMap<>();
        Map<Long, Ingredient> ingredients = new LinkedHashMap<>();

        for (Order_Item item : order.getItems()) {
            addRequiredIngredients(item.getProduct().getId(), item.getQuantity(), required, ingredients);
        }
        for (Order_Item_Custom item : order.getCustomItems()) {
            addRequiredIngredients(item.getProduct().getId(), item.getQuantity(), required, ingredients);
        }

        if (required.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Die Bestellung enthält keine Produkte mit hinterlegten Zutaten");
        }

        // Alle Lagerzeilen zuerst sperren und prüfen. Erst danach werden Mengen verändert.
        Map<Long, Inventory> stocks = new LinkedHashMap<>();
        for (Map.Entry<Long, Double> entry : required.entrySet()) {
            List<Inventory> found = em.createQuery(
                    "SELECT i FROM Inventory i WHERE i.ingredient.id = :ingredientId",
                    Inventory.class)
                    .setParameter("ingredientId", entry.getKey())
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultList();

            Ingredient ingredient = ingredients.get(entry.getKey());
            if (found.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Kein Lagerbestand für Zutat '" + ingredient.getName() + "' vorhanden");
            }

            Inventory stock = found.get(0);
            if (stock.getQuantity() < entry.getValue()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Nicht genügend " + ingredient.getName() + " auf Lager. Benötigt: "
                                + entry.getValue() + ", vorhanden: " + stock.getQuantity());
            }
            stocks.put(entry.getKey(), stock);
        }

        for (Map.Entry<Long, Double> entry : required.entrySet()) {
            Inventory stock = stocks.get(entry.getKey());
            stock.setQuantity(stock.getQuantity() - entry.getValue());
        }

        order.setStatus("CONFIRMED");
        em.flush();
    }

    private void addRequiredIngredients(Long productId, int productCount,
                                        Map<Long, Double> required,
                                        Map<Long, Ingredient> ingredients) {
        List<ProductIngredient> recipe = em.createQuery(
                "SELECT pi FROM ProductIngredient pi WHERE pi.product.id = :productId",
                ProductIngredient.class)
                .setParameter("productId", productId)
                .getResultList();

        if (recipe.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Für Produkt mit ID " + productId + " ist kein Rezept hinterlegt");
        }

        for (ProductIngredient line : recipe) {
            Long ingredientId = line.getIngredient().getId();
            required.merge(ingredientId, line.getQuantity() * productCount, Double::sum);
            ingredients.put(ingredientId, line.getIngredient());
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrder(@PathVariable Long id) {
        Order order = getOrderById(id);
        if (!"OPEN".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Eine bestätigte Bestellung kann nicht gelöscht werden");
        }
        em.remove(order);
    }
}