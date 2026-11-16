package at.htl.donershop.Controllers;


import at.htl.donershop.Entities.Order;
import at.htl.donershop.Entities.Order_Item;
import at.htl.donershop.Entities.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/order-items")
public class Order_Item_Controller {

    @PersistenceContext
    private EntityManager em;

    public record OrderItemRequest(
            Long orderId,
            Long productId,
            Integer quantity,
            double unitPrice
    ) {}

    @GetMapping
    public List<Order_Item> getAllOrderItems() {
        return em.createQuery(
                "SELECT i FROM Order_Item i", Order_Item.class
        ).getResultList();
    }

    @GetMapping("/{id}")
    public Order_Item getOrderItemById(@PathVariable Long id) {
        Order_Item item = em.find(Order_Item.class, id);

        if (item == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Bestellposition nicht gefunden"
            );
        }

        return item;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Order_Item createOrderItem(@RequestBody OrderItemRequest request) {

        Order order = em.find(Order.class, request.orderId());
        Product product = em.find(Product.class, request.productId());

        if (order == null || product == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Bestellung oder Produkt nicht gefunden"
            );
        }

        ensureOrderOpen(order);

        if (request.quantity() == null || request.quantity() < 1
                || request.unitPrice() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ungültige Menge oder Preis"
            );
        }

        Order_Item item = new Order_Item(
                order,
                product,
                request.quantity(),
                request.unitPrice()
        );

        order.addItem(item);
        em.persist(item);
        em.flush();

        return item;
    }

    @PutMapping("/{id}")
    @Transactional
    public Order_Item updateOrderItem(
            @PathVariable Long id,
            @RequestBody OrderItemRequest request
    ) {
        Order_Item item = getOrderItemById(id);
        ensureOrderOpen(item.getOrder());

        Product product = em.find(Product.class, request.productId());

        if (product == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Produkt nicht gefunden"
            );
        }

        if (request.quantity() == null || request.quantity() < 1
                || request.unitPrice() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ungültige Menge oder Preis"
            );
        }

        item.setProduct(product);
        item.setQuantity(request.quantity());
        item.setUnitPrice(request.unitPrice());

        return item;
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrderItem(@PathVariable Long id) {
        Order_Item item = getOrderItemById(id);
        ensureOrderOpen(item.getOrder());

        item.getOrder().getItems().remove(item);
        em.remove(item);
    }

    private void ensureOrderOpen(Order order) {
        if (!"OPEN".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bestellpositionen können nach der Bestätigung nicht mehr geändert werden");
        }
    }
}
