
package at.htl.donershop.controller;

import at.htl.donershop.entity.Category;
import at.htl.donershop.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    public List<Product> getAll() {
        return em.createQuery(
                "SELECT p FROM Product p", Product.class
        ).getResultList();
    }

    @GetMapping("/{id}")
    public Product getById(@PathVariable Long id) {
        Product product = em.find(Product.class, id);

        if (product == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Produkt nicht gefunden");
        }

        return product;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@RequestBody Product product) {
        Category category = findCategory(product);

        product.setId(null);
        product.setCategory(category);

        em.persist(product);
        em.flush();

        return product;
    }

    @PutMapping("/{id}")
    @Transactional
    public Product update(@PathVariable Long id,
                          @RequestBody Product updated) {

        Product product = getById(id);
        Category category = findCategory(updated);

        product.setName(updated.getName());
        product.setDescription(updated.getDescription());
        product.setPrice(updated.getPrice());
        product.setActive(updated.isActive());
        product.setCategory(category);

        return product;
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Product product = getById(id);
        em.remove(product);
    }

    private Category findCategory(Product product) {
        if (product.getCategory() == null ||
                product.getCategory().getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Kategorie fehlt");
        }

        Category category = em.find(
                Category.class, product.getCategory().getId());

        if (category == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Kategorie existiert nicht");
        }

        return category;
    }
}
