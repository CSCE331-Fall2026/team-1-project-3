package frontend.models;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.paint.Color;

/**
 * This class represents a stock item with its properties and provides getters and setters for each property.
 * @author Ashley Hoang
 */
public class StockItem {
    private final IntegerProperty inventoryID = new SimpleIntegerProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final IntegerProperty stock = new SimpleIntegerProperty();
    private final IntegerProperty minStock = new SimpleIntegerProperty();
    private final StringProperty nextShipment = new SimpleStringProperty();
    private final StringProperty shelfLife = new SimpleStringProperty();


    /**
     * Creates a stock item with the supplied inventory details.
     *
     * @param inventoryID  the inventory item's database ID
     * @param name         the inventory item's name
     * @param stock        the current stock quantity
     * @param minStock     the minimum desired stock quantity
     * @param nextShipment the next shipment date or description
     * @param shelfLife    the shelf life information for the item
     * @author Ashley Hoang
     */
    public StockItem(int inventoryID, String name, int stock, int minStock, String nextShipment, String shelfLife) {
        this.inventoryID.set(inventoryID);
        this.name.set(name);
        this.stock.set(stock);
        this.minStock.set(minStock);
        this.nextShipment.set(nextShipment);
        this.shelfLife.set(shelfLife);
    }

    // Getters and setters for each field
    /**
     * Returns the inventory item's database ID.
     *
     * @return the inventory item ID
     * @author Ashley Hoang
     */
    public int getInventoryID() { return inventoryID.get(); }
    /**
     * Returns the inventory item's name.
     *
     * @return the inventory item name
     * @author Ashley Hoang
     */
    public String getName() { return name.get(); }
    /**
     * Returns the current stock quantity.
     *
     * @return the stock quantity
     * @author Ashley Hoang
     */
    public int getStock() { return stock.get(); }
    /**
     * Returns the minimum desired stock quantity.
     *
     * @return the minimum stock quantity
     * @author Ashley Hoang
     */
    public int getMinStock() { return minStock.get(); }
    /**
     * Returns the next shipment date or description.
     *
     * @return the next shipment information
     * @author Ashley Hoang
     */
    public String getNextShipment() { return nextShipment.get(); }
    /**
     * Returns the shelf life information.
     *
     * @return the shelf life information
     * @author Ashley Hoang
     */
    public String getShelfLife() { return shelfLife.get(); }

    /**
     * Updates the current stock quantity.
     *
     * @param value the new stock quantity
     * @author Ashley Hoang
     */
    public void setStock(int value) { stock.set(value); }

    /**
     * Calculates and returns what color the stock item should display based on the relationship of stock and min stock
     * @return Color of warning for stock
     * @author Ashley Hoang
     */
    public Color getColor(){
        //lower than minStock --> red; between minStock and minStock*1.5 --> orange; else white
        Color warningColor = Color.rgb(255, 255, 255);
        if (getStock() < getMinStock())
            warningColor = Color.rgb(255, 51, 51); //red
        else if (getStock() >= getMinStock() && getStock() <= Math.floor(getMinStock() * 1.5))
            warningColor = Color.rgb(255, 204, 0); //orange-yellow
        return warningColor;
    }
}
