package at.htl.donershop.Entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
public class Payment {
    public enum Status { SUCCESSFUL, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime paymentTime;

    protected Payment() {}

    public Payment(Order order, BigDecimal amount, Status status, LocalDateTime paymentTime) {
        this.order = order;
        this.amount = amount;
        this.status = status;
        this.paymentTime = paymentTime;
    }

    public Long getId() { return id; }
    @JsonIgnore
    public Order getOrder() { return order; }
    public Long getOrderId() { return order.getId(); }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public LocalDateTime getPaymentTime() { return paymentTime; }
}
