package frontend.controllers;

import backend.database.ManagerRepository;
import backend.models.*;
import frontend.components.MenuListCell;
import frontend.components.StockListCell;
import frontend.models.MenuItem;
import frontend.models.StockItem;

import javafx.fxml.FXML;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Label;
import javafx.scene.control.Accordion;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Pair;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import java.util.Optional;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.TextInputDialog;
import javafx.concurrent.Task;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

/**
 * Controller for the manager view and its inventory, sales, receipt, and report
 * displays.
 */
public class ManagerController {
    /**
     * Creates a manager controller for use by the JavaFX FXML loader.
     */
    public ManagerController() {
    }

    // Variables
    private static final ExecutorService DATABASE_EXECUTOR = new ThreadPoolExecutor(
            4, 4, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16),
            runnable -> {
                Thread thread = new Thread(runnable, "manager-database-worker");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static final BigDecimal MAX_ITEM_PRICE = new BigDecimal("99999999.99");
    private static final int MAX_ITEM_STOCK = 99999999;

    private boolean controllerActive = true;
    private Timeline hourlyUpdateTimeline; // for x report (refresh hourly)
    @FXML
    BarChart<Number, String> salesGraph; // Sales graph display
    /**
     * List view displaying the editable menu items.
     */
    @FXML
    public ListView<MenuItem> menuListView; // Menu list creation
    /**
     * List view displaying the editable stock items.
     */
    @FXML
    public ListView<StockItem> stockListView; // Preserve remote stock editing through StockItem and StockListCell.
    @FXML
    BarChart<Number, String> stockGraph; // Stock/product used graph display
    @FXML
    Accordion receiptAccordion; // Receipt accordion display
    @FXML
    LineChart<String, Number> xReportGraph; // X Report Line Graph

    /**
     * This method runs automatically when the FXML loads
     *
     * @author Ashley Hoang
     */
    @FXML
    public void initialize() {
        /*
         * // Set up what happens when button is clicked
         * queryButton.setOnAction(event -> runQuery());
         * closeButton.setOnAction(event -> closeWindow());
         */
        runQuery();
        updateHourly();
    }

    /**
     * Your method to run the database query
     *
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    private void runQuery() {
        stockListView.setDisable(true);
        menuListView.setDisable(true);
        Task<ManagerData> task = new Task<>() {
            @Override
            protected ManagerData call() throws Exception {
                return new ManagerRepository().loadDashboard();
            }
        };
        task.setOnSucceeded(event -> {
            if (controllerActive) {
                applyManagerData(task.getValue());
                stockListView.setDisable(false);
                menuListView.setDisable(false);
            }
        });
        task.setOnFailed(event -> {
            if (controllerActive) {
                stockListView.setDisable(false);
                menuListView.setDisable(false);
                showError("Manager Load Failed", "The manager data could not be loaded.",
                        "Please check the database connection and try again.");
            }
        });
        if (!startTask(task)) {
            stockListView.setDisable(false);
            menuListView.setDisable(false);
            showError("Manager Load Failed", "The database is busy.", "Please try again.");
        }
    }

    /**
     * Applies the manager data to the GUI components.
     *
     * @param data The compiled manager data containing sales, stock, receipts, and
     *             menu information.
     * @author Pablo Almanza
     * @author Ashley Hoang
     */
    private void applyManagerData(ManagerData data) {
        // Sales Graph
        salesGraph.getData().clear();
        XYChart.Series<Number, String> salesSeries = new XYChart.Series<>();
        for (SalesRecord sale : data.getSales()) {
            long numSold = sale.getQuantity();
            String name = sale.getName();
            int id = sale.getID();
            String label = "#" + id + "-" + name + ' ' + numSold;
            salesSeries.getData().add(new XYChart.Data<>(numSold, label));
        }
        salesGraph.getData().add(salesSeries);

        // Stock list view
        ObservableList<StockItem> stockItems = FXCollections.observableArrayList();
        for (StockRecord item : data.getStock()) {
            stockItems.add(new StockItem(item.getInventoryID(), item.getName(), item.getStock(), item.getMinStock(),
                    item.getNextShipment(), item.getShelfLife()));
        }
        stockListView.setItems(stockItems);
        stockListView.setCellFactory(lv -> new StockListCell(this));

        // product used/stock graph
        stockGraph.getData().clear();
        XYChart.Series<Number, String> stockSeries = new XYChart.Series<>();
        for (StockGraphRecord stock : data.getStockGraphRecord()) {
            long stockUsed = stock.getQuantity();
            String name = stock.getName();
            int id = stock.getID();
            String label = "#" + id + "-" + name + ' ' + stockUsed;
            stockSeries.getData().add(new XYChart.Data<>(stockUsed, label));
        }
        stockGraph.getData().add(stockSeries);

        // receipt accordion
        receiptAccordion.getPanes().clear();
        int currentReceiptID = -1;
        VBox itemList = null;
        BigDecimal receiptTotal = BigDecimal.ZERO;
        TitledPane receiptPane = null;
        for (ReceiptRecord receipt : data.getReceipts()) {
            String timestamp = receipt.getTimestamp();
            if (receipt.getReceiptID() != currentReceiptID) {
                if (receiptPane != null)
                    itemList.getChildren().add(new Label(String.format("Total: $%.2f\n\n%s", receiptTotal, timestamp)));
                currentReceiptID = receipt.getReceiptID();
                receiptTotal = BigDecimal.ZERO;
                itemList = new VBox(5);
                itemList.setPadding(new Insets(10));
                receiptPane = new TitledPane("Receipt " + receipt.getReceiptID(), itemList);
                receiptPane.setMaxWidth(Double.MAX_VALUE);
                receiptAccordion.getPanes().add(receiptPane);
            }
            int itemID = receipt.getItemID();
            String itemName = receipt.getItemName();
            int quantity = receipt.getQuantity();
            // Checkout stores price_at_sale as the complete line total.
            BigDecimal lineTotal = receipt.getLineTotal();
            if (lineTotal == null)
                lineTotal = BigDecimal.ZERO;
            receiptTotal = receiptTotal.add(lineTotal);
            itemList.getChildren()
                    .add(new Label(String.format("#%d %s    x%d    $%.2f", itemID, itemName, quantity, lineTotal)));
        }
        if (receiptPane != null)
            itemList.getChildren().add(new Label(
                    String.format("Total: $%.2f", receiptTotal)));

        // menu list view
        ObservableList<MenuItem> menuItems = FXCollections.observableArrayList();
        for (MenuRecord item : data.getMenu()) {
            menuItems.add(new MenuItem(item.getItemID(), item.getName(), item.getPrice().doubleValue()));
        }
        menuListView.setItems(menuItems);
        menuListView.setCellFactory(lv -> new MenuListCell(this));

        // x report
        xReportGraph.getData().clear();
        XYChart.Series<String, Number> xSeries = new XYChart.Series<>();
        for (XRecord x : data.getX()) {
            long numSold = x.getQuantity();
            String hour = x.getHour();
            double revenue = x.getRevenue();
            String label = hour + ":00\nSales: " + numSold + "\nRevenue: $" + revenue;
            xSeries.getData().add(new XYChart.Data<>(label, numSold));
        }
        xReportGraph.getData().add(xSeries);
    }

    /*-------------------------------------------Sales Graph-----------------------------------------*/
    /**
     * Opens a dialog to allow the user to change the time period for the sales
     * graph.
     *
     * @param event The ActionEvent triggered when the user selects to change the
     *              time period.
     * @author Ashley Hoang
     */
    @FXML
    public void changeSoldTimePeriod(ActionEvent event) {
        // Create a dialog to get the new time period from the user
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Change Time Period");
        dialog.setHeaderText("Enter the start and end time periods (inclusive):");

        // Ask for time periods
        TextField startDateField = new TextField();
        startDateField.setPromptText("ex. 2026-01-31 00:00:00");
        TextField endDateField = new TextField();
        endDateField.setPromptText("ex. 2026-01-31 23:59:59");

        // Grid pane
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.add(new Label("Start:"), 0, 0);
        grid.add(startDateField, 1, 0);
        grid.add(new Label("End:"), 0, 1);
        grid.add(endDateField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // when button is clicked, gets the strings of the time
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new Pair<>(startDateField.getText(), endDateField.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();
        if (result.isPresent()) {
            Pair<String, String> times = result.get();
            String newTimePeriod1 = times.getKey().trim();
            String newTimePeriod2 = times.getValue().trim();

            if (newTimePeriod1.isEmpty() || newTimePeriod2.isEmpty()) {
                showError("Invalid Input", "Both start and end time periods are required.", "");
                return;
            }

            try {
                // Update the stock graph with the new time period
                updateSalesGraph(newTimePeriod1, newTimePeriod2);
            } catch (IllegalArgumentException e) {
                showError("Invalid Input", "Please enter valid time periods.", e.getMessage());
            }
        }
    }

    /**
     * Updates the sales graph to display the number of items sold within the
     * specified time period.
     *
     * @param timePeriod1 The start time for which to display sales data.
     * @param timePeriod2 The end time for which to display sales data.
     * @author Ashley Hoang
     */
    private void updateSalesGraph(String timePeriod1, String timePeriod2) {
        Task<List<ManagerRepository.GraphPoint>> task = new Task<>() {
            @Override
            protected List<ManagerRepository.GraphPoint> call() throws Exception {
                return new ManagerRepository().loadSalesGraph(timePeriod1, timePeriod2);
            }
        };
        task.setOnSucceeded(event -> {
            if (controllerActive) {
                XYChart.Series<Number, String> soldSeries = new XYChart.Series<>();
                for (ManagerRepository.GraphPoint point : task.getValue()) {
                    soldSeries.getData().add(new XYChart.Data<>(point.value, point.label));
                }
                salesGraph.setTitle("Items Sold in From " + timePeriod1 + " to " + timePeriod2);
                salesGraph.getData().clear();
                salesGraph.getData().add(soldSeries);
            }
        });
        task.setOnFailed(event -> {
            if (controllerActive) {
                Throwable exception = task.getException();
                exception.printStackTrace();
                showError("Sales Graph Update Failed", "The sales graph could not be updated.",
                        "Please enter valid dates.");
            }
        });
        if (!startTask(task)) {
            showError("Sales Graph Update Failed", "The database is busy.", "Please try again.");
        }
    }

    /*-------------------------------------------Stock List-----------------------------------------*/
    /**
     * Prompts the user to enter a stock quantity adjustment
     * and validates the entered value.
     *
     * @author Ashley Hoang
     * @return user's inputted change in stock
     */
    public int inputNewStock() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Change Stock");
        dialog.setHeaderText("Enter the change in stock (add a '-' if decreasing stock): ");
        dialog.setContentText("Change in stock: ");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty())
            return 0;
        try {
            int stock = Integer.parseInt(result.get().trim());
            if ((MAX_ITEM_STOCK) < stock)
                throw new IllegalArgumentException();
            return stock;
        } catch (IllegalArgumentException e) {
            showDatabaseError("update stock", "Enter a stock from 0 to 99999999 with no decimal places.");
            return 0;
        }
    }

    /**
     * Updates the stock quantity of an item in the database.
     *
     * @author Ashley Hoang
     * @author Pablo Almanza
     * @param inventoryID The ID of the inventory item to update.
     * @param stockDelta  The change in stock quantity.
     * @param callback    A callback function that will be called with the result of
     *                    the stock update operation.
     * @throws IllegalArgumentException if there is an error with the list creation
     */
    public void updateStockInDatabase(int inventoryID, int stockDelta, Consumer<StockUpdateResult> callback) {
        Task<StockUpdateResult> task = new Task<>() {
            @Override
            protected StockUpdateResult call() throws Exception {
                return new ManagerRepository().updateStock(inventoryID, stockDelta);
            }
        };
        task.setOnSucceeded(event -> callback.accept(task.getValue()));
        task.setOnFailed(event -> {
            if (controllerActive)
                showDatabaseError("update stock", "The database update failed.");
            callback.accept(new StockUpdateResult(null, false, false));
        });
        if (!startTask(task)) {
            if (controllerActive)
                showDatabaseError("update stock", "The database is busy.");
            callback.accept(new StockUpdateResult(null, false, false));
        }
    }

    /*-------------------------------------------Stock Graph-----------------------------------------*/
    /**
     * Opens a dialog to allow the user to change the time period for the stock used
     * graph.
     *
     * @param event The ActionEvent triggered when the user selects to change the
     *              time period.
     * @author Ashley Hoang
     */
    @FXML
    public void changeStockTimePeriod(ActionEvent event) {
        // Create a dialog to get the new time period from the user
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Change Time Period");
        dialog.setHeaderText("Enter the start and end time periods (inclusive):");

        // Ask for time periods
        TextField startDateField = new TextField();
        startDateField.setPromptText("ex. 2026-01-31 00:00:00");
        TextField endDateField = new TextField();
        endDateField.setPromptText("ex. 2026-01-31 23:59:59");

        // Grid pane
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.add(new Label("Start:"), 0, 0);
        grid.add(startDateField, 1, 0);
        grid.add(new Label("End:"), 0, 1);
        grid.add(endDateField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // when button is clicked, gets the strings of the time
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new Pair<>(startDateField.getText(), endDateField.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();
        if (result.isPresent()) {
            Pair<String, String> times = result.get();
            String newTimePeriod1 = times.getKey().trim();
            String newTimePeriod2 = times.getValue().trim();

            if (newTimePeriod1.isEmpty() || newTimePeriod2.isEmpty()) {
                showError("Invalid Input", "Both start and end time periods are required.", "");
                return;
            }

            try {
                // Update the stock graph with the new time period
                updateStockGraph(newTimePeriod1, newTimePeriod2);
            } catch (IllegalArgumentException e) {
                showError("Invalid Input", "Please enter valid time periods.", e.getMessage());
            }
        }
    }

    /**
     * Updates the stock-used graph to display inventory
     * usage over the selected time period.
     *
     * @param timePeriod1 The start time for which to display stock data.
     * @param timePeriod2 The end time for which to display stock data.
     * @author Ashley Hoang
     */
    private void updateStockGraph(String timePeriod1, String timePeriod2) {
        Task<List<ManagerRepository.GraphPoint>> task = new Task<>() {
            @Override
            protected List<ManagerRepository.GraphPoint> call() throws Exception {
                return new ManagerRepository().loadStockGraph(timePeriod1, timePeriod2);
            }
        };
        task.setOnSucceeded(event -> {
            if (controllerActive) {
                XYChart.Series<Number, String> stockSeries = new XYChart.Series<>();
                for (ManagerRepository.GraphPoint point : task.getValue()) {
                    stockSeries.getData().add(new XYChart.Data<>(point.value, point.label));
                }
                stockGraph.setTitle("Stock Used From " + timePeriod1 + " to " + timePeriod2);
                stockGraph.getData().clear();
                stockGraph.getData().add(stockSeries);
            }
        });
        task.setOnFailed(event -> {
            if (controllerActive) {
                Throwable exception = task.getException();
                exception.printStackTrace();
                showError("Stock Graph Update Failed", "The stock graph could not be updated.",
                        "Please enter valid dates.");
            }
        });
        if (!startTask(task)) {
            showError("Stock Graph Update Failed", "The database is busy.", "Please try again.");
        }
    }

    /*-------------------------------------------Menu List-----------------------------------------*/
    /**
     * Prompts the user to enter a new price
     * and validates the entered value.
     *
     * @author Ashley Hoang
     * @return The validated price, or null if the user cancels or enters invalid
     *         input.
     */
    public BigDecimal inputNewPrice() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Change Price");
        dialog.setHeaderText("Enter the new price: ");
        dialog.setContentText("Price: ");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty())
            return null;
        try {
            BigDecimal price = new BigDecimal(result.get().trim());
            if (price.scale() > 2 || price.signum() < 0 || price.compareTo(MAX_ITEM_PRICE) > 0)
                throw new IllegalArgumentException();
            return price;
        } catch (IllegalArgumentException e) {
            showDatabaseError("update price",
                    "Enter a price from 0.00 to 99999999.99 with at most two decimal places.");
            return null;
        }
    }

    /**
     * Updates the price of an item in the database.
     *
     * @param itemID   The ID of the item in the item database
     * @param newPrice The new price of the item (computed by inputNewPrice)
     * @param callback A callback function that will be called with the result of
     *                 the price update operation (double on success).
     * @author Pablo Almanza
     */
    public void updatePriceInDatabase(int itemID, BigDecimal newPrice, Consumer<Double> callback) {
        Task<Double> task = new Task<>() {
            @Override
            protected Double call() throws Exception {
                return new ManagerRepository().updatePrice(itemID, newPrice);
            }
        };
        task.setOnSucceeded(event -> {
            if (controllerActive && task.getValue() == null)
                showDatabaseError("update price", "The price value could not be updated.");
            callback.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            if (controllerActive)
                showDatabaseError("update price", "The database update failed.");
            callback.accept(null);
        });
        if (!startTask(task)) {
            if (controllerActive)
                showDatabaseError("update price", "The database is busy.");
            callback.accept(null);
        }
    }

    /**
     * Adds a new menu item to the database based on user input from a dialog.
     *
     * @author Ashley Hoang
     * @param event The ActionEvent triggered when the user clicks the "Add Menu
     *              Item" button.
     */
    @FXML
    public void addMenuItem(ActionEvent event) {
        // Get the input values from the user
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Menu Item");
        dialog.setHeaderText("Enter the details for the new menu item:");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        // Text fields for user input
        TextField itemIDField = new TextField();
        itemIDField.setPromptText("Item ID");
        TextField categoryIDField = new TextField();
        categoryIDField.setPromptText("Category ID");
        TextField nutritionInfoField = new TextField();
        nutritionInfoField.setPromptText("Nutrition Info");
        TextField itemPriceField = new TextField();
        itemPriceField.setPromptText("Item Price");
        TextField sizeField = new TextField();
        sizeField.setPromptText("Unit size");
        TextField itemNameField = new TextField();
        itemNameField.setPromptText("Item Name");

        grid.add(new Label("Item ID:"), 0, 0);
        grid.add(itemIDField, 1, 0);

        grid.add(new Label("Category ID:"), 0, 1);
        grid.add(categoryIDField, 1, 1);

        grid.add(new Label("Nutrition Info:"), 0, 2);
        grid.add(nutritionInfoField, 1, 2);

        grid.add(new Label("Item Price:"), 0, 3);
        grid.add(itemPriceField, 1, 3);

        grid.add(new Label("Unit Size:"), 0, 4);
        grid.add(sizeField, 1, 4);

        grid.add(new Label("Item Name:"), 0, 5);
        grid.add(itemNameField, 1, 5);

        dialog.getDialogPane().setContent(grid);

        // Show the dialog and wait for user input
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == addButtonType) {
            try {
                // Add the new menu item to the database
                int itemID = Integer.parseInt(itemIDField.getText());
                int categoryID = Integer.parseInt(categoryIDField.getText());
                String nutritionInfo = nutritionInfoField.getText();
                BigDecimal itemPrice = new BigDecimal(itemPriceField.getText().trim());
                if (itemPrice.scale() > 2 || itemPrice.signum() < 0
                        || itemPrice.compareTo(MAX_ITEM_PRICE) > 0)
                    throw new IllegalArgumentException("Invalid price");
                String size = sizeField.getText();
                String itemName = itemNameField.getText();
                Button addItemButton = (Button) event.getSource();
                addItemButton.setDisable(true);
                addMenuItemToDatabase(itemID, categoryID, nutritionInfo, itemPrice, size, itemName,
                        added -> {
                            if (controllerActive) {
                                addItemButton.setDisable(false);
                                if (added)
                                    reloadApplication();
                            }
                        });
            } catch (NumberFormatException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Input Error");
                alert.setHeaderText("Invalid Input");
                alert.setContentText("Please provide valid item, category, and price values.");
                alert.showAndWait();
            } catch (IllegalArgumentException e) {
                showDatabaseError("add menu item",
                        "Please provide a price from 0.00 to 99999999.99 with at most two decimal places.");
            } catch (Exception e) {
                showDatabaseError("add menu item", "The menu item was not added.");
            }
        }
    }

    /**
     * Adds a new menu item to the database.
     *
     * @param itemID        The ID of the new menu item.
     * @param categoryID    The category ID of the new menu item.
     * @param nutritionInfo The nutrition information of the new menu item.
     * @param itemPrice     The price of the new menu item.
     * @param size          The unit size of the new menu item.
     * @param itemName      The name of the new menu item.
     * @param callback      A callback function that will be called with the result
     *                      of the add operation (true on success, false on
     *                      failure).
     * @author Pablo Almanza
     */
    private void addMenuItemToDatabase(int itemID, int categoryID, String nutritionInfo,
            BigDecimal itemPrice, String size, String itemName, Consumer<Boolean> callback) {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return new ManagerRepository().addMenuItem(itemID, categoryID, nutritionInfo,
                        itemPrice, size, itemName);
            }
        };
        task.setOnSucceeded(event -> callback.accept(task.getValue()));
        task.setOnFailed(event -> {
            if (controllerActive)
                showDatabaseError("add menu item", "The menu item was not added.");
            callback.accept(false);
        });
        if (!startTask(task)) {
            if (controllerActive)
                showDatabaseError("add menu item", "The database is busy.");
            callback.accept(false);
        }
    }

    /**
     * Removes the specified menu item from the database.
     *
     * @param item     The menu item to remove.
     * @param callback A callback function that will be called with the result of
     *                 the removal operation.
     * @author Ashley Hoang
     * @author Pablo Almanza
     */

    public void removeMenuItemFromDatabase(MenuItem item, Consumer<Boolean> callback) {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return new ManagerRepository().removeMenuItem(item.getItemID());
            }
        };
        task.setOnSucceeded(event -> {
            if (controllerActive && !task.getValue())
                showDatabaseError("remove menu item", "The menu item was not active.");
            callback.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            if (controllerActive)
                showDatabaseError("remove menu item", "The database update failed.");
            callback.accept(false);
        });
        if (!startTask(task)) {
            if (controllerActive)
                showDatabaseError("remove menu item", "The database is busy.");
            callback.accept(false);
        }
    }

    /**
     * Removes a menu item from the list view in the GUI.
     *
     * @param item The menu item to remove from the list view.
     * @author Pablo Almanza
     */
    public void removeMenuItemFromList(MenuItem item) {
        if (menuListView != null)
            menuListView.getItems().remove(item);
    }

    /*-------------------------------------------X Report-----------------------------------------*/

    /**
     * Updates the X Report Graph.
     *
     * @author Ashley Hoang
     */
    private void updateXReportGraph() {
        Task<List<XRecord>> task = new Task<>() {
            @Override
            protected List<XRecord> call() throws Exception {
                return new ManagerRepository().loadXReport();
            }
        };
        task.setOnSucceeded(event -> {
            if (controllerActive) {
                XYChart.Series<String, Number> xSeries = new XYChart.Series<>();
                for (XRecord record : task.getValue()) {
                    String label = record.getHour() + ":00\nSales: " + record.getQuantity()
                            + "\nRevenue: $" + record.getRevenue();
                    xSeries.getData().add(new XYChart.Data<>(label, record.getQuantity()));
                }
                xReportGraph.getData().clear();
                xReportGraph.getData().add(xSeries);
            }
        });
        task.setOnFailed(event -> {
            if (controllerActive) {
                Throwable exception = task.getException();
                exception.printStackTrace();
                showError("X Report Graph Update Failed", "The X report graph could not be updated.",
                        "Please try again.");
            }
        });
        if (!startTask(task)) {
            showError("X Report Update Failed", "The database is busy.", "Please try again.");
        }
    }

    /**
     * From the local time, update X Report Graph hourly
     *
     * @author Ashley Hoang
     */
    private void updateHourly() {
        LocalTime now = LocalTime.now();
        int currentMinute = now.getMinute();
        int currentSecond = now.getSecond();

        int secondsUntilNextHour = (60 - currentMinute) * 60 - currentSecond;

        hourlyUpdateTimeline = new Timeline(new KeyFrame(Duration.seconds(secondsUntilNextHour), event -> {
            Platform.runLater(() -> {
                updateXReportGraph();
                updateRecurring();
            });
        }));

        hourlyUpdateTimeline.setCycleCount(1);
        hourlyUpdateTimeline.play();
    }

    /**
     * Schedules recurring updates for X Report Graph
     *
     * @author Ashley Hoang
     */
    private void updateRecurring() {
        hourlyUpdateTimeline = new Timeline(new KeyFrame(Duration.hours(1), event -> {
            Platform.runLater(() -> updateXReportGraph());
        }));
        hourlyUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
        hourlyUpdateTimeline.play();
    }

    /*-------------------------------------------Window-----------------------------------------*/
    /**
     * Closes the current window.
     *
     * @param event The ActionEvent triggered when the user clicks the close button.
     * @author Ashley Hoang
     */
    @FXML
    public void closeWindow(ActionEvent event) {
        if (hourlyUpdateTimeline != null)
            hourlyUpdateTimeline.stop();
        controllerActive = false;
        Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
        stage.close();
    }

    /**
     * Changes the view to the cashier view when the corresponding button is
     * clicked.
     *
     * @author Ashley Hoang
     * @param event - the action event triggering the view change
     * @throws IOException if the FXML resource cannot be loaded
     */
    @FXML
    public void changeView(ActionEvent event) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("/frontend/resources/cashierGUI.fxml"));
        controllerActive = false;

        Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        stage.setTitle("Cashier View");
        stage.setScene(new Scene(root, 1200, 800));
        stage.show();
    }

    /**
     * Reloads the current application view to reflect any changes made to the menu
     * items.
     *
     * @author Ashley Hoang
     */
    @FXML
    public void reloadApplication() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/frontend/resources/managerGUI.fxml"));
            Parent root = loader.load();
            controllerActive = false;
            Stage stage = (Stage) menuListView.getScene().getWindow();
            Scene scene = new Scene(root, 1200, 800);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            showError("Reload Failed", "The manager view could not be refreshed.",
                    "Please try again or reopen the manager view.");
        }

    }

    /**
     * Starts a task in the database executor.
     *
     * @param task The task to start.
     * @return true if the task was started successfully, false otherwise.
     * @author Pablo Almanza
     */
    private boolean startTask(Task<?> task) {
        try {
            DATABASE_EXECUTOR.submit(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    /**
     * Shows a database error message in an alert dialog.
     *
     * @param operation The operation that failed (e.g., "update stock", "add menu
     *                  item").
     * @param details   Additional details about the error (e.g., "The stock value
     *                  changed. Current stock: 5").
     * @author Pablo Almanza
     */
    private void showDatabaseError(String operation, String details) {
        showError("Database Error", "Unable to " + operation, details);
    }

    /**
     * Shows a database error message in an alert dialog when the stock update fails
     * due to a stock value change.
     *
     * @param currentStock The current stock value in the database.
     * @author Pablo Almanza
     */
    public void showStockUpdateFailure(int currentStock) {
        showDatabaseError("update stock", "The stock value changed. Current stock: " + currentStock);
    }

    /**
     * Shows a database error message in an alert dialog when the inventory record
     * no longer exists.
     *
     * @author Pablo Almanza
     */
    public void showMissingInventoryError() {
        showDatabaseError("update stock", "The inventory record no longer exists.");
    }

    /**
     * Displays an error alert dialog with the specified title, header, and details.
     *
     * @param title   The title of the error dialog.
     * @param header  The header of the error dialog.
     * @param details Additional details about the error.
     * @author Pablo Almanza
     */
    private void showError(String title, String header, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(details);
        alert.showAndWait();
    }

    /**
     * Checks whether or not the Manager Controller is currently active
     *
     * @return if the Manager Controller is currently active
     * @author Pablo Almanza
     */
    public boolean isActive() {
        return controllerActive;
    }
}
