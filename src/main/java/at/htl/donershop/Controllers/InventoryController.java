package at.htl.donershop.Controllers;

import at.htl.donershop.Entities.Ingredient;
import at.htl.donershop.Entities.Inventory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    public List<Inventory> getAll() {
        return em.createQuery(
                "SELECT i FROM Inventory i", Inventory.class
        ).getResultList();
    }

    @GetMapping("/{id}")
    public Inventory getById(@PathVariable Long id) {
        Inventory inventory = em.find(Inventory.class, id);
        if (inventory == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Lagerbestand nicht gefunden");
        }
        return inventory;
    }

    @GetMapping("/ingredient/{ingredientId}")
    public Inventory getByIngredient(@PathVariable Long ingredientId) {
        List<Inventory> result = em.createQuery(
                "SELECT i FROM Inventory i WHERE i.ingredient.id = :id",
                Inventory.class
        ).setParameter("id", ingredientId).getResultList();

        if (result.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Kein Lagerbestand für diese Zutat");
        }
        return result.get(0);
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Inventory create(@RequestBody Inventory data) {
        if (data.getIngredient() == null ||
                data.getIngredient().getId() == null ||
                data.getQuantity() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ungültige Eingabe");
        }

        Ingredient ingredient = em.find(
                Ingredient.class, data.getIngredient().getId());
        if (ingredient == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Zutat existiert nicht");
        }

        Long count = em.createQuery(
                "SELECT COUNT(i) FROM Inventory i WHERE i.ingredient.id = :id",
                Long.class
        ).setParameter("id", ingredient.getId()).getSingleResult();

        if (count > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Für diese Zutat existiert bereits ein Lagerbestand");
        }

        data.setId(null);
        data.setIngredient(ingredient);
        em.persist(data);
        em.flush();
        return data;
    }

    @PutMapping("/{id}")
    @Transactional
    public Inventory update(@PathVariable Long id, @RequestBody Inventory updated) {
        Inventory inventory = getById(id);

        if (updated.getQuantity() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Lagerbestand darf nicht negativ sein");
        }

        inventory.setQuantity(updated.getQuantity());
        return inventory;
    }

    @PatchMapping("/{id}/quantity")
    @Transactional
    public Inventory updateQuantity(@PathVariable Long id,
                                    @RequestParam double quantity) {
        if (quantity < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Lagerbestand darf nicht negativ sein");
        }

        Inventory inventory = getById(id);
        inventory.setQuantity(quantity);
        return inventory;
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        em.remove(getById(id));
    }
}
