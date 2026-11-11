package backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.database.DatabaseConnection;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * POST /api/checkout against a throwaway local PostgreSQL built from src/sql-scripts/sql/DesignSql.sql. The shared
 * team database is never contacted: DatabaseConnection.open() is redirected to the embedded server.
 */
class CheckoutApiTest {
    private static EmbeddedPostgres pg;
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CashierApi())
            .setControllerAdvice(new ApiErrors()).build();
    private MockedStatic<DatabaseConnection> db;

    @BeforeAll
    static void startPostgres() throws Exception {
        pg = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        pg.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        db = mockStatic(DatabaseConnection.class);
        db.when(DatabaseConnection::open).thenAnswer(i -> pg.getPostgresDatabase().getConnection());
        try (Connection c = pg.getPostgresDatabase().getConnection(); Statement s = c.createStatement()) {
            s.execute(Files.readString(Path.of("src/sql-scripts/sql/DesignSql.sql")));
            s.execute("INSERT INTO location VALUES (1, 'Test', 'College Station', 'TX')");
            s.execute("INSERT INTO app_user VALUES (3, 'Cashier', 'x', 'Cashier')");
            s.execute("INSERT INTO category (category_id, name) VALUES (1, 'Main'), (2, 'Side'), (3, 'Drink')");
            s.execute("INSERT INTO inventory (inventory_id, name, stock) VALUES "
                    + "(1, 'Chicken', 100), (2, 'Beef', 100), (3, 'Shrimp', 100), (4, 'Rice', 100), (5, 'Cup', 100)");
            s.execute("INSERT INTO item (item_id, category_id, price, name) VALUES (10, 1, 8.30, 'Orange Chicken'), "
                    + "(11, 1, 9.50, 'Beef'), (12, 1, 11.00, 'Shrimp'), (20, 2, 4.40, 'Rice'), (30, 3, 2.10, 'Drink')");
            s.execute("INSERT INTO ingredient_list VALUES (10, 1), (11, 2), (12, 3), (20, 4), (30, 5)");
        }
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private ResultActions checkout(String items) throws Exception {
        return mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationId\":1,\"userId\":3,\"items\":" + items + "}"));
    }

    private static String line(int itemId, int qty, long listPrice, long total) {
        return "{\"itemId\":" + itemId + ",\"quantity\":" + qty + ",\"listPriceCents\":" + listPrice
                + ",\"totalCents\":" + total + "}";
    }

    private long count(String sql) throws Exception {
        try (Connection c = pg.getPostgresDatabase().getConnection(); Statement s = c.createStatement();
                ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getLong(1);
        }
    }

    private long receipts() throws Exception {
        return count("SELECT COUNT(*) FROM receipt");
    }

    private void assertRejected(String items) throws Exception {
        checkout(items).andExpect(status().isBadRequest()).andExpect(jsonPath("$.outcome").value("FAILURE"));
        assertThat(receipts()).isZero();
        assertThat(count("SELECT SUM(stock) FROM inventory")).isEqualTo(500);
    }

    /* ---- legitimate orders (payloads produced by client/src/lib.js flattenCart) ---- */

    @Test
    void regularOrderSucceedsAndRecordsDatabasePrices() throws Exception {
        checkout("[" + line(20, 2, 440, 880) + "," + line(30, 1, 210, 210) + "]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("SUCCESS"));
        assertThat(receipts()).isEqualTo(1);
        assertThat(count("SELECT SUM(price_at_sale * 100) FROM receipt_item")).isEqualTo(1090);
        assertThat(count("SELECT stock FROM inventory WHERE inventory_id = 4")).isEqualTo(98);
    }

    @Test
    void biggerPlateMealDiscountIsAccepted() throws Exception {
        checkout("[" + line(10, 1, 830, 149) + "," + line(11, 1, 950, 269) + "," + line(12, 1, 1100, 417) + ","
                + line(20, 1, 440, 440) + "]").andExpect(jsonPath("$.outcome").value("SUCCESS"));
        assertThat(count("SELECT SUM(price_at_sale * 100) FROM receipt_item")).isEqualTo(1275);
    }

    @Test
    void mixedMealsAndSinglesAreAccepted() throws Exception {
        // 2x Plate(A, A) + single A + drink
        checkout("[" + line(10, 5, 830, 1846) + "," + line(20, 2, 440, 880) + "," + line(30, 1, 210, 210) + "]")
                .andExpect(jsonPath("$.outcome").value("SUCCESS"));
        // Bowl(C) + Plate(B, C)
        checkout("[" + line(12, 2, 1100, 915) + "," + line(20, 2, 440, 880) + "," + line(11, 1, 950, 268) + "]")
                .andExpect(jsonPath("$.outcome").value("SUCCESS"));
        assertThat(receipts()).isEqualTo(2);
    }

    /* ---- manipulated totals and prices ---- */

    @Test
    void zeroDollarCheckoutIsRejected() throws Exception {
        assertRejected("[" + line(10, 1, 830, 0) + "]");
        assertRejected("[" + line(30, 3, 210, 0) + "]");
        // A side makes a meal possible, but no meal discount reaches 100%.
        assertRejected("[" + line(10, 1, 830, 0) + "," + line(20, 1, 440, 440) + "]");
    }

    @Test
    void negativeAndExcessiveTotalsAreRejected() throws Exception {
        assertRejected("[" + line(30, 1, 210, -210) + "]");
        assertRejected("[" + line(30, 1, 210, 211) + "]");
        assertRejected("[" + line(30, 1, 210, 10_000_000_000L) + "]");
    }

    @Test
    void discountBeyondAnyMealIsRejected() throws Exception {
        // 10 mains at 61% off with only one side: at most 3 of them can be in a meal.
        assertRejected("[" + line(10, 10, 830, 3188) + "," + line(20, 1, 440, 440) + "]");
    }

    @Test
    void tamperedListPriceIsRejected() throws Exception {
        assertRejected("[" + line(30, 1, 1, 1) + "]"); // total checked against the database price
        // Correct total but a forged list price: the receipt insert requires list price == database price.
        checkout("[" + line(30, 1, 1, 210) + "]").andExpect(jsonPath("$.outcome").value("FAILURE"));
        assertThat(receipts()).isZero();
    }

    @Test
    void invalidQuantitiesAreRejected() throws Exception {
        assertRejected("[" + line(30, 0, 210, 0) + "]");
        assertRejected("[" + line(30, -1, 210, 210) + "]");
        assertRejected("[{\"itemId\":30,\"listPriceCents\":210,\"totalCents\":210}]");
        assertRejected("[" + line(30, Integer.MAX_VALUE, 210, 9_999_999_999L) + "]");
    }

    /* ---- duplicates and transactions ---- */

    @Test
    void duplicateItemLinesInOneRequestAreRejected() throws Exception {
        assertRejected("[" + line(30, 1, 210, 210) + "," + line(30, 1, 210, 210) + "]");
    }

    @Test
    void identicalRequestsAreNotDeduplicated() throws Exception {
        // Documents current behavior: there is no idempotency key, so a resubmitted order is recorded twice.
        String order = "[" + line(30, 1, 210, 210) + "]";
        checkout(order).andExpect(jsonPath("$.outcome").value("SUCCESS"));
        checkout(order).andExpect(jsonPath("$.outcome").value("SUCCESS"));
        assertThat(receipts()).isEqualTo(2);
    }

    @Test
    void failureMidTransactionRollsBackReceiptAndStock() throws Exception {
        try (Connection c = pg.getPostgresDatabase().getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE inventory SET stock = 0 WHERE inventory_id = 5");
        }
        checkout("[" + line(20, 1, 440, 440) + "," + line(30, 1, 210, 210) + "]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("FAILURE"));
        assertThat(receipts()).isZero();
        assertThat(count("SELECT COUNT(*) FROM receipt_item")).isZero();
        assertThat(count("SELECT stock FROM inventory WHERE inventory_id = 4")).isEqualTo(100);
    }
}
