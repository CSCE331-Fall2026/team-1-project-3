package backend.models;

import java.util.List;

/**
 * This class holds all the data for the manager controller, including sales,
 * stock, receipts, and menu information.
 *
 * @author Pablo Almanza
 * @author Ashley Hoang
 */

public final class ManagerData {
    private final List<SalesRecord> sales;
    private final List<StockRecord> stock;
    private final List<StockGraphRecord> productUsed;
    private final List<ReceiptRecord> receipts;
    private final List<MenuRecord> menu;
    private final List<XRecord> x;

    /**
     * Creates a container for the data displayed in the manager view.
     *
     * @param sales       the sales records
     * @param stock       the current inventory records
     * @param productUsed the stock usage records for graphing
     * @param receipts    the receipt records
     * @param menu        the menu item records
     * @param x           the hourly X-report records
     * @author Pablo Almanza
     * @author Ashley Hoang
     */

    public ManagerData(List<SalesRecord> sales, List<StockRecord> stock, List<StockGraphRecord> productUsed,
            List<ReceiptRecord> receipts, List<MenuRecord> menu, List<XRecord> x) {
        this.sales = sales;
        this.stock = stock;
        this.productUsed = productUsed;
        this.receipts = receipts;
        this.menu = menu;
        this.x = x;
    }

    // Getter functions
    /**
     * Returns the sales records.
     *
     * @return the sales records displayed by the manager view
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public List<SalesRecord> getSales() {
        return sales;
    }

    /**
     * Returns the inventory records.
     *
     * @return the current inventory records
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public List<StockRecord> getStock() {
        return stock;
    }

    /**
     * Returns the stock usage records used for graphing.
     *
     * @return the stock usage records
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public List<StockGraphRecord> getStockGraphRecord() {
        return productUsed;
    }

    /**
     * Returns the receipt records.
     *
     * @return the receipt records displayed by the manager view
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public List<ReceiptRecord> getReceipts() {
        return receipts;
    }

    /**
     * Returns the active menu records.
     *
     * @return the menu records displayed by the manager view
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public List<MenuRecord> getMenu() {
        return menu;
    }

    /**
     * Returns the hourly X-report records.
     *
     * @return the hourly X-report records
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    public List<XRecord> getX() {
        return x;
    }
}
