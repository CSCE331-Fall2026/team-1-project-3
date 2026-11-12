package backend.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import backend.database.CashierRepository.CheckoutOutcome;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs against a disposable local PostgreSQL loaded with src/sql-scripts/sql/DesignSql.sql.
 * Skipped unless TEST_DB_URL is set. It WIPES the tables, so only localhost URLs are accepted.
 */
class DatabaseConnectionTest {
    private static final String URL = System.getenv("TEST_DB_URL");
    private static final String USER = System.getenv("TEST_DB_USER");
    private static final String PASSWORD = System.getenv("TEST_DB_PASSWORD");

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
            s.execute("INSERT INTO item (item_id, category_id, price, name) VALUES (10, 1, 5.00, 'Orange Chicken')");
            s.execute("INSERT INTO inventory (inventory_id, name, stock) VALUES (100, 'chicken', 3)");
            s.execute("INSERT INTO ingredient_list VALUES (10, 100)");
        }
    }

    @AfterEach
    void tearDown() {
        DatabaseConnection.usePool(null);
        if (pool != null)
            pool.close();
    }

    @Test
    void reusesThePhysicalConnection() throws Exception {
        int first;
        try (Connection c = DatabaseConnection.open()) {
            first = backendPid(c);
        }
        try (Connection c = DatabaseConnection.open()) {
            assertEquals(first, backendPid(c));
        }
        assertTrue(pool.getHikariPoolMXBean().getTotalConnections() <= 2);
    }

    @Test
    void unreachableDatabaseFailsFastWithoutBlockingStartup() {
        HikariConfig config = DatabaseConnection.config("jdbc:postgresql://127.0.0.1:1/none", "x", "x");
        long start = System.nanoTime();
        try (HikariDataSource dead = new HikariDataSource(config)) {
            assertTrue(millisSince(start) < 1_000, "creating the pool must not wait on the database");
            long acquire = System.nanoTime();
            assertThrows(SQLException.class, dead::getConnection);
            long waited = millisSince(acquire);
            assertTrue(waited >= 4_500 && waited < 6_500, "expected ~5 s acquisition timeout, waited " + waited + " ms");
        }
    }

    @Test
    void returnedConnectionIsRolledBackAndAutoCommitRestored() throws Exception {
        int pid;
        try (Connection c = DatabaseConnection.open(); Statement s = c.createStatement()) {
            pid = backendPid(c);
            c.setAutoCommit(false);
            s.executeUpdate("UPDATE inventory SET stock = 0 WHERE inventory_id = 100");
            // closed without commit or rollback
        }
        try (Connection c = DatabaseConnection.open()) {
            assertEquals(pid, backendPid(c), "same physical connection, so the cleanup is what we observe");
            assertTrue(c.getAutoCommit());
            assertEquals(3, stock(c));
        }
    }

    @Test
    void brokenConnectionIsEvicted() throws Exception {
        int dead;
        try (Connection c = DatabaseConnection.open()) {
            dead = backendPid(c);
            try (Connection admin = DriverManager.getConnection(URL, USER, PASSWORD);
                    Statement s = admin.createStatement()) {
                s.execute("SELECT pg_terminate_backend(" + dead + ")");
            }
            assertThrows(SQLException.class, () -> backendPid(c));
        }
        try (Connection c = DatabaseConnection.open()) {
            assertNotEquals(dead, backendPid(c));
        }
    }

    @Test
    void checkoutSuccessCommits() throws Exception {
        assertEquals(CheckoutOutcome.SUCCESS, checkout(2));
        try (Connection c = DatabaseConnection.open()) {
            assertEquals(1, stock(c));
            assertEquals(1, count(c, "receipt"));
            assertEquals(1, count(c, "receipt_item"));
        }
    }

    @Test
    void checkoutFailureRollsBackAndLeavesThePoolUsable() throws Exception {
        assertEquals(CheckoutOutcome.FAILURE, checkout(5)); // only 3 in stock
        try (Connection c = DatabaseConnection.open()) {
            assertTrue(c.getAutoCommit());
            assertEquals(3, stock(c));
            assertEquals(0, count(c, "receipt"), "receipt insert must be rolled back");
        }
        assertEquals(1, new CashierRepository().loadMenu().size());
        assertEquals(CheckoutOutcome.SUCCESS, checkout(1));
    }

    /** Prints pooled vs one-connection-per-call timings for the same query on the same database. */
    @Test
    void printLatencyComparison() throws Exception {
        int n = 30;
        long[] direct = new long[n];
        long[] pooled = new long[n];
        CashierRepository repository = new CashierRepository();
        repository.loadMenu(); // warm up the pool and JIT
        for (int i = 0; i < n; i++) {
            long start = System.nanoTime();
            try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
                    Statement s = c.createStatement();
                    ResultSet r = s.executeQuery("SELECT name, price, item_id, category_id FROM item")) {
                while (r.next()) { }
            }
            direct[i] = System.nanoTime() - start;
            start = System.nanoTime();
            repository.loadMenu();
            pooled[i] = System.nanoTime() - start;
        }
        System.out.printf("[latency] median per menu read, n=%d: new connection %.2f ms, pooled %.2f ms%n",
                n, median(direct), median(pooled));
    }

    private static CheckoutOutcome checkout(int quantity) {
        TreeMap<Integer, Integer> cart = new TreeMap<>();
        TreeMap<Integer, BigDecimal> prices = new TreeMap<>();
        TreeMap<Integer, BigDecimal> totals = new TreeMap<>();
        cart.put(10, quantity);
        prices.put(10, new BigDecimal("5.00"));
        totals.put(10, new BigDecimal("5.00").multiply(BigDecimal.valueOf(quantity)));
        return new CashierRepository().checkout(1, 1, cart, prices, totals);
    }

    private static int backendPid(Connection c) throws SQLException {
        return queryInt(c, "SELECT pg_backend_pid()");
    }

    private static int stock(Connection c) throws SQLException {
        return queryInt(c, "SELECT stock FROM inventory WHERE inventory_id = 100");
    }

    private static int count(Connection c, String table) throws SQLException {
        return queryInt(c, "SELECT COUNT(*) FROM " + table);
    }

    private static int queryInt(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static double median(long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2] / 1e6;
    }
}
