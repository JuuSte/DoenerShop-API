package at.htl.donershop.Entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class DemoSeed {
    @Id
    private String id;

    protected DemoSeed() {}
    public DemoSeed(String id) { this.id = id; }
}
