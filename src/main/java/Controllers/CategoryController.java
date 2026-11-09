
package Controllers;

import Entities.Category;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    public List<Category> getAll() {
        return em.createQuery(
                "SELECT c FROM Category c", Category.class
        ).getResultList();
    }

    @GetMapping("/{id}")
    public Category getById(@PathVariable Long id) {
        Category category = em.find(Category.class, id);

        if (category == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Kategorie nicht gefunden");
        }

        return category;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Category create(@RequestBody Category category) {
        category.setId(null);
        em.persist(category);
        em.flush();
        return category;
    }

    @PutMapping("/{id}")
    @Transactional
    public Category update(@PathVariable Long id,
                           @RequestBody Category updated) {
        Category category = getById(id);
        category.setName(updated.getName());
        return category;
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Category category = getById(id);
        em.remove(category);
    }
}
