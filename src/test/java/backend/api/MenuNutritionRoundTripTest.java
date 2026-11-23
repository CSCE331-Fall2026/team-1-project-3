package backend.api;

import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.database.DatabaseConnection;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Regression for #70: newline-separated nutrition survives the Manager edit/add round trip and reaches the
 * customer menu unchanged. Runs against a throwaway embedded PostgreSQL; the shared database is never contacted.
 */
class MenuNutritionRoundTripTest {
    private static final String NUTRITION = "Calories: 490\nContains: soy, wheat";
    private static EmbeddedPostgres pg;
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ManagerApi(), new CashierApi())
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
            s.execute("INSERT INTO category (category_id, name) VALUES (1, 'Main'), (2, 'Side'), (3, 'Drink')");
            s.execute("INSERT INTO inventory (inventory_id, name, stock) VALUES (1, 'Chicken', 100)");
            s.execute("INSERT INTO item (item_id, category_id, price, name, nutrition) VALUES "
                    + "(10, 1, 8.30, 'Orange Chicken', E'Calories: 490\\nContains: soy, wheat')");
        }
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private static String body(int itemId, String name) {
        return "{\"itemId\":" + itemId + ",\"categoryId\":1,\"nutrition\":\"Calories: 490\\nContains: soy, wheat\","
                + "\"priceCents\":900,\"unitSize\":\"\",\"name\":\"" + name + "\",\"ingredientIds\":[1]}";
    }

    @Test
    void editingAnotherFieldKeepsNutritionLineBreaks() throws Exception {
        mvc.perform(put("/api/manager/menu/10").contentType(MediaType.APPLICATION_JSON).content(body(10, "Renamed")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(true));
        mvc.perform(get("/api/manager/menu")).andExpect(jsonPath("$[0].nutrition").value(NUTRITION));
        mvc.perform(get("/api/menu")).andExpect(jsonPath("$[0].name").value("Renamed"))
                .andExpect(jsonPath("$[0].nutrition").value(NUTRITION));
    }

    @Test
    void addingItemKeepsMultilineNutrition() throws Exception {
        mvc.perform(post("/api/manager/menu").contentType(MediaType.APPLICATION_JSON).content(body(11, "New")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.added").value(true));
        mvc.perform(get("/api/menu")).andExpect(jsonPath("$[?(@.itemId == 11)].nutrition").value(NUTRITION));
    }
}
