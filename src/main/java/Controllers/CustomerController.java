package Controllers;

import Entities.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/kunden")
public class CustomerController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    public List<Customer> getAllKunden(){
        return em.createQuery("SELECT c FROM Customer c", Customer.class)
                .getResultList();
    }

    @GetMapping("/kunden/{id}")
    public Customer getCustomerById(@PathVariable("id") Long id){
        return em.createQuery("select c from Customer c where c.id = :Id", Customer.class)
                .setParameter("Id", id)
                .getSingleResult();
    }




}
