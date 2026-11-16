package at.htl.donershop.Controllers;
import at.htl.donershop.Entities.Order;
import at.htl.donershop.Entities.Order_Item_Custom;
import at.htl.donershop.Entities.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/order-item-customs")
public class Order_Item_Custom_Controller {

    @PersistenceContext
    private EntityManager em;

    public record CustomOrderItemRequest(
            Long orderId,
            Long productId,
            Integer quantity,
            double unitPrice,
            String specialInstructions
    ) {}

    @GetMapping
    public List<Order_Item_Custom> getAllCustomOrderItems() {
        return em.createQuery(
                "SELECT i FROM Order_Item_Custom i",
                Order_Item_Custom.class
        ).getResultList();
    }

    @GetMapping("/{id}")
    public Order_Item_Custom getCustomOrderItemById(@PathVariable Long id) {
        Order_Item_Custom item = em.find(Order_Item_Custom.class, id);

        if (item == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Individuelle Bestellposition nicht gefunden"
            );
        }

        return item;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Order_Item_Custom createCustomOrderItem(
            @RequestBody CustomOrderItemRequest request
    ) {
        Order order = em.find(Order.class, request.orderId());
        Product product = em.find(Product.class, request.productId());

        if (order == null || product == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Bestellung oder Produkt nicht gefunden"
            );
        }

        if (request.quantity() == null || request.quantity() < 1
                || request.unitPrice() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ungültige Menge oder Preis"
            );
        }

        Order_Item_Custom item = new Order_Item_Custom(
                order,
                product,
                request.quantity(),
                request.unitPrice(),
                request.specialInstructions()
        );

        order.addCustomItem(item);
        em.persist(item);
        em.flush();

        return item;
    }

    @PutMapping("/{id}")
    @Transactional
    public Order_Item_Custom updateCustomOrderItem(
            @PathVariable Long id,
            @RequestBody CustomOrderItemRequest request
    ) {
        Order_Item_Custom item = getCustomOrderItemById(id);

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
        item.setSpecialInstructions(request.specialInstructions());

        return item;
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomOrderItem(@PathVariable Long id) {
        Order_Item_Custom item = getCustomOrderItemById(id);

        item.getOrder().getCustomItems().remove(item);
        em.remove(item);
    }
}
