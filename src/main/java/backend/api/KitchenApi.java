package backend.api;

import backend.api.Dtos.Stock;
import backend.database.ManagerRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only inventory for kitchen staff; kept off /api/manager so it stays open when manager login arrives. */
@RestController
@RequestMapping("/api/kitchen")
public class KitchenApi {
    private final ManagerRepository repository = new ManagerRepository();

    @GetMapping("/stock")
    public List<Stock> stock() throws Exception {
        return repository.loadStock().stream().map(Stock::of).toList();
    }
}
