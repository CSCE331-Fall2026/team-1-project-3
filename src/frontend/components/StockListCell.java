package frontend.components;

import frontend.controllers.ManagerController;
import frontend.models.StockItem;

import javafx.scene.control.Label;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import java.util.Comparator;
import javafx.scene.layout.Pane;
/**
 * This class represents a custom ListCell for displaying StockItem objects in a ListView. 
 * It provides buttons to increase or decrease the stock of the item and updates the display accordingly.
 * @author Ashley Hoang
 */
public class StockListCell extends ListCell<StockItem> {
    private final HBox content;
    private final Label infoLabel;
    private final Button changeStockBtn;

    private final ManagerController controller;

    /**
     * Constructs the cell (for list view) of a Stock Item with buttons for adding and removing stock.
     * @param controller The ManagerController which handles the logic for updating stock.
     * @author Ashley Hoang
     */
    public StockListCell(ManagerController controller) {
        this.controller = controller;
        infoLabel = new Label();
        Pane spacer = new Pane();
        changeStockBtn = new Button("Change Stock");

        changeStockBtn.setOnAction(_ -> onChangeButtonPressed());

        HBox.setHgrow(changeStockBtn, Priority.NEVER);
        HBox.setHgrow(infoLabel, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        content = new HBox(10, infoLabel, spacer, changeStockBtn);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(5));
    }

    /**
     * Handles the event when the "Change Stock" button is pressed.
     * @author Samuel Just
     * @author Ashley Hoang
     */
    private void onChangeButtonPressed() {
        StockItem item = getItem();
        if (item == null) return;
        int stockDelta = controller.inputNewStock();
        if (stockDelta == 0) return;
        updateStock(item, stockDelta);
    }

    /**
     * Updates the stock level of a StockItem.
     * @param item The StockItem to update.
     * @param delta The change in stock level.
     * @author Pablo Almanza
     */
    private void updateStock(StockItem item, int delta) {
        changeStockBtn.setDisable(true);
        controller.updateStockInDatabase(item.getInventoryID(), delta, result -> {
            if (controller.isActive() && getItem() == item) {
                if (result.getStock() != null) {
                    item.setStock(result.getStock());
                    getListView().getItems().sort(
                        Comparator.comparingInt(StockItem::getStock).thenComparingInt(StockItem::getInventoryID)
                    );
                    updateDisplay();            
                    if (!result.isUpdated()) controller.showStockUpdateFailure(result.getStock());
                } else if (result.isMissing()) {
                    getListView().getItems().remove(item);
                    controller.showMissingInventoryError();
                }
            }
            if (controller.isActive()) {
                changeStockBtn.setDisable(false);
            }
        });
    }

    /**
     * Updates the display of the ListCell based on the current StockItem.
     * @author Ashley Hoang
     */
    private void updateDisplay() {
        StockItem item = getItem();
        if (item != null) {
            //color the background based on warning color
            Color color = item.getColor();
            BackgroundFill fill = new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY);
            Background bg = new Background(fill);
            content.setBackground(bg);

            //make the text label
            infoLabel.setText(String.format(
                "#%d %s:\n- Stock: %d\n- Minimum Stock: %d\n- Next Shipment: %s\n- Shelf Life: %s",
                item.getInventoryID(), item.getName(), item.getStock(),
                item.getMinStock(), item.getNextShipment(), item.getShelfLife()
            ));
            infoLabel.setTextFill(Color.rgb(0,0,0));
        }
    }

    /**
     * Overrides the updateItem method to update the ListCell's content when the item changes.
     * @param item The new StockItem to display.
     * @param empty Whether the cell is empty.
     * @author Ashley Hoang
     */
    @Override
    protected void updateItem(StockItem item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
        } else {
            updateDisplay();
            setGraphic(content);
        }
    }
}
