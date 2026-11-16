package at.htl.donershop;

import at.htl.donershop.Controllers.OrderController;
import at.htl.donershop.Entities.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class InventoryOrderIntegrationTests {

    @Autowired
    private EntityManager em;

    @Autowired
    private OrderController orderController;

    @Test
    void confirmingOrderDeductsStockAndCannotDeductTwice() {
        TestData data = createOrderWithStock(100.0, 2, 15.0);

        Order confirmed = orderController.confirmOrder(data.order().getId());
        em.flush();

        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(70.0, em.find(Inventory.class, data.inventory().getId()).getQuantity(), 0.0001);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderController.confirmOrder(data.order().getId())
        );
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(70.0, em.find(Inventory.class, data.inventory().getId()).getQuantity(), 0.0001);
    }

    @Test
    void insufficientStockDoesNotChangeAnyInventory() {
        TestData data = createOrderWithStock(20.0, 2, 15.0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderController.confirmOrder(data.order().getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("OPEN", em.find(Order.class, data.order().getId()).getStatus());
        assertEquals(20.0, em.find(Inventory.class, data.inventory().getId()).getQuantity(), 0.0001);
    }

    private TestData createOrderWithStock(double stockQuantity, int orderQuantity,
                                          double ingredientPerProduct) {
        Customer customer = new Customer("Test", "Kunde");
        em.persist(customer);

        Category category = new Category();
        category.setName("Test");
        em.persist(category);

        Product product = new Product();
        product.setName("Test-Döner");
        product.setPrice(5.0);
        product.setCategory(category);
        em.persist(product);

        Ingredient ingredient = new Ingredient();
        ingredient.setName("Fleisch");
        ingredient.setUnit("g");
        em.persist(ingredient);

        Inventory inventory = new Inventory();
        inventory.setIngredient(ingredient);
        inventory.setQuantity(stockQuantity);
        em.persist(inventory);

        ProductIngredient recipeLine = new ProductIngredient();
        recipeLine.setProduct(product);
        recipeLine.setIngredient(ingredient);
        recipeLine.setQuantity(ingredientPerProduct);
        em.persist(recipeLine);

        Order order = new Order(customer);
        Order_Item item = new Order_Item(order, product, orderQuantity, 5.0);
        order.addItem(item);
        em.persist(order);
        em.flush();

        return new TestData(order, inventory);
    }

    private record TestData(Order order, Inventory inventory) {}
}
