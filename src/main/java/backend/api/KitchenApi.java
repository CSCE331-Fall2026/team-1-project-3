package backend.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.api.Dtos.KitchenOrder;
import backend.api.Dtos.Stock;
import backend.database.CashierRepository;
import backend.database.ManagerRepository;

/** Inventory and the order queue for kitchen staff; kept off /api/manager so it stays open when manager login arrives. */
@RestController
@RequestMapping("/api/kitchen")
public class KitchenApi {
    private final ManagerRepository repository = new ManagerRepository();
    private final CashierRepository orders = new CashierRepository();

    @GetMapping("/stock")
    public List<Stock> stock() throws Exception {
        return repository.loadStock().stream().map(Stock::of).toList();
    }

    @GetMapping("/orders")
    public List<KitchenOrder> pendingOrders() throws Exception {
        return orders.loadPendingOrders().stream().map(KitchenOrder::of).toList();
    }

    /** Idempotent: completing an already-completed order is 200 with changed=false. */
    @PostMapping("/orders/{orderId}/complete")
    public ResponseEntity<Map<String, Object>> complete(@PathVariable int orderId) throws Exception {
        if (orderId < 1)
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid order ID"));
        Boolean changed = orders.completeOrder(orderId);
        if (changed == null)
            return ResponseEntity.status(404).body(Map.of("error", "Order not found"));
        return ResponseEntity.ok(Map.of("orderId", orderId, "status", "COMPLETED", "changed", changed));
    }
}
