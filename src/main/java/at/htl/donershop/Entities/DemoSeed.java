package at.htl.donershop.Entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Persistente Markierung, unabhängig von bearbeitbaren Demo-Kunden und Produkten. */
@Entity
public class DemoSeed {
    @Id
    private String id;

    protected DemoSeed() {}
    public DemoSeed(String id) { this.id = id; }
}
