package at.htl.donershop.Controllers;

import at.htl.donershop.Entities.Ingredient;
import at.htl.donershop.Entities.Product;
import at.htl.donershop.Entities.ProductIngredient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/product-ingredients")
public class ProductIngredientController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    public List<ProductIngredient> getAll() {
        return em.createQuery(
                "SELECT pi FROM ProductIngredient pi",
                ProductIngredient.class
        ).getResultList();
    }

    @GetMapping("/product/{productId}")
    public List<ProductIngredient> getByProduct(
            @PathVariable Long productId) {
        return em.createQuery(
                "SELECT pi FROM ProductIngredient pi " +
                        "WHERE pi.product.id = :id",
                ProductIngredient.class
        ).setParameter("id", productId).getResultList();
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public ProductIngredient create(
            @RequestBody ProductIngredient data) {

        if (data.getProduct() == null ||
                data.getProduct().getId() == null ||
                data.getIngredient() == null ||
                data.getIngredient().getId() == null ||
                data.getQuantity() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ungültige Eingabe");
        }

        Product product = em.find(
                Product.class, data.getProduct().getId());

        Ingredient ingredient = em.find(
                Ingredient.class, data.getIngredient().getId());

        if (product == null || ingredient == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Produkt oder Zutat existiert nicht");
        }

        data.setId(null);
        data.setProduct(product);
        data.setIngredient(ingredient);

        em.persist(data);
        em.flush();

        return data;
    }


    @PutMapping("/{id}")
    @Transactional
    public ProductIngredient update(@PathVariable Long id,
                                   @RequestBody ProductIngredient updated) {
        ProductIngredient pi = em.find(ProductIngredient.class, id);
        if (pi == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Zuordnung nicht gefunden");
        }

        if (updated.getProduct() == null || updated.getProduct().getId() == null ||
                updated.getIngredient() == null || updated.getIngredient().getId() == null ||
                updated.getQuantity() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ungültige Eingabe");
        }

        Product product = em.find(Product.class, updated.getProduct().getId());
        Ingredient ingredient = em.find(Ingredient.class, updated.getIngredient().getId());
        if (product == null || ingredient == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Produkt oder Zutat existiert nicht");
        }

        pi.setProduct(product);
        pi.setIngredient(ingredient);
        pi.setQuantity(updated.getQuantity());
        return pi;
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ProductIngredient pi =
                em.find(ProductIngredient.class, id);

        if (pi == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Zuordnung nicht gefunden");
        }

        em.remove(pi);
    }
}
