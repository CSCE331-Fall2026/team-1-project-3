package backend.api;

import backend.api.Dtos.CheckoutRequest;
import backend.api.Dtos.MenuItem;
import backend.database.CashierRepository;
import backend.database.CashierRepository.CheckoutOutcome;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Replaces the cashier half of CashierController's database work. */
@RestController
@RequestMapping("/api")
public class CashierApi {
    private final CashierRepository repository = new CashierRepository();

    @GetMapping("/menu")
    public List<MenuItem> menu() throws Exception {
        return repository.loadMenu().stream().map(MenuItem::of).toList();
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> checkout(@RequestBody CheckoutRequest request) {
        if (request.locationId() == null || request.userId() == null
                || request.items() == null || request.items().isEmpty())
            return rejected();

        // Same inputs the JavaFX controller's flattenCart() produced.
        TreeMap<Integer, Integer> quantities = new TreeMap<>();
        TreeMap<Integer, BigDecimal> listPrices = new TreeMap<>();
        TreeMap<Integer, BigDecimal> totals = new TreeMap<>();
        for (CheckoutRequest.Line line : request.items()) {
            if (line == null || line.itemId() == null || line.quantity() == null || line.quantity() < 1
                    || !Money.valid(line.listPriceCents()) || !Money.valid(line.totalCents())
                    || quantities.put(line.itemId(), line.quantity()) != null) // null-check also rejects duplicates
                return rejected();
            listPrices.put(line.itemId(), Money.fromCents(line.listPriceCents()));
            totals.put(line.itemId(), Money.fromCents(line.totalCents()));
        }

        CheckoutOutcome outcome = repository.checkout(request.locationId(), request.userId(),
                quantities, listPrices, totals);
        return ResponseEntity.ok(Map.of("outcome", outcome.name())); // SUCCESS | FAILURE | UNCERTAIN
    }

    private static ResponseEntity<Map<String, String>> rejected() {
        return ResponseEntity.badRequest().body(Map.of("outcome", "FAILURE"));
    }
}
