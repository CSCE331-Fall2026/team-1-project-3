package backend.models;

/**
 * This class represents the result of a stock update operation, including the
 * new stock value, whether the update was successful, and whether the inventory
 * record was missing.
 *
 * @author Pablo Almanza
 */

public final class StockUpdateResult {
    private final Integer stock;
    private final boolean updated;
    private final boolean missing;

    /**
     * Creates a result describing the outcome of a stock update.
     *
     * @param stock   the updated stock value, or null if no stock value is
     *                available
     * @param updated whether the stock update succeeded
     * @param missing whether the inventory record was not found
     * @author Pablo Almanza
     * @author Ashley Hoang
     */

    public StockUpdateResult(Integer stock, boolean updated, boolean missing) {
        this.stock = stock;
        this.updated = updated;
        this.missing = missing;
    }

    /**
     * Returns the updated stock value, when one is available.
     *
     * @return the updated stock value, or null when unavailable
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public Integer getStock() {
        return stock;
    }

    /**
     * Indicates whether the stock update succeeded.
     *
     * @return true if the stock was updated; otherwise false
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public boolean isUpdated() {
        return updated;
    }

    /**
     * Indicates whether the inventory record was missing.
     *
     * @return true if the inventory record was not found; otherwise false
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public boolean isMissing() {
        return missing;
    }
}
