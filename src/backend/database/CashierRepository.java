package backend.database;

import backend.models.MenuRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;

/** Handles cashier menu reads and checkout transactions. */
public final class CashierRepository {
    public enum CheckoutOutcome {
        SUCCESS, FAILURE, UNCERTAIN
    }

    public List<MenuRecord> loadMenu() throws Exception {
        final String SQL = "SELECT name, price, item_id, category_id FROM item "
                                + "WHERE category_id BETWEEN 1 AND 3 AND active = TRUE "
                                + "ORDER BY category_id, item_id";

        List<MenuRecord> menu = new ArrayList<>();
        try (
                Connection connection = DatabaseConnection.open();
                PreparedStatement statement = connection.prepareStatement(SQL);
                ResultSet resultSet = statement.executeQuery();
            ) {
            while (resultSet.next()) {
                menu.add(new MenuRecord(resultSet.getInt("item_id"), resultSet.getBigDecimal("price"),
                        resultSet.getString("name"), resultSet.getInt("category_id")));
            }
        }
        catch (Exception e) {
            System.out.println(e);
            throw e;
        }
        return menu;
    }

    public CheckoutOutcome checkout(int locationId, int userId,
            TreeMap<Integer, Integer> checkoutCart,
            TreeMap<Integer, BigDecimal> checkoutPrices,
            TreeMap<Integer, BigDecimal> checkoutTotals) {
        Connection connection = null;
        try {
            connection = DatabaseConnection.open();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement receipt = connection.prepareStatement(
                        "INSERT INTO receipt (location_id, user_id, receipt_timestamp) VALUES (?, ?, DATE_TRUNC('second', CURRENT_TIMESTAMP)) RETURNING receipt_id");
                        PreparedStatement itemValidation = connection.prepareStatement(
                                "SELECT i.active, COUNT(il.inventory_id) AS ingredient_count "
                                        + "FROM item i LEFT JOIN ingredient_list il ON il.item_id = i.item_id "
                                        + "WHERE i.item_id = ? GROUP BY i.active");
                        PreparedStatement ingredients = connection.prepareStatement(
                                "SELECT inventory_id FROM ingredient_list WHERE item_id = ? ORDER BY inventory_id FOR SHARE");
                        PreparedStatement update = connection.prepareStatement(
                                "UPDATE inventory SET stock = stock - ? WHERE inventory_id = ? AND stock >= ?");
                        PreparedStatement receiptItem = connection.prepareStatement(
                                "INSERT INTO receipt_item (receipt_id, item_id, quantity, price_at_sale) "
                                        + "SELECT ?, item_id, ?, ? FROM item "
                                        + "WHERE item_id = ? AND active = TRUE AND price = ?")) {
                    for (Integer itemId : checkoutCart.keySet()) {
                        itemValidation.setInt(1, itemId);
                        try (ResultSet results = itemValidation.executeQuery()) {
                            if (!results.next() || !results.getBoolean("active")
                                    || results.getInt("ingredient_count") == 0) {
                                throw new SQLException("Item is unavailable or has no ingredient mapping: " + itemId);
                            }
                        }
                        if (!checkoutPrices.containsKey(itemId))
                            throw new SQLException("Price snapshot is missing for item " + itemId);
                    }
                    receipt.setInt(1, locationId);
                    receipt.setInt(2, userId);
                    int receiptId;
                    try (ResultSet results = receipt.executeQuery()) {
                        if (!results.next())
                            throw new SQLException("Unable to create receipt");
                        receiptId = results.getInt("receipt_id");
                    }
                    TreeMap<Integer, Integer> requiredInventory = new TreeMap<>();
                    for (Map.Entry<Integer, Integer> entry : checkoutCart.entrySet()) {
                        int itemId = entry.getKey();
                        int expectedIngredientCount;
                        itemValidation.setInt(1, itemId);
                        try (ResultSet results = itemValidation.executeQuery()) {
                            if (!results.next() || !results.getBoolean("active")
                                    || results.getInt("ingredient_count") == 0) {
                                throw new SQLException("Item is no longer available: " + itemId);
                            }
                            expectedIngredientCount = results.getInt("ingredient_count");
                        }
                        int mappedIngredientCount = 0;
                        ingredients.setInt(1, itemId);
                        try (ResultSet results = ingredients.executeQuery()) {
                            while (results.next()) {
                                requiredInventory.merge(results.getInt("inventory_id"), entry.getValue(), Integer::sum);
                                mappedIngredientCount++;
                            }
                        }
                        if (mappedIngredientCount != expectedIngredientCount)
                            throw new SQLException("Ingredient mapping changed for item " + itemId);
                    }
                    for (Map.Entry<Integer, Integer> entry : requiredInventory.entrySet()) {
                        update.setInt(1, entry.getValue());
                        update.setInt(2, entry.getKey());
                        update.setInt(3, entry.getValue());
                        if (update.executeUpdate() == 0)
                            throw new SQLException("Insufficient inventory for checkout");
                    }
                    for (Map.Entry<Integer, Integer> entry : checkoutCart.entrySet()) {
                        int itemId = entry.getKey();
                        BigDecimal lineTotal = checkoutTotals.get(itemId);
                        if (lineTotal.scale() > 2 || lineTotal.signum() < 0
                                || lineTotal.compareTo(new BigDecimal("99999999.99")) > 0)
                            throw new SQLException("Checkout total is outside the database price range");
                        receiptItem.setInt(1, receiptId);
                        receiptItem.setInt(2, entry.getValue());
                        receiptItem.setBigDecimal(3, lineTotal);
                        receiptItem.setInt(4, itemId);
                        receiptItem.setBigDecimal(5, checkoutPrices.get(itemId));
                        if (receiptItem.executeUpdate() == 0)
                            throw new SQLException("Item price or availability changed: " + itemId);
                    }
                }
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    return CheckoutOutcome.UNCERTAIN;
                }
                return CheckoutOutcome.FAILURE;
            }
            try {
                connection.commit();
                return CheckoutOutcome.SUCCESS;
            } catch (SQLException commitException) {
                return CheckoutOutcome.UNCERTAIN;
            }
        } catch (SQLException | ClassNotFoundException exception) {
            return CheckoutOutcome.FAILURE;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }
}
