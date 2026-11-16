package at.htl.donershop.Services;

import at.htl.donershop.Entities.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {
    // Feste Präsentationsdaten: Ergebnisse bleiben auch nach einem Neustart nachvollziehbar.
    public static final LocalDate DEMO_DATE = LocalDate.of(2026, 11, 16);
    private static final String SEED_ID = "payments-statistics-demo-v1";

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (em.find(DemoSeed.class, SEED_ID) != null) { return; }

        Customer anna = new Customer("Anna", "Demo");
        Customer max = new Customer("Max", "Demo");
        em.persist(anna);
        em.persist(max);
        Category category = new Category();
        category.setName("Präsentation – Demo");
        em.persist(category);
        Product doener = product("Demo Döner", 6.50, category);
        Product durum = product("Demo Dürüm", 7.50, category);
        Product ayran = product("Demo Ayran", 2.00, category);

        Order first = order(anna, Order.COMPLETED);
        first.addItem(new Order_Item(first, doener, 2, doener.getPrice()));
        first.addItem(new Order_Item(first, ayran, 2, ayran.getPrice()));
        payment(first, "17.00", Payment.Status.SUCCESSFUL, 12);

        Order retry = order(max, Order.COMPLETED);
        retry.addCustomItem(new Order_Item_Custom(retry, durum, 2, durum.getPrice(), "ohne Zwiebel"));
        retry.addItem(new Order_Item(retry, ayran, 1, ayran.getPrice()));
        payment(retry, "17.00", Payment.Status.FAILED, 13);
        payment(retry, "17.00", Payment.Status.SUCCESSFUL, 14);

        Order third = order(anna, Order.COMPLETED);
        third.addItem(new Order_Item(third, doener, 2, doener.getPrice()));
        third.addItem(new Order_Item(third, durum, 1, durum.getPrice()));
        payment(third, "20.50", Payment.Status.SUCCESSFUL, 15);

        Order failed = order(max, "OPEN");
        failed.addItem(new Order_Item(failed, doener, 10, doener.getPrice()));
        payment(failed, "65.00", Payment.Status.FAILED, 16);
        Order unpaid = order(anna, "OPEN");
        unpaid.addItem(new Order_Item(unpaid, ayran, 5, ayran.getPrice()));

        em.persist(new DemoSeed(SEED_ID));
        em.flush();
    }

    private Product product(String name, double price, Category category) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setCategory(category);
        product.setDescription("Automatische Präsentationsdaten");
        em.persist(product);
        return product;
    }

    private Order order(Customer customer, String status) {
        Order order = new Order(customer);
        order.setStatus(status);
        em.persist(order);
        return order;
    }

    private void payment(Order order, String amount, Payment.Status status, int hour) {
        em.persist(new Payment(order, new BigDecimal(amount), status, DEMO_DATE.atTime(hour, 0)));
    }
}
