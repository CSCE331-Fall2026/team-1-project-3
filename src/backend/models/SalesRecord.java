package backend.models;

/**
 * This class represents a sales record, including the name of the item and the
 * quantity sold.
 *
 * @author Pablo Almanza
 */
public final class SalesRecord {
    private final String name;
    private final long quantity;
    private final int id;

    /**
     * Creates a sales record for an item and its quantity sold.
     *
     * @param id       the item's database ID
     * @param name     the item's name
     * @param quantity the quantity of the item sold
     * @author Pablo Almanza
     * @author Ashley Hoang
     */

    public SalesRecord(int id, String name, long quantity) {
        this.name = name;
        this.quantity = quantity;
        this.id = id;
    }

    // getter functions
    /**
     * Returns the item name.
     *
     * @return the item name
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the quantity sold.
     *
     * @return the quantity sold
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public long getQuantity() {
        return quantity;
    }

    /**
     * Returns the item's database ID.
     *
     * @return the item ID
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public int getID() {
        return id;
    }
}
