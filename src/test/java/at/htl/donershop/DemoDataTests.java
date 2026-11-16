package at.htl.donershop;

import at.htl.donershop.Entities.Customer;
import at.htl.donershop.Services.DemoDataInitializer;
import at.htl.donershop.Services.StatisticsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.demo.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:demo-test;DB_CLOSE_DELAY=-1"})
@Transactional
class DemoDataTests {
    @Autowired DemoDataInitializer initializer;
    @Autowired StatisticsService statistics;
    @Autowired EntityManager em;

    @Test
    void startupCreatesUsefulDataAndRepeatedStartupPreservesExistingData() {
        assertThat(statistics.dailyRevenue(DemoDataInitializer.DEMO_DATE)).isEqualByComparingTo("54.50");
        assertThat(statistics.monthlyRevenue(YearMonth.from(DemoDataInitializer.DEMO_DATE)))
                .isEqualByComparingTo("54.50");
        assertThat(statistics.averageOrderValue("COMPLETED")).isEqualByComparingTo("18.17");
        assertThat(statistics.bestSellingProducts()).extracting(StatisticsService.ProductSales::quantity)
                .containsExactly(4L, 3L, 3L);
        assertThat(em.createQuery("SELECT COUNT(p) FROM Payment p", Long.class).getSingleResult()).isEqualTo(5);
        assertThat(em.createQuery("SELECT COUNT(o) FROM Order o", Long.class).getSingleResult()).isEqualTo(5);
        Customer existing = new Customer("Vorhandener", "Kunde");
        em.persist(existing);
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());
        em.flush();
        em.clear();
        assertThat(em.find(Customer.class, existing.getId()).getFirstName()).isEqualTo("Vorhandener");
        assertThat(em.createQuery("SELECT COUNT(c) FROM Customer c", Long.class).getSingleResult()).isEqualTo(3);
        assertThat(em.createQuery("SELECT COUNT(p) FROM Payment p", Long.class).getSingleResult()).isEqualTo(5);
    }
}
