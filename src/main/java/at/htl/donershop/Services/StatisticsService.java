package at.htl.donershop.Services;

import at.htl.donershop.Entities.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class StatisticsService {
    @PersistenceContext
    private EntityManager em;

    public record ProductSales(Long productId, String name, long quantity) {}

    public BigDecimal dailyRevenue(LocalDate date) {
        return revenue(date, date.plusDays(1));
    }

    public BigDecimal monthlyRevenue(YearMonth month) {
        return revenue(month.atDay(1), month.plusMonths(1).atDay(1));
    }

    private BigDecimal revenue(LocalDate start, LocalDate end) {
        BigDecimal total = em.createQuery("SELECT SUM(p.amount) FROM Payment p WHERE p.status = :status "
                        + "AND p.paymentTime >= :start AND p.paymentTime < :end", BigDecimal.class)
                .setParameter("status", Payment.Status.SUCCESSFUL)
                .setParameter("start", start.atStartOfDay()).setParameter("end", end.atStartOfDay())
                .getSingleResult();
        return total == null ? new BigDecimal("0.00") : total.setScale(2, RoundingMode.HALF_UP);
    }

    public List<ProductSales> bestSellingProducts() {
        Map<Long, ProductSales> sales = new HashMap<>();
        // EXISTS zählt jede Position einmal, unabhängig von der Anzahl der Zahlungsversuche.
        for (String entity : List.of("Order_Item", "Order_Item_Custom")) {
            List<Object[]> rows = em.createQuery("SELECT i.product.id, i.product.name, SUM(i.quantity) FROM "
                            + entity + " i WHERE EXISTS (SELECT p.id FROM Payment p WHERE p.order = i.order "
                            + "AND p.status = :status) GROUP BY i.product.id, i.product.name", Object[].class)
                    .setParameter("status", Payment.Status.SUCCESSFUL).getResultList();
            for (Object[] row : rows) {
                Long id = (Long) row[0];
                long quantity = ((Number) row[2]).longValue();
                ProductSales previous = sales.get(id);
                sales.put(id, new ProductSales(id, (String) row[1],
                        quantity + (previous == null ? 0 : previous.quantity())));
            }
        }
        return sales.values().stream().sorted(Comparator.comparingLong(ProductSales::quantity).reversed()
                .thenComparing(ProductSales::productId)).toList();
    }

    public BigDecimal averageOrderValue(String completedStatus) {
        if (completedStatus == null || completedStatus.isBlank() || "OPEN".equals(completedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Abschlussstatus erforderlich; OPEN ist nicht abgeschlossen");
        }
        List<BigDecimal> amounts = em.createQuery("SELECT p.amount FROM Payment p WHERE p.status = :status "
                        + "AND p.order.status = :completedStatus", BigDecimal.class)
                .setParameter("status", Payment.Status.SUCCESSFUL)
                .setParameter("completedStatus", completedStatus).getResultList();
        if (amounts.isEmpty()) { return new BigDecimal("0.00"); }
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), 2, RoundingMode.HALF_UP);
    }
}
