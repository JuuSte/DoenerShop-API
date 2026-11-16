package at.htl.donershop;

import at.htl.donershop.Entities.Customer;
import at.htl.donershop.Entities.Order;
import at.htl.donershop.Entities.Order_Item;
import at.htl.donershop.Entities.Product;
import at.htl.donershop.Services.PaymentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrentPaymentTests {
    @Autowired EntityManager em;
    @Autowired PaymentService payments;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentSuccessfulAttemptsOnlyCreateOnePayment() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        long[] ids = transaction.execute(status -> {
            Customer customer = new Customer("Parallel", "Test");
            Product product = new Product();
            product.setName("Testprodukt");
            em.persist(customer);
            em.persist(product);
            Order order = new Order(customer);
            order.addItem(new Order_Item(order, product, 1, 5));
            em.persist(order);
            em.flush();
            return new long[]{order.getId(), customer.getId(), product.getId()};
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            start.await();
            try {
                payments.simulate(ids[0], true);
                return 201;
            } catch (ResponseStatusException e) {
                return e.getStatusCode().value();
            }
        };
        try {
            Future<Integer> first = executor.submit(attempt);
            Future<Integer> second = executor.submit(attempt);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(payments.getPayments(ids[0])).hasSize(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            transaction.executeWithoutResult(status -> {
                em.createQuery("DELETE FROM Payment p WHERE p.order.id = :id").setParameter("id", ids[0]).executeUpdate();
                em.remove(em.find(Order.class, ids[0]));
                em.flush();
                em.remove(em.find(Customer.class, ids[1]));
                em.remove(em.find(Product.class, ids[2]));
            });
        }
    }
}
