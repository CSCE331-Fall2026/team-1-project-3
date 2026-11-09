package backend.models;

/**
 * Represents a record in the inventory system.
 * @author Ashley Hoang
 */
public class InventoryRecord {
    private final int inventoryId;
    private final String name;
    private final String nutrition;
    private final int stock;
    private final int minStock;
    private final String nextShipment;
    private final String shelfLife;
    private final float unitSize;

    /**
     * Creates a new inventory record.
     * @param inventoryId the ID of the inventory item
     * @param name the name of the inventory item
     * @param nutrition the nutritional information of the inventory item
     * @param stock the current stock level of the inventory item
     * @param minStock the minimum stock level for the inventory item
     * @param nextShipment the date of the next shipment for the inventory item
     * @param shelfLife the shelf life of the inventory item
     * @param unitSize the size of each unit of the inventory item
     * @author Ashley Hoang
     */
    public InventoryRecord(int inventoryId, String name, String nutrition, int stock, int minStock, String nextShipment, String shelfLife, float unitSize) {
        this.inventoryId = inventoryId;
        this.name = name;
        this.nutrition = nutrition;
        this.stock = stock;
        this.minStock = minStock;
        this.nextShipment = nextShipment;
        this.shelfLife = shelfLife;
        this.unitSize = unitSize;
    }

    /**
     * Returns the ID of the inventory item.
     * @return the inventory ID
     * @author Ashley Hoang
     */
    public int getInventoryId() {
        return inventoryId;
    }

    /**
     * Returns the name of the inventory item.
     * @return the inventory name
     * @author Ashley Hoang
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the nutritional information of the inventory item.
     * @return the nutrition information
     * @author Ashley Hoang
     */
    public String getNutrition() {
        return nutrition;
    }

    /**
     * Returns the current stock level of the inventory item.
     * @return the stock level
     * @author Ashley Hoang
     */
    public int getStock() {
        return stock;
    }

    /**
     * Returns the minimum stock level for the inventory item.
     * @return the minimum stock level
     * @author Ashley Hoang
     */
    public int getMinStock() {
        return minStock;
    }

    /**
     * Returns the date of the next shipment for the inventory item.
     * @return the next shipment date
     * @author Ashley Hoang
     */
    public String getNextShipment() {
        return nextShipment;
    }

    /**
     * Returns the shelf life of the inventory item.
     * @return the shelf life
     * @author Ashley Hoang
     */
    public String getShelfLife() {
        return shelfLife;
    }

    /**
     * Returns the size of each unit of the inventory item.
     * @return the unit size
     * @author Ashley Hoang
     */
    public float getUnitSize() {
        return unitSize;
    }

}