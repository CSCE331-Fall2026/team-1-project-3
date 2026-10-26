package frontend.models;

import backend.models.MenuRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javafx.scene.control.CheckBox;

/**
 * Represents a line item in the shopping cart, including its
 * price, quantity, and associated menu components.
 *
 * @author Sharaf Mehmood
 */

public final class CartLine {
    private final String key;
    private final String label;
    private final BigDecimal unitPrice;
    private final List<MenuRecord> components;
    private int quantity = 1;
    private CheckBox row;

    /**
     * Creates a cart line with the specified item details.
     *
     * @param key        The identifier for the cart line.
     * @param label      The display name of the item.
     * @param unitPrice  The price per unit.
     * @param components The menu items included in the cart line.
     * @author Sharaf Mehmood
     */

    public CartLine(String key, String label, BigDecimal unitPrice, List<MenuRecord> components) {
        this.key = key;
        this.label = label;
        this.unitPrice = unitPrice;
        this.components = components;
    }

    /**
     * Creates a cart line for a single menu item.
     *
     * @param record The menu item to add to the cart.
     * @return A new cart line containing the specified menu item.
     * @author Sharaf Mehmood
     */

    public static CartLine single(MenuRecord record) {
        return new CartLine("item:" + record.getItemID(), record.getName(), record.getPrice(), List.of(record));
    }

    /**
     * Creates a cart line for a meal with the selected entrees and side.
     *
     * @param size    The size of the meal.
     * @param entrees The entrees included in the meal.
     * @param side    The side included in the meal.
     * @return A new cart line representing the complete meal.
     * @author Sharaf Mehmood
     */

    public static CartLine meal(MealSize size, List<MenuRecord> entrees, MenuRecord side) {
        List<MenuRecord> components = new ArrayList<>(entrees);
        components.add(side);
        BigDecimal listTotal = BigDecimal.ZERO;
        for (MenuRecord c : components) {
            listTotal = listTotal.add(c.getPrice());
        }
        BigDecimal price = listTotal.multiply(BigDecimal.ONE.subtract(size.getDiscount())).setScale(2,
                RoundingMode.HALF_UP);
        return new CartLine(null, size.getLabel(), price, components);
    }

    /**
     * Calculates the total price of the cart line.
     *
     * @return The unit price multiplied by the quantity.
     * @author Sharaf Mehmood
     */

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Calculates the price difference between the cart line
     * and the total price of its individual components.
     *
     * @return The sum of the component prices minus the unit price.
     * @author Sharaf Mehmood
     */

    public BigDecimal discount() {
        BigDecimal listTotal = BigDecimal.ZERO;
        for (MenuRecord component : components)
            listTotal = listTotal.add(component.getPrice());
        return listTotal.subtract(unitPrice);
    }

    /**
     * Generates a formatted description of the cart line,
     * including its quantity, label, components, and total price.
     *
     * @return A formatted string describing the cart line.
     * @author Sharaf Mehmood
     */
    public String describe() {
        String text = quantity + "x " + label;
        if (components.size() > 1) {
            text += " (" + components.stream().map(MenuRecord::getName).collect(Collectors.joining(", ")) + ")";
        }
        return text + " $%.2f".formatted(lineTotal());
    }

    /**
     * Gets the identifier associated with the cart line.
     *
     * @return The cart line identifier.
     * @author Sharaf Mehmood
     */

    public String getKey() {
        return key;
    }

    /**
     * Gets the display label of the cart line.
     *
     * @return The cart line's display label.
     * @author Sharaf Mehmood
     */

    public String getLabel() {
        return label;
    }

    /**
     * Gets the price per unit of the cart line.
     *
     * @return The unit price.
     * @author Sharaf Mehmood
     */

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * Gets the menu items included in the cart line.
     *
     * @return The list of menu item components.
     * @author Sharaf Mehmood
     */

    public List<MenuRecord> getComponents() {
        return components;
    }

    /**
     * Gets the checkbox associated with the cart line.
     *
     * @return The checkbox associated with this cart line.
     * @author Sharaf Mehmood
     */

    public CheckBox getRow() {
        return row;
    }

    /**
     * Gets the quantity of the cart line.
     *
     * @return The current quantity.
     * @author Sharaf Mehmood
     */

    public int getQuantity() {
        return quantity;
    }

    /**
     * Sets the quantity of the cart line.
     *
     * @param q The new quantity.
     * @author Sharaf Mehmood
     */

    public void setQuantity(int q) {
        quantity = q;
    }

    /**
     * Sets the checkbox associated with the cart line.
     *
     * @param r The checkbox to associate with this cart line.
     * @author Sharaf Mehmood
     */

    public void setRow(CheckBox r) {
        row = r;
    }
}
