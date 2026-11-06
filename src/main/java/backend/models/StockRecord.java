package backend.models;

/**
 * This class represents a stock record, including the inventory ID, name, stock
 * level, minimum stock level, next shipment date, and shelf life.
 *
 * @author Pablo Almanza
 */
public final class StockRecord {
    private final int inventoryID;
    private final String name;
    private final int stock;
    private final int minStock;
    private final String nextShipment;
    private final String shelfLife;

    /**
     * Creates a stock record with inventory details used by the manager view.
     *
     * @param inventoryID  the inventory item's database ID
     * @param name         the inventory item's name
     * @param stock        the current stock quantity
     * @param minStock     the minimum desired stock quantity
     * @param nextShipment the next shipment date or description
     * @param shelfLife    the shelf life information for the item
     * @author Pablo Almanza
     * @author Ashley Hoang
     */

    public StockRecord(int inventoryID, String name, int stock, int minStock,
            String nextShipment, String shelfLife) {
        this.inventoryID = inventoryID;
        this.name = name;
        this.stock = stock;
        this.minStock = minStock;
        this.nextShipment = nextShipment;
        this.shelfLife = shelfLife;
    }

    // getter functions
    /**
     * Returns the inventory item's database ID.
     *
     * @return the inventory item ID
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public int getInventoryID() {
        return inventoryID;
    }

    /**
     * Returns the inventory item's name.
     *
     * @return the inventory item name
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the current stock quantity.
     *
     * @return the stock quantity
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public int getStock() {
        return stock;
    }

    /**
     * Returns the minimum desired stock quantity.
     *
     * @return the minimum stock quantity
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public int getMinStock() {
        return minStock;
    }

    /**
     * Returns the next shipment date or description.
     *
     * @return the next shipment information
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public String getNextShipment() {
        return nextShipment;
    }

    /**
     * Returns the shelf life information.
     *
     * @return the shelf life information
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public String getShelfLife() {
        return shelfLife;
    }
}
