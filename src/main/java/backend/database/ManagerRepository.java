package backend.database;

import backend.models.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Handles manager dashboard reads and updates in PostgreSQL. */
public final class ManagerRepository {
    public static final class GraphPoint {
        public final Number value;
        public final String label;

        public GraphPoint(Number value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    public ManagerData loadDashboard() throws Exception {
        List<SalesRecord> sales = new ArrayList<>();
        List<StockRecord> stock = new ArrayList<>();
        List<StockGraphRecord> productsUsed = new ArrayList<>();
        List<ReceiptRecord> receipts = new ArrayList<>();
        List<MenuRecord> menu = new ArrayList<>();
        List<XRecord> x = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open(); Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT i.item_id, i.name AS itemNames, SUM(ri.quantity) AS numSold FROM receipt_item ri "
                            + "JOIN item i ON i.item_id = ri.item_id GROUP BY i.name, i.item_id ORDER BY numSold DESC")) {
                while (resultSet.next())
                    sales.add(new SalesRecord(resultSet.getInt("item_id"), resultSet.getString("itemNames"),
                            resultSet.getLong("numSold")));
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT inventory_id, name, stock, min_stock, next_shipment, shelf_life "
                            + "FROM inventory ORDER BY stock ASC, inventory_id ASC")) {
                while (resultSet.next())
                    stock.add(new StockRecord(resultSet.getInt("inventory_id"), resultSet.getString("name"),
                            resultSet.getInt("stock"), resultSet.getInt("min_stock"),
                            resultSet.getString("next_shipment"), resultSet.getString("shelf_life")));
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT i.inventory_id, i.name AS inventoryNames, SUM(ri.quantity) AS stockUsed "
                            + "FROM receipt_item ri JOIN item it ON ri.item_id = it.item_id "
                            + "JOIN ingredient_list il ON it.item_id = il.item_id "
                            + "JOIN inventory i ON il.inventory_id = i.inventory_id JOIN receipt r ON ri.receipt_id = r.receipt_id "
                            + "GROUP BY i.inventory_id, i.name ORDER BY stockUsed DESC")) {
                while (resultSet.next())
                    productsUsed.add(new StockGraphRecord(resultSet.getInt("inventory_id"),
                            resultSet.getString("inventoryNames"), resultSet.getLong("stockUsed")));
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT r.receipt_id, i.item_id, i.name AS item_name, ri.quantity, ri.price_at_sale, r.receipt_timestamp "
                            + "FROM receipt r JOIN receipt_item ri ON r.receipt_id = ri.receipt_id "
                            + "JOIN item i ON ri.item_id = i.item_id WHERE r.receipt_id IN "
                            + "(SELECT receipt_id FROM receipt ORDER BY receipt_id DESC LIMIT 20) "
                            + "ORDER BY r.receipt_id DESC, ri.receipt_item_id")) {
                while (resultSet.next())
                    receipts.add(new ReceiptRecord(resultSet.getInt("receipt_id"), resultSet.getInt("item_id"),
                            resultSet.getString("item_name"), resultSet.getInt("quantity"),
                            resultSet.getBigDecimal("price_at_sale"), resultSet.getString("receipt_timestamp")));
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT item_id, name, price, category_id FROM item WHERE active = TRUE ORDER BY item_id")) {
                while (resultSet.next())
                    menu.add(new MenuRecord(resultSet.getInt("item_id"), resultSet.getBigDecimal("price"),
                            resultSet.getString("name"), resultSet.getInt("category_id")));
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT EXTRACT(HOUR FROM receipt_timestamp) AS hour, COUNT(*) as order_count, SUM(total_cost) AS total_sales "
                            + "FROM (SELECT r.receipt_id, r.receipt_timestamp, SUM(ri.price_at_sale) AS total_cost "
                            + "FROM receipt r JOIN receipt_item ri ON ri.receipt_id = r.receipt_id "
                            + "WHERE r.receipt_timestamp >= CURRENT_DATE AND r.receipt_timestamp <= date_trunc('hour', current_timestamp) "
                            + "GROUP BY r.receipt_id, r.receipt_timestamp) AS receipt "
                            + "GROUP BY EXTRACT(HOUR FROM receipt_timestamp) ORDER BY hour")) {
                while (resultSet.next())
                    x.add(new XRecord(resultSet.getString("hour"), resultSet.getLong("order_count"),
                            resultSet.getDouble("total_sales")));
            }
        }
        return new ManagerData(sales, stock, productsUsed, receipts, menu, x);
    }

    public List<GraphPoint> loadSalesGraph(String start, String end) throws Exception {
        List<GraphPoint> points = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT i.item_id, i.name AS itemNames, SUM(ri.quantity) AS numSold "
                                + "FROM receipt_item ri JOIN item i ON i.item_id = ri.item_id "
                                + "JOIN receipt r ON r.receipt_id = ri.receipt_id "
                                + "WHERE r.receipt_timestamp >= CAST(? AS timestamp) AND r.receipt_timestamp <= CAST(? AS timestamp) "
                                + "GROUP BY i.name, i.item_id ORDER BY numSold DESC")) {
            statement.setString(1, start);
            statement.setString(2, end);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next())
                    points.add(new GraphPoint(results.getInt("numSold"),
                            "#" + results.getInt("item_id") + "-" + results.getString("itemNames") + ' '
                                    + results.getInt("numSold")));
            }
        }
        return points;
    }

    public StockUpdateResult updateStock(int inventoryId, int stockDelta) throws Exception {
        try (Connection connection = DatabaseConnection.open();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE inventory SET stock = stock + ? WHERE inventory_id = ? AND stock + ? >= 0 RETURNING stock")) {
            statement.setInt(1, stockDelta);
            statement.setInt(2, inventoryId);
            statement.setInt(3, stockDelta);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next())
                    return new StockUpdateResult(results.getInt("stock"), true, false);
            }
            try (PreparedStatement currentStock = connection.prepareStatement(
                    "SELECT stock FROM inventory WHERE inventory_id = ?")) {
                currentStock.setInt(1, inventoryId);
                try (ResultSet results = currentStock.executeQuery()) {
                    if (results.next())
                        return new StockUpdateResult(results.getInt("stock"), false, false);
                }
            }
            return new StockUpdateResult(null, false, true);
        }
    }

    public List<GraphPoint> loadStockGraph(String start, String end) throws Exception {
        List<GraphPoint> points = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT i.inventory_id, i.name AS inventoryNames, SUM(ri.quantity) AS stockUsed "
                                + "FROM receipt_item ri JOIN item it ON ri.item_id = it.item_id "
                                + "JOIN ingredient_list il ON it.item_id = il.item_id "
                                + "JOIN inventory i ON il.inventory_id = i.inventory_id JOIN receipt r ON ri.receipt_id = r.receipt_id "
                                + "WHERE r.receipt_timestamp >= CAST(? AS timestamp) AND r.receipt_timestamp <= CAST(? AS timestamp) "
                                + "GROUP BY i.inventory_id, i.name ORDER BY stockUsed DESC")) {
            statement.setString(1, start);
            statement.setString(2, end);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next())
                    points.add(new GraphPoint(results.getInt("stockUsed"),
                            "#" + results.getInt("inventory_id") + "-" + results.getString("inventoryNames") + ' '
                                    + results.getInt("stockUsed")));
            }
        }
        return points;
    }

    public Double updatePrice(int itemId, BigDecimal newPrice) throws Exception {
        try (Connection connection = DatabaseConnection.open();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE item SET price = ? WHERE item_id = ? AND ? >= 0 RETURNING price")) {
            statement.setBigDecimal(1, newPrice);
            statement.setInt(2, itemId);
            statement.setBigDecimal(3, newPrice);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getBigDecimal("price").doubleValue() : null;
            }
        }
    }

    public boolean addMenuItem(int itemId, int categoryId, String nutritionInfo,
            BigDecimal itemPrice, String size, String itemName) throws Exception {
        try (Connection connection = DatabaseConnection.open();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO item (item_id, category_id, nutrition, price, unit_size, name, active) "
                                + "VALUES (?, ?, ?, ?, ?, ?, TRUE)")) {
            statement.setInt(1, itemId);
            statement.setInt(2, categoryId);
            statement.setString(3, nutritionInfo);
            statement.setBigDecimal(4, itemPrice);
            statement.setString(5, size);
            statement.setString(6, itemName);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean removeMenuItem(int itemId) throws Exception {
        try (Connection connection = DatabaseConnection.open();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE item SET active = FALSE WHERE item_id = ? AND active = TRUE")) {
            statement.setInt(1, itemId);
            return statement.executeUpdate() > 0;
        }
    }

    public List<XRecord> loadXReport() throws Exception {
        List<XRecord> records = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT EXTRACT(HOUR FROM receipt_timestamp) AS hour, COUNT(*) as order_count, SUM(total_cost) AS total_sales "
                                + "FROM (SELECT r.receipt_id, r.receipt_timestamp, SUM(ri.price_at_sale) AS total_cost "
                                + "FROM receipt r JOIN receipt_item ri ON ri.receipt_id = r.receipt_id "
                                + "WHERE r.receipt_timestamp >= CURRENT_DATE AND r.receipt_timestamp <= date_trunc('hour', current_timestamp) "
                                + "GROUP BY r.receipt_id, r.receipt_timestamp) AS receipt "
                                + "GROUP BY EXTRACT(HOUR FROM receipt_timestamp) ORDER BY hour")) {
            try (ResultSet results = statement.executeQuery()) {
                while (results.next())
                    records.add(new XRecord(results.getString("hour"), results.getLong("order_count"),
                            results.getDouble("total_sales")));
            }
        }
        return records;
    }
}
