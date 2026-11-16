package at.htl.donershop.Controllers;

import at.htl.donershop.Entities.Payment;
import at.htl.donershop.Services.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) { this.service = service; }

    public record PaymentRequest(@NotNull @Positive Long orderId, @NotNull Boolean successful) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payment simulate(@Valid @RequestBody PaymentRequest request) {
        return service.simulate(request.orderId(), request.successful());
    }

    @GetMapping
    public List<Payment> getPayments(@RequestParam(required = false) Long orderId) {
        return service.getPayments(orderId);
    }

    @GetMapping("/{id}")
    public Payment getPayment(@PathVariable Long id) { return service.getPayment(id); }
}
