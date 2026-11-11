package backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import backend.api.Dtos.ManagerMenuItem;
import backend.api.Dtos.MenuItem;
import backend.models.MenuRecord;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DtosTest {
    private final MenuRecord record = new MenuRecord(7, new BigDecimal("8.30"), "Orange Chicken", 2, List.of(1, 2),
            "490 cal", "5.7 oz");

    @Test
    void managerMenuItemKeepsNutritionAndUnitSize() {
        assertThat(ManagerMenuItem.of(record))
                .isEqualTo(new ManagerMenuItem(7, "Orange Chicken", 830, 2, "490 cal", "5.7 oz"));
    }

    @Test
    void cashierMenuItemContractIsUnchanged() {
        List<String> fields = Arrays.stream(MenuItem.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertThat(fields).containsExactly("itemId", "name", "priceCents", "categoryId");
        assertThat(MenuItem.of(record)).isEqualTo(new MenuItem(7, "Orange Chicken", 830, 2));
    }
}
