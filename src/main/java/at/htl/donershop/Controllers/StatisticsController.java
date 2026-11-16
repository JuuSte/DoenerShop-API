package at.htl.donershop.Controllers;

import at.htl.donershop.Services.StatisticsService;
import at.htl.donershop.Entities.Order;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService service;

    public StatisticsController(StatisticsService service) { this.service = service; }

    @GetMapping("/revenue/daily")
    public BigDecimal dailyRevenue(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.dailyRevenue(date);
    }

    @GetMapping("/revenue/monthly")
    public BigDecimal monthlyRevenue(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.monthlyRevenue(month);
    }

    @GetMapping("/best-selling-products")
    public List<StatisticsService.ProductSales> bestSellingProducts() { return service.bestSellingProducts(); }

    @GetMapping("/average-order-value")
    public BigDecimal averageOrderValue(@RequestParam(defaultValue = Order.COMPLETED) String completedStatus) {
        return service.averageOrderValue(completedStatus);
    }
}
