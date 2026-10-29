package backend.api;

import backend.models.MenuRecord;
import backend.models.ReceiptRecord;
import backend.models.StockRecord;
import backend.models.XRecord;

/** JSON shapes shared by the controllers. Field names are the contract with the React client. */
public final class Dtos {
    private Dtos() {
    }

    // ---- responses ----
    public record MenuItem(int itemId, String name, long priceCents, int categoryId) {
        public static MenuItem of(MenuRecord r) {
            return new MenuItem(r.getItemID(), r.getName(), Money.toCents(r.getPrice()), r.getCategoryID());
        }
    }

    public record Stock(int inventoryId, String name, int stock, int minStock, String nextShipment, String shelfLife) {
        public static Stock of(StockRecord r) {
            return new Stock(r.getInventoryID(), r.getName(), r.getStock(), r.getMinStock(),
                    r.getNextShipment(), r.getShelfLife());
        }
    }

    public record StockResult(Integer stock, boolean updated, boolean missing) {
    }

    /** One bar in the Items Sold / Product Usage charts. */
    public record Point(String label, Number value) {
    }

    public record Receipt(int receiptId, int itemId, String itemName, int quantity, long lineTotalCents,
            String timestamp) {
        public static Receipt of(ReceiptRecord r) {
            // price_at_sale holds the complete line total
            return new Receipt(r.getReceiptID(), r.getItemID(), r.getItemName(), r.getQuantity(),
                    Money.toCents(r.getLineTotal()), r.getTimestamp());
        }
    }

    public record XPoint(String hour, long orders, long revenueCents) {
        public static XPoint of(XRecord r) {
            return new XPoint(r.getHour(), r.getQuantity(), Math.round(r.getRevenue() * 100));
        }
    }

    // ---- requests (boxed types so a missing field arrives as null and can be rejected) ----
    public record CheckoutRequest(Integer locationId, Integer userId, java.util.List<Line> items) {
        public record Line(Integer itemId, Integer quantity, Long listPriceCents, Long totalCents) {
        }
    }

    public record NewMenuItem(Integer itemId, Integer categoryId, String nutrition, Long priceCents,
            String unitSize, String name) {
    }

    public record PriceChange(Long priceCents) {
    }

    public record StockChange(Integer delta) {
    }
}
