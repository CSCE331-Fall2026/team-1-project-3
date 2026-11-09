package backend.models;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;


/**
 * This class represents a menu record, including the item ID, name, and price.
 *
 * @author Pablo Almanza
 */

public final class MenuRecord {
    private final int itemID;
    private final BigDecimal price;
    private final String nutrition;
    private final String unitSize;
    private final String name;
    private final int categoryID;
    private final List<Integer> ingredientIDs;

    /**
     * Creates a menu record with the item's database ID, price, name, and category.
     *
     * @param itemID     the item's database ID
     * @param price      the item's price
     * @param name       the item's name
     * @param categoryID the item's category ID
     * @param ingredientIDs the list of ingredient IDs
     * @author Pablo Almanza
     * @author Ashley Hoang
     */

    //for new menu items: ingredientIDs can be passed
    public MenuRecord(int itemID, BigDecimal price, String name, int categoryID, List<Integer> ingredientIDs, String nutrition, String unitSize) {
        this.itemID = itemID;
        this.price = price;
        this.name = name;
        this.categoryID = categoryID;
        this.ingredientIDs = ingredientIDs != null ? new ArrayList<>(ingredientIDs) : new ArrayList<>();
        this.nutrition = nutrition;
        this.unitSize = unitSize;
    }

    //for cashier view: ingredientIDs not needed
    public MenuRecord(int itemID, BigDecimal price, String name, int categoryID) {
        this.itemID = itemID;
        this.price = price;
        this.name = name;
        this.categoryID = categoryID;
        this.ingredientIDs = new ArrayList<>();
        this.nutrition = "";
        this.unitSize = "";
    }

    /**
     * Returns the menu item's database ID.
     *
     * @return the menu item ID
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public int getItemID() {
        return itemID;
    }

    /**
     * Returns the menu item's price.
     *
     * @return the menu item price
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Returns the menu item's name.
     *
     * @return the menu item name
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the menu item's category ID.
     *
     * @return the menu item category ID
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public int getCategoryID() {
        return categoryID;
    }

    /**
     * Returns the list of ingredient IDs for the menu item.
     *
     * @return the list of ingredient IDs
     * @author Ashley Hoang
     */
    public List<Integer> getIngredientIDs() {
        return new ArrayList<>(ingredientIDs);
    }

    /**
     * Returns the nutritional information of the menu item.
     *
     * @return the menu item nutrition information
     * @author Ashley Hoang
     */
    public String getNutrition() {
        return nutrition;
    }

    /**
     * Returns the unit size of the menu item.
     *
     * @return the menu item unit size
     * @author Ashley Hoang
     */
    public String getUnitSize() {
        return unitSize;
    }
}
