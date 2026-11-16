package at.htl.donershop.Controllers;

import at.htl.donershop.Entities.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/kunden")
public class CustomerController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    public List<Customer> getAllKunden(){
        return em.createQuery("SELECT c FROM Customer c", Customer.class)
                .getResultList();
    }

    @GetMapping("/{id}")
    public Customer getCustomerById(@PathVariable("id") Long id){
        Customer customer = em.find(Customer.class, id);
        if (customer == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Kunde nicht gefunden");
        }
        return customer;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Customer createCustomer(@RequestBody Customer customer) {
        customer.setId(null);
        em.persist(customer);
        em.flush();

        return customer;
    }


    @PutMapping("/{id}")
    @Transactional
    public Customer updateCustomer(
            @PathVariable Long id,
            @RequestBody Customer updatedCustomer) {

        Customer customer = getCustomerById(id);

        customer.setFirstName(updatedCustomer.getFirstName());
        customer.setLastName(updatedCustomer.getLastName());

        return customer;
    }


    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomer(@PathVariable Long id) {
        Customer customer = getCustomerById(id);

        em.remove(customer);
    }




}
