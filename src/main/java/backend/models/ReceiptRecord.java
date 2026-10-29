package backend.models;

import java.math.BigDecimal;

/**
 * This class represents a receipt record, including the receipt ID, item name,
 * quantity, line total, item ID, and timestamp.
 *
 * @author Pablo Almanza
 */
public final class ReceiptRecord {
    private final int receiptID;
    private final String itemName;
    private final int quantity;
    private final BigDecimal lineTotal;
    private final int itemID;
    private final String timestamp;

    /**
     * Creates a receipt record containing item and transaction details.
     *
     * @param receiptID the receipt's database ID
     * @param itemID    the item's database ID
     * @param itemName  the item's name
     * @param quantity  the quantity purchased
     * @param lineTotal the total price for this receipt line
     * @param timestamp the receipt timestamp
     * @author Pablo Almanza
     * @author Ashley Hoang
     */

    public ReceiptRecord(int receiptID, int itemID, String itemName, int quantity, BigDecimal lineTotal,
            String timestamp) {
        this.receiptID = receiptID;
        this.itemName = itemName;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
        this.itemID = itemID;
        this.timestamp = timestamp;
    }

    // getter functions
    /**
     * Returns the receipt's database ID.
     *
     * @return the receipt ID
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public int getReceiptID() {
        return receiptID;
    }

    /**
     * Returns the purchased item's name.
     *
     * @return the item name
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public String getItemName() {
        return itemName;
    }

    /**
     * Returns the purchased quantity.
     *
     * @return the quantity
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * Returns the total for this receipt line.
     *
     * @return the line total
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    /**
     * Returns the purchased item's database ID.
     *
     * @return the item ID
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public int getItemID() {
        return itemID;
    }

    /**
     * Returns the receipt timestamp.
     *
     * @return the timestamp
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public String getTimestamp() {
        return timestamp;
    }
}
