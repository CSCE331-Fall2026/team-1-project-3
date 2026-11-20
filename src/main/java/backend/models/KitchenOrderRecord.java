package backend.models;

import java.util.List;

/** A receipt as the kitchen sees it: what to prepare, without prices. */
public record KitchenOrderRecord(int receiptId, String timestamp, String status, List<Line> items) {
    public record Line(int itemId, String name, int quantity) {
    }
}
