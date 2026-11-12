package backend.database; 

import backend.models.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    /** Current inventory, lowest stock first. One query; used by the kitchen view. */
    public List<StockRecord> loadStock() throws Exception {
        try (Connection connection = DatabaseConnection.open(); Statement statement = connection.createStatement()) {
            return readStock(statement);
        }
    }

    private static List<StockRecord> readStock(Statement statement) throws SQLException {
        List<StockRecord> stock = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT inventory_id, name, stock, min_stock, next_shipment, shelf_life "
                        + "FROM inventory ORDER BY stock ASC, inventory_id ASC")) {
            while (resultSet.next())
                stock.add(new StockRecord(resultSet.getInt("inventory_id"), resultSet.getString("name"),
                        resultSet.getInt("stock"), resultSet.getInt("min_stock"),
                        resultSet.getString("next_shipment"), resultSet.getString("shelf_life")));
        }
        return stock;
    }

    private static List<Integer> loadIngredientIDs(Connection connection, int itemId) throws SQLException {
        List<Integer> ingredientIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT inventory_id FROM ingredient_list WHERE item_id = ? ORDER BY inventory_id")) {
            
            statement.setInt(1, itemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ingredientIds.add(resultSet.getInt("inventory_id"));
                }
            }
        }

        return ingredientIds;
    }

    public List<InventoryRecord> loadInventory() throws Exception {
        List<InventoryRecord> inventory = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open();
            PreparedStatement statement = connection.prepareStatement(
                "SELECT inventory_id, name, nutrition, stock, min_stock, next_shipment, shelf_life, unit_size "
                + "FROM inventory ORDER BY inventory_id")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    inventory.add(new InventoryRecord(
                        resultSet.getInt("inventory_id"),
                        resultSet.getString("name"),
                        resultSet.getString("nutrition"),
                        resultSet.getInt("stock"),
                        resultSet.getInt("min_stock"),
                        resultSet.getString("next_shipment"),
                        resultSet.getString("shelf_life"),
                        resultSet.getFloat("unit_size")
                    ));
                }
            }
        }
        return inventory;
    }


    public ManagerData loadDashboard() throws Exception {
        List<SalesRecord> sales = new ArrayList<>();
        List<StockRecord> stock;
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
            stock = readStock(statement);
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
                    "SELECT item_id, name, price, category_id, nutrition, unit_size FROM item WHERE active = TRUE ORDER BY item_id")) {
                while (resultSet.next())
                    menu.add(new MenuRecord(resultSet.getInt("item_id"), resultSet.getBigDecimal("price"),
                            resultSet.getString("name"), resultSet.getInt("category_id"), loadIngredientIDs(connection, resultSet.getInt("item_id")), 
                            resultSet.getString("nutrition"), resultSet.getString("unit_size")));
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
        BigDecimal itemPrice, String size, String itemName, List<Integer> ingredientIds) throws Exception {

        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                //Insertion of new menu item
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO item (item_id, category_id, nutrition, price, unit_size, name, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, TRUE)")) {
                    stmt.setInt(1, itemId);
                    stmt.setInt(2, categoryId);
                    stmt.setString(3, nutritionInfo);
                    stmt.setBigDecimal(4, itemPrice);
                    stmt.setString(5, size);
                    stmt.setString(6, itemName);
                    if (stmt.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }

                //Insertion of ingredients
                if (ingredientIds != null && !ingredientIds.isEmpty()) {
                    try (PreparedStatement ingStmt = connection.prepareStatement(
                            "INSERT INTO ingredient_list (item_id, inventory_id) VALUES (?, ?)")) {
                        for (Integer invId : ingredientIds) {
                            if (invId != null) {
                                ingStmt.setInt(1, itemId);
                                ingStmt.setInt(2, invId);
                                ingStmt.addBatch();
                            }
                        }
                        ingStmt.executeBatch();
                    }
                }

                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                e.printStackTrace();
                throw e;
            }
        }
    }

    public List<Integer> getItemIngredients(int itemId) throws Exception {
        List<Integer> ingredientIds = new ArrayList<>();
        try (Connection conn = DatabaseConnection.open();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT inventory_id FROM ingredient_list WHERE item_id = ?")) {
            stmt.setInt(1, itemId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ingredientIds.add(rs.getInt("inventory_id"));
                }
            }
        }
        return ingredientIds;
    }

    public boolean updateMenuItem(int itemId, int newItemId, int categoryId, String nutritionInfo,
            BigDecimal itemPrice, String size, String itemName, List<Integer> ingredientIds) throws Exception {
        
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                // Clear the recipe before the ID changes: ingredient_list.item_id is ON UPDATE CASCADE,
                // so rows left in place would follow the item to newItemId and collide with the re-insert.
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM ingredient_list WHERE item_id = ?")) {
                    stmt.setInt(1, itemId);
                    stmt.executeUpdate();
                }

                // Update item details
                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE item SET item_id = ?, category_id = ?, nutrition = ?, price = ?, unit_size = ?, name = ? "
                        + "WHERE item_id = ?")) {
                    stmt.setInt(1, newItemId);
                    stmt.setInt(2, categoryId);
                    stmt.setString(3, nutritionInfo);
                    stmt.setBigDecimal(4, itemPrice);
                    stmt.setString(5, size);
                    stmt.setString(6, itemName);
                    stmt.setInt(7, itemId);
                    if (stmt.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }

                // Insert new ingredients
                if (ingredientIds != null && !ingredientIds.isEmpty()) {
                    try (PreparedStatement stmt = connection.prepareStatement(
                            "INSERT INTO ingredient_list (item_id, inventory_id) VALUES (?, ?)")) {
                        for (Integer invId : ingredientIds) {
                            if (invId != null) {
                                stmt.setInt(1, newItemId);
                                stmt.setInt(2, invId);
                                stmt.addBatch();
                            }
                        }
                        stmt.executeBatch();
                    }
                }

                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                e.printStackTrace();
                throw e;
            }
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
