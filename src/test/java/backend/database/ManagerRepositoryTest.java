package backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.api.ApiErrors;
import backend.api.ManagerApi;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Runs ManagerRepository against an in-memory H2 database in PostgreSQL mode, so nothing reaches the shared team
 * database. The tables copy the relevant parts of src/sql-scripts/sql/DesignSql.sql, including ON UPDATE CASCADE.
 */
class ManagerRepositoryTest {
    private static final String URL = "jdbc:h2:mem:manager;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=HOUR"; // loadDashboard aliases a column "hour"

    private final ManagerRepository repository = new ManagerRepository();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ManagerApi())
            .setControllerAdvice(new ApiErrors()).build();
    private MockedStatic<DatabaseConnection> db;

    @BeforeEach
    void setUp() throws Exception {
        db = mockStatic(DatabaseConnection.class);
        db.when(DatabaseConnection::open).thenAnswer(i -> DriverManager.getConnection(URL));
        try (Connection c = DriverManager.getConnection(URL); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
            s.execute("CREATE TABLE item (item_id INTEGER PRIMARY KEY, category_id INTEGER NOT NULL, nutrition TEXT, "
                    + "price NUMERIC(10, 2) NOT NULL, unit_size VARCHAR(50), name VARCHAR(100) NOT NULL, "
                    + "active BOOLEAN NOT NULL DEFAULT TRUE)");
            s.execute("CREATE TABLE inventory (inventory_id INTEGER PRIMARY KEY, name VARCHAR(64) NOT NULL, "
                    + "nutrition VARCHAR(128), stock INT NOT NULL DEFAULT 0, min_stock INT NOT NULL DEFAULT 0, "
                    + "next_shipment TIMESTAMP, shelf_life TIMESTAMP, unit_size FLOAT)");
            s.execute("CREATE TABLE ingredient_list (item_id INTEGER NOT NULL, inventory_id INTEGER NOT NULL, "
                    + "PRIMARY KEY (item_id, inventory_id), "
                    + "FOREIGN KEY (item_id) REFERENCES item(item_id) ON DELETE RESTRICT ON UPDATE CASCADE, "
                    + "FOREIGN KEY (inventory_id) REFERENCES inventory(inventory_id) ON DELETE RESTRICT ON UPDATE CASCADE)");
            s.execute("CREATE TABLE receipt (receipt_id INTEGER PRIMARY KEY, receipt_timestamp TIMESTAMP NOT NULL)");
            s.execute("CREATE TABLE receipt_item (receipt_item_id INTEGER PRIMARY KEY, receipt_id INTEGER NOT NULL, "
                    + "item_id INTEGER NOT NULL REFERENCES item(item_id) ON UPDATE CASCADE, quantity INTEGER NOT NULL, "
                    + "price_at_sale NUMERIC(10, 2) NOT NULL)");
            s.execute("INSERT INTO inventory (inventory_id, name) VALUES (1, 'Chicken'), (2, 'Sauce'), (3, 'Rice')");
            s.execute("INSERT INTO item VALUES (10, 2, '490 cal', 8.30, '5.7 oz', 'Orange Chicken', TRUE), "
                    + "(20, 1, '', 4.40, '', 'Chow Mein', TRUE)");
            s.execute("INSERT INTO ingredient_list VALUES (10, 1), (10, 2), (20, 3)");
        }
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private boolean update(int oldId, int newId, List<Integer> ingredients) throws Exception {
        return repository.updateMenuItem(oldId, newId, 2, "520 cal", new BigDecimal("9.00"), "6 oz", "Orange Chicken",
                ingredients);
    }

    private String itemName(int id) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL);
                PreparedStatement s = c.prepareStatement("SELECT name FROM item WHERE item_id = ?")) {
            s.setInt(1, id);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getString(1) : null;
            }
        }
    }

    @Test
    void changingItemIdKeepsItsIngredients() throws Exception {
        assertThat(update(10, 11, List.of(1, 2))).isTrue();

        assertThat(repository.getItemIngredients(11)).containsExactlyInAnyOrder(1, 2);
        assertThat(repository.getItemIngredients(10)).isEmpty();
        assertThat(itemName(10)).isNull();
        assertThat(itemName(11)).isEqualTo("Orange Chicken");
    }

    @Test
    void updatingIngredientSelectionReplacesTheRecipe() throws Exception {
        assertThat(update(10, 10, List.of(2, 3))).isTrue();
        assertThat(repository.getItemIngredients(10)).containsExactlyInAnyOrder(2, 3);

        assertThat(update(10, 12, List.of(3))).isTrue();
        assertThat(repository.getItemIngredients(12)).containsExactly(3);
        assertThat(repository.getItemIngredients(20)).containsExactly(3); // other items untouched
    }

    @Test
    void failedIngredientInsertRollsBackEverything() {
        assertThatThrownBy(() -> update(10, 11, List.of(1, 999))).isInstanceOf(SQLException.class);

        assertRecipeUntouched();
    }

    @Test
    void idCollisionRollsBackAndKeepsRecipe() {
        assertThatThrownBy(() -> update(10, 20, List.of(1, 2)))
                .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));

        assertRecipeUntouched();
    }

    @Test
    void missingItemReturnsFalseAndChangesNothing() throws Exception {
        assertThat(update(99, 98, List.of(1))).isFalse();
        assertRecipeUntouched();
    }

    private void assertRecipeUntouched() {
        try {
            assertThat(itemName(10)).isEqualTo("Orange Chicken");
            assertThat(itemName(11)).isNull();
            assertThat(repository.getItemIngredients(10)).containsExactlyInAnyOrder(1, 2);
            assertThat(repository.getItemIngredients(20)).containsExactly(3);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void managerMenuResponseIncludesNutritionAndUnitSize() throws Exception {
        mvc.perform(get("/api/manager/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemId").value(10))
                .andExpect(jsonPath("$[0].priceCents").value(830))
                .andExpect(jsonPath("$[0].nutrition").value("490 cal"))
                .andExpect(jsonPath("$[0].unitSize").value("5.7 oz"));
    }

    @Test
    void databaseFailuresReturn500InsteadOfEmptyLists() throws Exception {
        db.when(DatabaseConnection::open).thenThrow(new SQLException("connection refused", "08001"));

        mvc.perform(get("/api/manager/inventory"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Database request failed"));
        mvc.perform(get("/api/manager/menu/10/ingredients"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Database request failed"));
    }

    @Test
    void endpointsStillWorkWhenDatabaseIsHealthy() throws Exception {
        mvc.perform(get("/api/manager/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inventory.length()").value(3));
        mvc.perform(get("/api/manager/menu/10/ingredients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientIds.length()").value(2));
    }
}
