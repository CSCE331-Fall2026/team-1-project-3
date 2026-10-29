package backend.api;

import backend.api.Dtos.MenuItem;
import backend.api.Dtos.NewMenuItem;
import backend.api.Dtos.Point;
import backend.api.Dtos.PriceChange;
import backend.api.Dtos.Receipt;
import backend.api.Dtos.Stock;
import backend.api.Dtos.StockChange;
import backend.api.Dtos.StockResult;
import backend.api.Dtos.XPoint;
import backend.database.ManagerRepository;
import backend.database.ManagerRepository.GraphPoint;
import backend.models.StockUpdateResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Replaces the database work done by ManagerController and the two list cells. */
@RestController
@RequestMapping("/api/manager")
public class ManagerApi {
    private static final int MAX_STOCK = 99_999_999;
    private final ManagerRepository repository = new ManagerRepository();

    /* ---- Menu items ---- */

    @GetMapping("/menu")
    public List<MenuItem> menu() throws Exception {
        return repository.loadDashboard().getMenu().stream().map(MenuItem::of).toList();
    }

    @PostMapping("/menu")
    public ResponseEntity<?> addMenuItem(@RequestBody NewMenuItem body) throws Exception {
        if (body.itemId() == null || body.categoryId() == null || !Money.valid(body.priceCents())
                || body.name() == null || body.name().isBlank())
            return invalid("Invalid menu item");
        boolean added = repository.addMenuItem(body.itemId(), body.categoryId(), orEmpty(body.nutrition()),
                Money.fromCents(body.priceCents()), orEmpty(body.unitSize()), body.name());
        return ResponseEntity.status(201).body(Map.of("added", added));
    }

    @PatchMapping("/menu/{id}/price")
    public ResponseEntity<?> changePrice(@PathVariable int id, @RequestBody PriceChange body) throws Exception {
        if (!Money.valid(body.priceCents())) return invalid("Invalid price");
        Double price = repository.updatePrice(id, Money.fromCents(body.priceCents()));
        if (price == null) return ResponseEntity.status(404).body(Map.of("error", "Item not found"));
        return ResponseEntity.ok(Map.of("priceCents", Math.round(price * 100)));
    }

    @DeleteMapping("/menu/{id}")
    public Map<String, Boolean> removeMenuItem(@PathVariable int id) throws Exception {
        return Map.of("removed", repository.removeMenuItem(id));
    }

    /* ---- Ingredient stock ---- */

    @GetMapping("/stock")
    public List<Stock> stock() throws Exception {
        return repository.loadDashboard().getStock().stream().map(Stock::of).toList();
    }

    @PatchMapping("/stock/{id}")
    public ResponseEntity<?> changeStock(@PathVariable int id, @RequestBody StockChange body) throws Exception {
        if (body.delta() == null || Math.abs((long) body.delta()) > MAX_STOCK) return invalid("Invalid stock change");
        StockUpdateResult result = repository.updateStock(id, body.delta());
        return ResponseEntity.ok(new StockResult(result.getStock(), result.isUpdated(), result.isMissing()));
    }

    /* ---- Charts. Omit start and end for the whole period. ---- */

    @GetMapping("/sales")
    public ResponseEntity<?> sales(@RequestParam(required = false) String start,
            @RequestParam(required = false) String end) throws Exception {
        boolean hasStart = start != null && !start.isBlank();
        boolean hasEnd = end != null && !end.isBlank();
        if (hasStart != hasEnd) return invalid("Provide both start and end");
        if (hasStart) return ResponseEntity.ok(points(repository.loadSalesGraph(start.trim(), end.trim())));
        List<Point> whole = repository.loadDashboard().getSales().stream()
                .map(s -> new Point("#" + s.getID() + "-" + s.getName() + ' ' + s.getQuantity(), s.getQuantity()))
                .toList();
        return ResponseEntity.ok(whole);
    }

    @GetMapping("/stock-usage")
    public ResponseEntity<?> stockUsage(@RequestParam(required = false) String start,
            @RequestParam(required = false) String end) throws Exception {
        boolean hasStart = start != null && !start.isBlank();
        boolean hasEnd = end != null && !end.isBlank();
        if (hasStart != hasEnd) return invalid("Provide both start and end");
        if (hasStart) return ResponseEntity.ok(points(repository.loadStockGraph(start.trim(), end.trim())));
        List<Point> whole = repository.loadDashboard().getStockGraphRecord().stream()
                .map(s -> new Point("#" + s.getID() + "-" + s.getName() + ' ' + s.getQuantity(), s.getQuantity()))
                .toList();
        return ResponseEntity.ok(whole);
    }

    /* ---- Order history and X report ---- */

    @GetMapping("/receipts")
    public List<Receipt> receipts() throws Exception {
        return repository.loadDashboard().getReceipts().stream().map(Receipt::of).toList();
    }

    @GetMapping("/x-report")
    public List<XPoint> xReport() throws Exception {
        return repository.loadXReport().stream().map(XPoint::of).toList();
    }

    /* ---- helpers ---- */

    private static List<Point> points(List<GraphPoint> graph) {
        return graph.stream().map(p -> new Point(p.label, p.value)).toList();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static ResponseEntity<Map<String, String>> invalid(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
