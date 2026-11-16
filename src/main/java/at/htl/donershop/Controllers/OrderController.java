package at.htl.donershop.Controllers;

import at.htl.donershop.Entities.Customer;
import at.htl.donershop.Entities.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PersistenceContext
    private EntityManager em;

    public record OrderRequest(
            @Schema(description = "ID eines vorhandenen Kunden: zuerst POST /api/kunden oder GET /api/kunden verwenden.", example = "1")
            Long customerId,
            @Schema(description = "Optional; Standardwert ist OPEN. COMPLETED kennzeichnet eine abgeschlossene Bestellung.", example = "OPEN")
            String status) {}

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
    @Operation(description = "Erstellt eine Bestellung für einen vorhandenen Kunden. Bei unbekannter customerId wird 404 zurückgegeben (Kunde nicht gefunden).")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Order createOrder(@RequestBody OrderRequest request) {

        if (request.customerId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "customerId muss angegeben werden"
            );
        }

        Customer customer = em.find(Customer.class, request.customerId());

        if (customer == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Kunde nicht gefunden"
            );
        }

        Order order = new Order(customer);

        if (request.status() != null && !request.status().isBlank()) {
            order.setStatus(request.status());
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

        if (request.customerId() != null) {
            Customer customer = em.find(Customer.class, request.customerId());

            if (customer == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Kunde nicht gefunden"
                );
            }

            order.setKunde(customer);
        }

        if (request.status() != null) {
            order.setStatus(request.status());
        }

        return order;
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrder(@PathVariable Long id) {
        Order order = getOrderById(id);
        em.remove(order);
    }
}
