package at.htl.donershop.Entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_order")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer kunde;

    private LocalDateTime orderDate;

    private String status;

    @OneToMany(mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<Order_Item> items = new ArrayList<>();

    @OneToMany(mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<Order_Item_Custom> customItems = new ArrayList<>();

    public Order() {
    }

    public Order(Customer kunde) {
        this.kunde = kunde;
        this.orderDate = LocalDateTime.now();
        this.status = "OPEN";
    }

    public Long getId() {
        return id;
    }

    public Customer getKunde() {
        return kunde;
    }

    public void setKunde(Customer kunde) {
        this.kunde = kunde;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @JsonIgnore
    public List<Order_Item> getItems() {
        return items;
    }

    public List<Order_Item_Custom> getCustomItems() {
        return customItems;
    }

    public void addItem(Order_Item item) {
        items.add(item);
        item.setOrder(this);
    }

    public void addCustomItem(Order_Item_Custom item) {
        customItems.add(item);
        item.setOrder(this);
    }
}