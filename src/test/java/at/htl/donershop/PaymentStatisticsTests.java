package at.htl.donershop;

import at.htl.donershop.Controllers.PaymentController;
import at.htl.donershop.Controllers.StatisticsController;
import at.htl.donershop.Entities.*;
import at.htl.donershop.Services.PaymentService;
import at.htl.donershop.Services.StatisticsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class PaymentStatisticsTests {
    @Autowired EntityManager em;
    @Autowired PaymentService payments;
    @Autowired StatisticsService statistics;
    private Product product;
    private Customer customer;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        customer = new Customer("Test", "Kunde");
        em.persist(customer);
        product = new Product();
        product.setName("Döner");
        product.setPrice(99); // Verwendet wird der gespeicherte Positionspreis.
        em.persist(product);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(payments), new StatisticsController(statistics)).build();
    }

    private Order order(String status, int quantity, double price) {
        Order order = new Order(customer);
        order.setStatus(status);
        order.addItem(new Order_Item(order, product, quantity, price));
        em.persist(order);
        em.flush();
        return order;
    }

    @Test
    void failedAttemptAndSuccessfulRetryArePersistedAndOnlySuccessCounts() {
        Order order = order("OPEN", 3, 0.1);
        Order_Item_Custom custom = new Order_Item_Custom(order, product, 2, 1.25, "ohne Zwiebel");
        order.addCustomItem(custom);
        em.persist(custom);
        Payment failed = payments.simulate(order.getId(), false);
        assertThat(statistics.dailyRevenue(LocalDate.now())).isEqualByComparingTo("0.00");
        Payment success = payments.simulate(order.getId(), true);
        Long orderId = order.getId();
        em.flush();
        em.clear();
        assertThat(payments.getPayment(failed.getId()).getStatus()).isEqualTo(Payment.Status.FAILED);
        assertThat(payments.getPayment(success.getId()).getAmount()).isEqualByComparingTo("2.80");
        assertThat(payments.getPayments(orderId)).hasSize(2);
        assertThat(statistics.dailyRevenue(LocalDate.now())).isEqualByComparingTo("2.80");
        assertThat(statistics.monthlyRevenue(YearMonth.now())).isEqualByComparingTo("2.80");
        assertThat(statistics.bestSellingProducts()).containsExactly(
                new StatisticsService.ProductSales(product.getId(), "Döner", 5));
    }

    @Test
    void furtherPaymentsForPaidOrderAreRejected() {
        Order order = order("OPEN", 1, 5);
        payments.simulate(order.getId(), true);
        assertThatThrownBy(() -> payments.simulate(order.getId(), true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void revenueUsesPaymentTimeAndExclusivePeriodEnd() {
        Order order = order("OPEN", 1, 5);
        em.persist(new Payment(order, new BigDecimal("3.10"), Payment.Status.SUCCESSFUL,
                LocalDateTime.of(2026, 1, 1, 0, 0)));
        em.persist(new Payment(order("OPEN", 1, 7), new BigDecimal("7.20"), Payment.Status.SUCCESSFUL,
                LocalDateTime.of(2026, 1, 31, 23, 59, 59)));
        em.persist(new Payment(order("OPEN", 1, 9), new BigDecimal("9.00"), Payment.Status.SUCCESSFUL,
                LocalDateTime.of(2026, 2, 1, 0, 0)));
        em.persist(new Payment(order, new BigDecimal("100.00"), Payment.Status.FAILED,
                LocalDateTime.of(2026, 1, 15, 12, 0)));
        assertThat(statistics.dailyRevenue(LocalDate.of(2026, 1, 1))).isEqualByComparingTo("3.10");
        assertThat(statistics.monthlyRevenue(YearMonth.of(2026, 1))).isEqualByComparingTo("10.30");
        assertThat(statistics.monthlyRevenue(YearMonth.of(2025, 12))).isEqualByComparingTo("0.00");
    }

    @Test
    void averageRequiresExplicitCompletionStatusAndSuccessfulPayment() {
        // Der Bestellcontroller erlaubt freie Statuswerte. Diese Tests verwenden bewusst einen Testwert.
        payments.simulate(order("TEST_FINISHED", 1, 10).getId(), true);
        payments.simulate(order("TEST_FINISHED", 1, 5.01).getId(), true);
        payments.simulate(order("TEST_FINISHED", 1, 100).getId(), false);
        payments.simulate(order("OPEN", 1, 200).getId(), true);
        assertThat(statistics.averageOrderValue("TEST_FINISHED")).isEqualByComparingTo("7.51");
        assertThat(statistics.averageOrderValue("UNUSED_TEST_STATUS")).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> statistics.averageOrderValue("OPEN")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void emptyStatistics() {
        assertThat(statistics.dailyRevenue(LocalDate.now())).isEqualByComparingTo("0.00");
        assertThat(statistics.bestSellingProducts()).isEmpty();
    }

    @Test
    void restEndpointsValidateRequestsAndExposePaymentsAndStatistics() throws Exception {
        Order order = order("OPEN", 2, 5.5);
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + order.getId() + ",\"successful\":false}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + order.getId() + ",\"successful\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(11.0))
                .andExpect(jsonPath("$.orderId").value(order.getId()));
        mvc.perform(get("/api/payments").param("orderId", order.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/statistics/revenue/daily").param("date", LocalDate.now().toString()))
                .andExpect(status().isOk()).andExpect(content().string("11.00"));
        mvc.perform(get("/api/statistics/revenue/monthly").param("month", YearMonth.now().toString()))
                .andExpect(status().isOk()).andExpect(content().string("11.00"));
        mvc.perform(get("/api/statistics/best-selling-products"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(2));
        mvc.perform(get("/api/statistics/average-order-value").param("completedStatus", "TEST_FINISHED"))
                .andExpect(status().isOk()).andExpect(content().string("0.00"));
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/payments/999999" )).andExpect(status().isNotFound());
        mvc.perform(get("/api/statistics/revenue/daily").param("date", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingOrderIsRejected() {
        assertThatThrownBy(() -> payments.simulate(Long.MAX_VALUE, true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void emptyOrderIsRejected() {
        Order order = new Order(customer);
        em.persist(order);
        assertThatThrownBy(() -> payments.simulate(order.getId(), true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }
}
