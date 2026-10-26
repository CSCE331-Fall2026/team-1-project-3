package frontend.components;

import frontend.controllers.ManagerController;
import frontend.models.MenuItem;

import javafx.scene.control.Label;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Pane;

/**
 * This class represents a custom ListCell for displaying MenuItem objects in a ListView. 
 * @author Ashley Hoang
 */
public class MenuListCell extends ListCell<MenuItem> {
    private final HBox content;
    private final Label infoLabel;
    private final Button changePriceBtn;
    private final Button removeBtn;

    private final ManagerController controller;

    /**
     * Constructs the cell (for list view) of a Menu Item with buttons for changing the price and removing the item.
     * @param controller The ManagerController which handles the logic for updating and removing menu items.
     * @author Ashley Hoang
     */
    public MenuListCell(ManagerController controller) {
        this.controller = controller;
        infoLabel = new Label();
        Pane spacer = new Pane();
        changePriceBtn = new Button("Change Price");
        removeBtn = new Button("Remove Item");

        changePriceBtn.setOnAction(e -> onChangePriceButtonPressed());
        removeBtn.setOnAction(e -> onRemoveButtonPressed());

        HBox.setHgrow(changePriceBtn, Priority.NEVER);
        HBox.setHgrow(removeBtn, Priority.NEVER);
        HBox.setHgrow(infoLabel, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        content = new HBox(10, infoLabel, spacer, changePriceBtn, removeBtn);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(5));
    }

    /**
     * Handles the event when the "Change Price" button is pressed.
     * @author Ashley Hoang
     * @author Pablo Almanza
     */
    private void onChangePriceButtonPressed() {
        MenuItem item = getItem();
        if (item == null) return;
        java.math.BigDecimal newPrice = controller.inputNewPrice();
        if (newPrice == null) return;
        setControlsDisabled(true);
        controller.updatePriceInDatabase(item.getItemID(), newPrice, confirmedPrice -> {
            if (controller.isActive() && getItem() == item && confirmedPrice != null) {
                item.setPrice(confirmedPrice);
                updateDisplay();
            }
            if (controller.isActive()) setControlsDisabled(false);
        });
    }

    /**
     * Handles the event when the "Remove Item" button is pressed.
     * @author Ashley Hoang
     * @author Pablo Almanza
     */
    private void onRemoveButtonPressed() {
        MenuItem item = getItem();
        if (item == null) return;
        setControlsDisabled(true);
        controller.removeMenuItemFromDatabase(item, removed -> {
            if (controller.isActive()) {
                if (getItem() == item && removed) controller.removeMenuItemFromList(item);
                setControlsDisabled(false);
            }
        });
    }

    /**
     * Sets the disabled state of the buttons in the ListCell.
     * @param disabled Whether the controls should be disabled.
     * @author Pablo Almanza
     */
    private void setControlsDisabled(boolean disabled) {
        changePriceBtn.setDisable(disabled);
        removeBtn.setDisable(disabled);
    }

    /**
     * Updates the display of the ListCell based on the current MenuItem.
     * @author Ashley Hoang
     */
    private void updateDisplay() {
        MenuItem item = getItem();
        if (item != null) {
            infoLabel.setText(String.format(
                    "#%d %s:\n- Price: $%.2f",
                    item.getItemID(), item.getItemName(), item.getItemPrice()));
        }
    }

    /**
     * Overrides the updateItem method to update the ListCell's content when the
     * item changes.
     *
     * @param item  The new MenuItem to display.
     * @param empty Whether the cell is empty.
     * @author Ashley Hoang
     */
    @Override
    protected void updateItem(MenuItem item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
        } else {
            updateDisplay();
            setGraphic(content);
        }
    }
}
