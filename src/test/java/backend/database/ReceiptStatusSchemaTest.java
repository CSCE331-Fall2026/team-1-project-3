package backend.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import backend.database.CashierRepository.CheckoutOutcome;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * receipt.status on a fresh DesignSql.sql database and via AddReceiptStatus.sql on a pre-#45 one. Same disposable
 * database as KitchenOrdersTest; it DROPS every table, so only localhost URLs are accepted. Each test leaves the
 * current schema in place.
 */
class ReceiptStatusSchemaTest {
    private static final String URL = System.getenv("TEST_DB_URL");
    private static final String USER = System.getenv("TEST_DB_USER");
    private static final String PASSWORD = System.getenv("TEST_DB_PASSWORD");
    private static final Path SQL = Path.of("src/sql-scripts/sql");

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(URL != null, "TEST_DB_URL not set; skipping database tests");
        assertTrue(URL.startsWith("jdbc:postgresql://localhost") || URL.startsWith("jdbc:postgresql://127.0.0.1"),
                "TEST_DB_URL must point at a disposable local database");
        run("DesignSql.sql");
    }

    @Test
    void freshSchemaDefaultsToPendingAndRejectsUnknownStatus() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO receipt (receipt_timestamp) VALUES (CURRENT_TIMESTAMP)");
            assertEquals("PENDING", queryString(c, "SELECT status FROM receipt"));
            SQLException bad = assertThrows(SQLException.class,
                    () -> s.execute("INSERT INTO receipt (receipt_timestamp, status) VALUES (CURRENT_TIMESTAMP, 'DONE')"));
            assertEquals("23514", bad.getSQLState()); // check_violation
            assertThrows(SQLException.class,
                    () -> s.execute("INSERT INTO receipt (receipt_timestamp, status) VALUES (CURRENT_TIMESTAMP, NULL)"));
            assertEquals(1, queryInt(c, "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_receipt_pending'"));
        }
    }

    @Test
    void migrationCompletesHistoryThenNewCheckoutsArePendingAndRerunChangesNothing() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("ALTER TABLE receipt DROP COLUMN status"); // back to the pre-#45 schema; drops its index too
            seed(s);
            s.execute("INSERT INTO receipt (location_id, user_id, receipt_timestamp) VALUES"
                    + " (1, 1, '2026-01-01 10:00'), (1, 1, '2026-01-02 11:00')");
            s.execute("INSERT INTO receipt_item (receipt_id, item_id, quantity, price_at_sale) VALUES (1, 10, 1, 5.00),"
                    + " (2, 10, 2, 10.00)");
        }
        run("AddReceiptStatus.sql");

        HikariDataSource pool = new HikariDataSource(DatabaseConnection.config(URL, USER, PASSWORD));
        DatabaseConnection.usePool(pool);
        try (Connection c = connect()) {
            assertEquals(2, queryInt(c, "SELECT COUNT(*) FROM receipt WHERE status = 'COMPLETED'"));
            CashierRepository repository = new CashierRepository();
            assertEquals(0, repository.loadPendingOrders().size(), "history must not flood the kitchen queue");

            assertEquals(CheckoutOutcome.SUCCESS, checkout(repository));
            assertEquals("PENDING", queryString(c, "SELECT status FROM receipt WHERE receipt_id = 3"));
            assertEquals(1, repository.loadPendingOrders().size());

            SQLException rerun = assertThrows(SQLException.class, () -> run("AddReceiptStatus.sql"));
            assertTrue(rerun.getMessage().contains("already exists"), rerun.getMessage());
            assertEquals("PENDING", queryString(c, "SELECT status FROM receipt WHERE receipt_id = 3"));
            assertEquals(2, queryInt(c, "SELECT COUNT(*) FROM receipt WHERE status = 'COMPLETED'"));
            assertEquals("'PENDING'::character varying", queryString(c, "SELECT column_default FROM"
                    + " information_schema.columns WHERE table_name = 'receipt' AND column_name = 'status'"));
            assertEquals(1, queryInt(c, "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_receipt_pending'"));
        } finally {
            DatabaseConnection.usePool(null);
            pool.close();
        }
    }

    private static void seed(Statement s) throws SQLException {
        s.execute("INSERT INTO location VALUES (1, 'Test', 'College Station', 'TX')");
        s.execute("INSERT INTO category VALUES (1, 'Main', NULL)");
        s.execute("INSERT INTO app_user VALUES (1, 'cashier', 'x', 'Cashier')");
        s.execute("INSERT INTO item (item_id, category_id, price, name) VALUES (10, 1, 5.00, 'Orange Chicken')");
        s.execute("INSERT INTO inventory (inventory_id, name, stock) VALUES (100, 'chicken', 10)");
        s.execute("INSERT INTO ingredient_list VALUES (10, 100)");
    }

    private static CheckoutOutcome checkout(CashierRepository repository) {
        TreeMap<Integer, Integer> cart = new TreeMap<>();
        TreeMap<Integer, BigDecimal> prices = new TreeMap<>();
        TreeMap<Integer, BigDecimal> totals = new TreeMap<>();
        cart.put(10, 1);
        prices.put(10, new BigDecimal("5.00"));
        totals.put(10, new BigDecimal("5.00"));
        return repository.checkout(1, 1, cart, prices, totals);
    }

    /** Runs a whole script on its own connection; closing it rolls back a transaction a failed script left open. */
    private static void run(String script) throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute(Files.readString(SQL.resolve(script)));
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static int queryInt(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static String queryString(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getString(1);
        }
    }
}
