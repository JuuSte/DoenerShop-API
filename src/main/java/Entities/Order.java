package Entities;

import jakarta.persistence.*;

@Entity
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)

    private Long id;

    @ManyToOne
    @JoinColumn(name="customer_id", nullable = false)
    private Customer kunde;

    protected Order(){

    }

    public Order(Customer kunde){
        this.kunde = kunde;
    }

    public Long getId(){ return this.id; }

    public Customer getKunde(){ return this.kunde; }

    public void setKunde(Customer kunde){ this.kunde = kunde; }



}
