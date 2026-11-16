package at.htl.donershop.Services;

import at.htl.donershop.Entities.Order;
import at.htl.donershop.Entities.Payment;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {
    @PersistenceContext
    private EntityManager em;

    @Transactional
    public Payment simulate(Long orderId, boolean successful) {
        Order order = em.find(Order.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bestellung nicht gefunden");
        }
        Long paid = em.createQuery("SELECT COUNT(p) FROM Payment p WHERE p.order = :order AND p.status = :status", Long.class)
                .setParameter("order", order).setParameter("status", Payment.Status.SUCCESSFUL).getSingleResult();
        if (paid > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bestellung bereits erfolgreich bezahlt");
        }
        if (order.getItems().isEmpty() && order.getCustomItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bestellung enthält keine Positionen");
        }
        BigDecimal amount = BigDecimal.ZERO;
        for (var item : order.getItems()) {
            amount = amount.add(lineTotal(item.getUnitPrice(), item.getQuantity()));
        }
        for (var item : order.getCustomItems()) {
            amount = amount.add(lineTotal(item.getUnitPrice(), item.getQuantity()));
        }
        Payment payment = new Payment(order, amount.setScale(2, RoundingMode.HALF_UP),
                successful ? Payment.Status.SUCCESSFUL : Payment.Status.FAILED, LocalDateTime.now());
        em.persist(payment);
        em.flush();
        return payment;
    }

    private BigDecimal lineTotal(double price, Integer quantity) {
        if (!Double.isFinite(price) || price < 0 || quantity == null || quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Menge oder Preis");
        }
        return BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(quantity));
    }

    public List<Payment> getPayments(Long orderId) {
        if (orderId == null) {
            return em.createQuery("SELECT p FROM Payment p ORDER BY p.id", Payment.class).getResultList();
        }
        if (em.find(Order.class, orderId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bestellung nicht gefunden");
        }
        return em.createQuery("SELECT p FROM Payment p WHERE p.order.id = :id ORDER BY p.id", Payment.class)
                .setParameter("id", orderId).getResultList();
    }

    public Payment getPayment(Long id) {
        Payment payment = em.find(Payment.class, id);
        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Zahlung nicht gefunden");
        }
        return payment;
    }
}
