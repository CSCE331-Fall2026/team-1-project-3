package frontend.controllers;

import backend.database.CashierRepository;
import backend.database.CashierRepository.CheckoutOutcome;
import backend.models.MenuRecord;
import frontend.models.CartLine;
import frontend.models.MealSize;

import java.math.BigDecimal;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.event.ActionEvent;

/**
 * Class controller for cashier view fxml
 *
 * @author Sharaf Mehmood
 */
public class CashierController {
    /**
     * Creates a cashier controller for use by the JavaFX FXML loader.
     */
    public CashierController() {
    }

    @FXML
    private Button bowl, plate, biggerPlate, changeView, checkout, closeButton;
    @FXML
    private VBox mainBox, sideBox, drinkBox, cartBox;
    private Label MAIN_LABEL = new Label("Mains"), SIDE_LABEL = new Label("Sides"), DRINK_LABEL = new Label("Drinks"),
            totalPriceLabel;
    private String SELECTED_STYLE = "-fx-border-color: #D61927; -fx-font-weight: bold";
    private static final ExecutorService DATABASE_EXECUTOR = new ThreadPoolExecutor( // TODO: Put this in the
                                                                                     // constructor with a short
                                                                                     // explanation for why this is
                                                                                     // needed.
            4, 4, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16),
            runnable -> {
                Thread thread = new Thread(runnable, "cashier-database-worker");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final List<CartLine> cartLines = new ArrayList<>();
    private MealSize selectedSize;
    private final List<MenuRecord> pendingMains = new ArrayList<>();
    private MenuRecord pendingSide;
    private int currentLocationId = 1;
    private int currentUserId = 3;
    private boolean checkoutInProgress;
    private boolean checkoutOutcomeUncertain;
    private boolean controllerActive = true;

    /**
     * Load menu items and initialize button functionality onto the screen
     *
     * @author Sharaf Mehmood
     */
    @FXML
    public void initialize() {
        runQuery();
        closeButton.setOnAction(event -> closeWindow());
        bowl.setOnAction(event -> selectSize(MealSize.BOWL));
        plate.setOnAction(event -> selectSize(MealSize.PLATE));
        biggerPlate.setOnAction(event -> selectSize(MealSize.BIGGER_PLATE));
        checkout.setOnAction(event -> confirmCheckout());
    }

    /**
     * Attempts to connect to the database and load the current menu items.
     *
     * @author Sharaf Mehmood
     * @author Pablo Almanza
     */ // TODO: Please refactor this, the function is very unclear on what it does and
        // very ugly.
    private void runQuery() {
        checkout.setDisable(true);
        Task<List<MenuRecord>> task = new Task<>() {
            @Override
            protected List<MenuRecord> call() throws Exception {
                return new CashierRepository().loadMenu();
            }
        };

        task.setOnSucceeded(event -> loadBoxes(task));
        task.setOnFailed(event -> loadBoxesFail());

        if (!startTask(task)) {
            checkout.setDisable(false);
            showError("Menu Load Failed", "The database is busy.", "Please try again.");
        }
    }

    /**
     * Loads all of the menu items onto the screen, if task succeeds in connecting
     * to database
     *
     * @param task represents the task of loading elements from the database
     * @author Pablo Almanza, Sharaf Mehmood
     */
    private void loadBoxes(Task<List<MenuRecord>> task) {
        if (!controllerActive)
            return;
        VBox[] areas = { mainBox, sideBox, drinkBox };
        for (VBox area : areas)
            area.getChildren().clear();
        mainBox.getChildren().add(MAIN_LABEL);
        sideBox.getChildren().add(SIDE_LABEL);
        drinkBox.getChildren().add(DRINK_LABEL);
        if (totalPriceLabel == null) {
            totalPriceLabel = new Label("Total Price: $0.0");
            cartBox.getChildren().add(totalPriceLabel);
            totalPriceLabel.getStyleClass().add("font");
        }
        for (MenuRecord record : task.getValue()) {
            VBox area = areas[record.getCategoryID() - 1];
            Button button = new Button(record.getName());
            button.setPrefSize(area.getPrefWidth(), 30);
            button.setOnAction(action -> onMenuItemPressed(record, button));
            area.getChildren().add(button);
        }
        checkout.setDisable(false);
    }

    /**
     * Displays an error message when the boxes fail to load.
     *
     * @author Pablo Almanza
     */

    private void loadBoxesFail() {
        if (!controllerActive)
            return;

        checkout.setDisable(false);
        showError("Menu Load Failed", "The menu could not be loaded.",
                "Please check the database connection and try again.");
    }

    /**
     * Clears any highlights that may have occured when selecting items.
     *
     * @author Sharaf Mehmood
     */

    private void clearHighlights() {
        VBox[] areas = { mainBox, sideBox };
        for (VBox box : areas) {
            for (javafx.scene.Node node : box.getChildren())
                node.setStyle("");
        }
    }

    /**
     * Handles size selection (bowl, plate, bigger plate)
     *
     * @author Sharaf Mehmood
     */

    private void selectSize(MealSize size) {
        if (selectedSize == size)
            selectedSize = null;
        else
            selectedSize = size;
        pendingMains.clear();
        pendingSide = null;
        clearHighlights();

        if (selectedSize == MealSize.BOWL) {
            bowl.setStyle(SELECTED_STYLE);
        } else
            bowl.setStyle("");

        if (selectedSize == MealSize.PLATE) {
            plate.setStyle(SELECTED_STYLE);
        } else
            plate.setStyle("");

        if (selectedSize == MealSize.BIGGER_PLATE) {
            biggerPlate.setStyle(SELECTED_STYLE);
        } else
            biggerPlate.setStyle("");
    }

    /**
     * Handles selecting menu items, checking whether a meal is being formed or if
     * orders are a la carte.
     *
     * @param record MenuRecord tracking all components of the menu item clicked
     * @param button the corresponding button for the menu item clicked
     * @author Sharaf Mehmood
     */

    private void onMenuItemPressed(MenuRecord record, Button button) {
        if (selectedSize == null || record.getCategoryID() == 3) {
            addToCart(CartLine.single(record));
            return;
        }
        if (record.getCategoryID() == 1 && pendingMains.size() < selectedSize.getMains()) {
            pendingMains.add(record);
            button.setStyle(SELECTED_STYLE);
        }
        if (record.getCategoryID() == 2) {
            for (javafx.scene.Node node : sideBox.getChildren()) node.setStyle("");
            pendingSide = record;
            button.setStyle(SELECTED_STYLE);
        }
        if (pendingMains.size() == selectedSize.getMains() && pendingSide != null) {
            addToCart(CartLine.meal(selectedSize, pendingMains, pendingSide));
            pendingMains.clear();
            pendingSide = null;
            clearHighlights();
            selectSize(selectedSize);
        }
    }

    /**
     * Updates the quantity of an item in the cart.
     * Removes the item if its quantity reaches zero.
     *
     * @param line   represents the item in the cart being changed
     * @param change the amount the item's quantity is being changed by
     * @author Sharaf Mehmood
     */
    private void changeQuantity(CartLine line, int change) {
        int newQuantity = line.getQuantity() + change;
        if (newQuantity < 1) {
            cartBox.getChildren().remove(line.getRow().getParent());
            cartLines.remove(line);
        } else {
            line.setQuantity(newQuantity);
            line.getRow().setText(line.describe());
        }
        refreshTotal();
    }

    /**
     * Adds an item to cart (either as a meal or individually, based of if a size
     * was selected)
     *
     * @param line the cart line being added to the cart
     * @author Sharaf Mehmood
     */
    private void addToCart(CartLine line) {
        for (CartLine existing : cartLines) {
            if (line.getKey() != null && line.getKey().equals(existing.getKey())) {
                existing.setQuantity(existing.getQuantity() + 1);
                existing.getRow().setText(existing.describe());
                refreshTotal();
                return;
            }
        }
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(true);
        checkBox.setWrapText(true);
        checkBox.setOnAction(e -> onCheckboxSelected(line, checkBox));
        checkBox.setText(line.describe());
        Button minus = new Button("-");
        Button plus = new Button("+");
        minus.setOnAction(e -> changeQuantity(line, -1));
        plus.setOnAction(e -> changeQuantity(line, 1));
        HBox.setHgrow(checkBox, Priority.ALWAYS);
        checkBox.setMaxWidth(Double.MAX_VALUE);
        line.setRow(checkBox);
        cartLines.add(line);
        cartBox.getChildren().add(new HBox(5, checkBox, minus, plus));
        refreshTotal();
    }

    /**
     * Refreshes total price label
     *
     * @author Sharaf Mehmood
     */
    private void refreshTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (CartLine line : cartLines)
            total = total.add(line.lineTotal());
        totalPriceLabel.setText("Total Price: $%.2f".formatted(total));
    }

    /**
     * Removes the corresponding item from cart when selected (unchecked)
     *
     * @param line   represents the item in the cart
     * @param parent represents the checkbox being unchecked
     * @author Sharaf Mehmood
     */
    private void onCheckboxSelected(CartLine line, CheckBox parent) {
        if (!parent.isSelected()) {
            cartBox.getChildren().remove(parent.getParent());
            cartLines.remove(line);
            refreshTotal();
        }
    }

    /**
     * Closes the current window
     *
     * @author Sharaf Mehmood
     */
    private void closeWindow() {
        controllerActive = false;
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Represents what happens on a success with checkout
     *
     * @param task The task to execute.
     * @author Pablo Almanza
     */
    private void checkoutSuccess(Task<CheckoutOutcome> task) {
        CheckoutOutcome outcome = task.getValue();
        if (outcome == CheckoutOutcome.SUCCESS && controllerActive) {
            for (CartLine line : cartLines)
                cartBox.getChildren().remove(line.getRow().getParent());
            cartLines.clear();
            refreshTotal();
            showInformation("Order Complete", "Order recorded successfully.");
        } else if (outcome == CheckoutOutcome.UNCERTAIN && controllerActive) {
            checkoutOutcomeUncertain = true;
            showError("Checkout Status Unknown", "The order status could not be confirmed.",
                    "Do not retry. Verify the receipt with a manager before submitting another order.");
        } else if (outcome == CheckoutOutcome.FAILURE && controllerActive) {
            showError("Checkout Failed", "The order was not recorded.",
                    "Please verify inventory and try again.");
        }
        if (outcome != CheckoutOutcome.UNCERTAIN)
            finishCheckout();
    }

    /**
     * Represents what happens on a failure to checkout
     *
     * @author Pablo Almanza
     */
    private void checkoutFail() {
        if (controllerActive)
            showError("Checkout Failed", "The order was not recorded.",
                    "Please verify inventory and try again.");
        finishCheckout();
    }

    /**
     * Converts the current cart lines into item quantities, prices, and line totals
     * used during checkout.
     *
     * @param quantities maps item IDs to total quantities in the cart
     * @param listPrices maps item IDs to their current menu prices
     * @param totals     maps item IDs to the total amount charged for those items
     * @author Colby Ideker
     * @author Sharaf Mehmood
     */

    private void flattenCart(TreeMap<Integer, Integer> quantities, TreeMap<Integer, BigDecimal> listPrices,
            TreeMap<Integer, BigDecimal> totals) {
        for (CartLine line : cartLines) {
            BigDecimal quantity = BigDecimal.valueOf(line.getQuantity());
            for (int i = 0; i < line.getComponents().size(); i++) {
                MenuRecord component = line.getComponents().get(i);
                //old method of discount either added/subtracted the discount which could result
                //in errors, this distributes it on protein so it is correct + cant error
                int proteins = line.getComponents().size()-1;
                long cents = line.discount().movePointRight(2).longValueExact();
                BigDecimal unitAmount = component.getPrice();
                if (i < proteins) {
                    long share;
                    if (i == proteins - 1){
                        share = cents - (cents/proteins) * (proteins-1);
                    }
                    else{
                        share = cents / proteins;
                    }
                    unitAmount = unitAmount.subtract(BigDecimal.valueOf(share, 2));
                }
                quantities.merge(component.getItemID(), line.getQuantity(), Integer::sum);
                listPrices.put(component.getItemID(), component.getPrice());
                totals.merge(component.getItemID(), unitAmount.multiply(quantity), BigDecimal::add);
            }
        }
    }

    /**
     * Starts the checkout process by preparing the cart data, disabling checkout
     * controls, and running the database transaction asynchronously.
     *
     * @author Sharaf Mehmood
     * @author Colby Ideker
     * @author Pablo Almanza
     */

    private void confirmCheckout() {
        if (cartLines.isEmpty() || checkoutInProgress || checkoutOutcomeUncertain)
            return;
        checkoutInProgress = true;
        checkout.setDisable(true);
        mainBox.setDisable(true);
        sideBox.setDisable(true);
        drinkBox.setDisable(true);
        cartBox.setDisable(true);
        TreeMap<Integer, Integer> checkoutCart = new TreeMap<>();
        TreeMap<Integer, BigDecimal> checkoutPrices = new TreeMap<>();
        TreeMap<Integer, BigDecimal> checkoutTotal = new TreeMap<>();
        flattenCart(checkoutCart, checkoutPrices, checkoutTotal);
        Task<CheckoutOutcome> task = new Task<>() {
            @Override
            protected CheckoutOutcome call() {
                return new CashierRepository().checkout(currentLocationId, currentUserId,
                        checkoutCart, checkoutPrices, checkoutTotal);
            }
        };

        task.setOnSucceeded(event -> checkoutSuccess(task));
        task.setOnFailed(event -> checkoutFail());
        task.setOnCancelled(event -> finishCheckout());

        if (!startTask(task)) {
            finishCheckout();
            showError("Checkout Failed", "The database is busy.", "Please try again.");
        }
    }

    /**
     * Submits a database task to the shared executor for asynchronous execution.
     *
     * @param task the JavaFX task to execute
     * @return true if the task was submitted successfully, false if the executor
     *         rejected it
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
     * Re-enables the cashier controls after a checkout attempt finishes.
     *
     * @author Pablo Almanza
     */

    private void finishCheckout() {
        if (!controllerActive)
            return;
        checkoutInProgress = false;
        checkout.setDisable(false);
        mainBox.setDisable(false);
        sideBox.setDisable(false);
        drinkBox.setDisable(false);
        cartBox.setDisable(false);
    }

    /**
     * Displays an informational alert with the provided title and message.
     *
     * @param title   the alert window title
     * @param details the message displayed in the alert
     * @author Pablo Almanza
     */

    private void showInformation(String title, String details) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(details);
        alert.showAndWait();
    }

    /**
     * Displays an error alert with the provided title, header, and message.
     *
     * @param title   the alert window title
     * @param header  the alert header text
     * @param details the message displayed in the alert
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
     * Changes view from cashier to manager
     *
     * @param event event of clicking change view button
     * @throws Exception if the manager view cannot be loaded
     * @author Sharaf Mehmood
     */
    @FXML
    public void changeView(ActionEvent event) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/frontend/resources/managerGUI.fxml"));
        controllerActive = false;
        Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        stage.setTitle("Manager View");
        stage.setScene(new Scene(root, 1200, 800));
        stage.show();
    }
}
