package backend.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.api.ApiErrors;
import backend.api.KitchenApi;
import backend.database.CashierRepository.CheckoutOutcome;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Kitchen queue end to end: real checkout, then /api/kitchen/orders. Needs a disposable local PostgreSQL loaded with
 * DesignSql.sql then AddReceiptStatus.sql. Skipped unless TEST_DB_URL is set; it WIPES the tables, so only
 * localhost URLs are accepted.
 */
class KitchenOrdersTest {
    private static final String URL = System.getenv("TEST_DB_URL");
    private static final String USER = System.getenv("TEST_DB_USER");
    private static final String PASSWORD = System.getenv("TEST_DB_PASSWORD");

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new KitchenApi())
            .setControllerAdvice(new ApiErrors()).build();
    private HikariDataSource pool;

    @BeforeEach
    void setUp() throws SQLException {
        assumeTrue(URL != null, "TEST_DB_URL not set; skipping database tests");
        assertTrue(URL.startsWith("jdbc:postgresql://localhost") || URL.startsWith("jdbc:postgresql://127.0.0.1"),
                "TEST_DB_URL must point at a disposable local database");
        pool = new HikariDataSource(DatabaseConnection.config(URL, USER, PASSWORD));
        DatabaseConnection.usePool(pool);
        try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
            s.execute("TRUNCATE receipt_item, receipt, ingredient_list, item, inventory, user_timetable,"
                    + " app_user, category, location RESTART IDENTITY CASCADE");
            s.execute("INSERT INTO location VALUES (1, 'Test', 'College Station', 'TX')");
            s.execute("INSERT INTO category VALUES (1, 'Main', NULL)");
            s.execute("INSERT INTO app_user VALUES (1, 'cashier', 'x', 'Cashier')");
            s.execute("INSERT INTO item (item_id, category_id, price, name) VALUES (10, 1, 5.00, 'Orange Chicken'),"
                    + " (20, 1, 4.00, 'Chow Mein')");
            s.execute("INSERT INTO inventory (inventory_id, name, stock) VALUES (100, 'chicken', 10), (200, 'noodles', 10)");
            s.execute("INSERT INTO ingredient_list VALUES (10, 100), (20, 200)");
        }
    }

    @AfterEach
    void tearDown() {
        DatabaseConnection.usePool(null);
        if (pool != null)
            pool.close();
    }

    @Test
    void emptyQueueReturnsEmptyList() throws Exception {
        mvc.perform(get("/api/kitchen/orders")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void checkoutPersistsReceiptAndShowsUpAsPending() throws Exception {
        assertEquals(CheckoutOutcome.SUCCESS, checkout(2, 1));
        try (Connection c = pool.getConnection()) {
            assertEquals(1, queryInt(c, "SELECT COUNT(*) FROM receipt"));
            assertEquals(2, queryInt(c, "SELECT COUNT(*) FROM receipt_item WHERE receipt_id = 1"));
            assertEquals(8, stock(c, 100));
            assertEquals(9, stock(c, 200));
        }
        mvc.perform(get("/api/kitchen/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].timestamp").isNotEmpty())
                .andExpect(jsonPath("$[0].items.length()").value(2))
                .andExpect(jsonPath("$[0].items[0].name").value("Orange Chicken"))
                .andExpect(jsonPath("$[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$[0].items[1].name").value("Chow Mein"))
                .andExpect(jsonPath("$[0].items[1].quantity").value(1));
    }

    @Test
    void queueIsOldestFirstAndFailedCheckoutAddsNothing() throws Exception {
        assertEquals(CheckoutOutcome.SUCCESS, checkout(1, 0));
        assertEquals(CheckoutOutcome.FAILURE, checkout(50, 0)); // only 9 chicken left
        assertEquals(CheckoutOutcome.SUCCESS, checkout(0, 1));
        mvc.perform(get("/api/kitchen/orders"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].items[0].name").value("Orange Chicken"))
                .andExpect(jsonPath("$[1].items[0].name").value("Chow Mein"));
    }

    @Test
    void completingRemovesFromQueueWithoutDeductingInventoryAgain() throws Exception {
        assertEquals(CheckoutOutcome.SUCCESS, checkout(2, 1));

        mvc.perform(post("/api/kitchen/orders/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.changed").value(true));

        mvc.perform(get("/api/kitchen/orders")).andExpect(jsonPath("$.length()").value(0));
        try (Connection c = pool.getConnection()) {
            assertEquals(8, stock(c, 100), "inventory is deducted at checkout only");
            assertEquals(9, stock(c, 200));
            assertEquals(2, queryInt(c, "SELECT COUNT(*) FROM receipt_item WHERE receipt_id = 1"));
        }
    }

    @Test
    void repeatedCompletionIsSafe() throws Exception {
        assertEquals(CheckoutOutcome.SUCCESS, checkout(1, 0));
        mvc.perform(post("/api/kitchen/orders/1/complete")).andExpect(jsonPath("$.changed").value(true));

        mvc.perform(post("/api/kitchen/orders/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.changed").value(false));
        try (Connection c = pool.getConnection()) {
            assertEquals(9, stock(c, 100));
        }
    }

    @Test
    void concurrentCompletionsChangeTheOrderOnce() throws Exception {
        assertEquals(CheckoutOutcome.SUCCESS, checkout(1, 0));
        CashierRepository repository = new CashierRepository();
        ExecutorService threads = Executors.newFixedThreadPool(2); // pool max is 2 connections
        try {
            List<Future<Boolean>> results = threads.invokeAll(List.<Callable<Boolean>>of(
                    () -> repository.completeOrder(1), () -> repository.completeOrder(1)));
            int changed = 0;
            for (Future<Boolean> result : results)
                if (result.get())
                    changed++;
            assertEquals(1, changed);
        } finally {
            threads.shutdown();
        }
    }

    @Test
    void unknownAndInvalidOrderIds() throws Exception {
        mvc.perform(post("/api/kitchen/orders/999/complete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found"));
        mvc.perform(post("/api/kitchen/orders/0/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid order ID"));
        mvc.perform(post("/api/kitchen/orders/abc/complete")).andExpect(status().isBadRequest());
    }

    /** One cart line per item with a positive quantity, priced at the seeded list price. */
    private static CheckoutOutcome checkout(int chicken, int noodles) {
        TreeMap<Integer, Integer> cart = new TreeMap<>();
        TreeMap<Integer, BigDecimal> prices = new TreeMap<>();
        TreeMap<Integer, BigDecimal> totals = new TreeMap<>();
        add(cart, prices, totals, 10, chicken, new BigDecimal("5.00"));
        add(cart, prices, totals, 20, noodles, new BigDecimal("4.00"));
        return new CashierRepository().checkout(1, 1, cart, prices, totals);
    }

    private static void add(TreeMap<Integer, Integer> cart, TreeMap<Integer, BigDecimal> prices,
            TreeMap<Integer, BigDecimal> totals, int itemId, int quantity, BigDecimal price) {
        if (quantity < 1)
            return;
        cart.put(itemId, quantity);
        prices.put(itemId, price);
        totals.put(itemId, price.multiply(BigDecimal.valueOf(quantity)));
    }

    private static int stock(Connection c, int inventoryId) throws SQLException {
        return queryInt(c, "SELECT stock FROM inventory WHERE inventory_id = " + inventoryId);
    }

    private static int queryInt(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }
}
