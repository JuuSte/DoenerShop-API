
package at.htl.donershop.Controllers;

import at.htl.donershop.Entities.Ingredient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    public List<Ingredient> getAll() {
        return em.createQuery(
                "SELECT i FROM Ingredient i", Ingredient.class
        ).getResultList();
    }

    @GetMapping("/{id}")
    public Ingredient getById(@PathVariable Long id) {
        Ingredient ingredient = em.find(Ingredient.class, id);

        if (ingredient == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Zutat nicht gefunden");
        }

        return ingredient;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Ingredient create(@RequestBody Ingredient ingredient) {
        ingredient.setId(null);
        em.persist(ingredient);
        em.flush();
        return ingredient;
    }

    @PutMapping("/{id}")
    @Transactional
    public Ingredient update(@PathVariable Long id,
                             @RequestBody Ingredient updated) {
        Ingredient ingredient = getById(id);
        ingredient.setName(updated.getName());
        ingredient.setUnit(updated.getUnit());
        ingredient.setActive(updated.isActive());
        return ingredient;
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Ingredient ingredient = getById(id);
        em.remove(ingredient);
    }
}
