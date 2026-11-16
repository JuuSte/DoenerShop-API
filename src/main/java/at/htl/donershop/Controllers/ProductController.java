
package at.htl.donershop.Controllers;

import at.htl.donershop.Entities.Category;
import at.htl.donershop.Entities.Product;
import at.htl.donershop.Entities.ProductIngredient;
import at.htl.donershop.Entities.Inventory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

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


    @GetMapping("/{id}/availability")
    public Map<String, Object> checkAvailability(@PathVariable Long id) {
        Product product = getById(id);

        List<ProductIngredient> recipe = em.createQuery(
                "SELECT pi FROM ProductIngredient pi WHERE pi.product.id = :id",
                ProductIngredient.class
        ).setParameter("id", id).getResultList();

        List<Map<String, Object>> missing = new ArrayList<>();
        for (ProductIngredient item : recipe) {
            List<Inventory> stock = em.createQuery(
                    "SELECT i FROM Inventory i WHERE i.ingredient.id = :ingredientId",
                    Inventory.class
            ).setParameter("ingredientId", item.getIngredient().getId()).getResultList();

            double available = stock.isEmpty() ? 0 : stock.get(0).getQuantity();
            if (available < item.getQuantity()) {
                Map<String, Object> shortage = new LinkedHashMap<>();
                shortage.put("ingredient", item.getIngredient().getName());
                shortage.put("required", item.getQuantity());
                shortage.put("available", available);
                shortage.put("unit", item.getIngredient().getUnit());
                missing.add(shortage);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", product.getId());
        result.put("productName", product.getName());
        result.put("available", !recipe.isEmpty() && missing.isEmpty());
        result.put("message", recipe.isEmpty()
                ? "Für dieses Produkt wurden noch keine Zutaten hinterlegt."
                : (missing.isEmpty() ? "Alle Zutaten sind ausreichend vorhanden."
                : "Nicht genügend Zutaten auf Lager."));
        result.put("missingIngredients", missing);
        return result;
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
