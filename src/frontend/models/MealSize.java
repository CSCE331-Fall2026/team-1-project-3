package frontend.models;

import java.math.BigDecimal;

/**
 * Represents meal sizes and their discounts
 *
 * @author Sharaf Mehmood
 */
public enum MealSize {
    /** A bowl containing one main. */
    BOWL("Bowl", 1, new BigDecimal("0.39068")),
    /** A plate containing two mains. */
    PLATE("Plate", 2, new BigDecimal("0.54839")),
    /** A bigger plate containing three mains. */
    BIGGER_PLATE("Bigger Plate", 3, new BigDecimal("0.61598"));
    private final String label;
    private final int mains;
    private final BigDecimal discount;

    /**
     * Creates a meal size with its label, number of mains, and discount.
     *
     * @param label    label for the size
     * @param mains    number of mains included in the size
     * @param discount discount multiplier for the meal size
     * @author Sharaf Mehmood
     */
    MealSize(String label, int mains, BigDecimal discount) {
        this.label = label;
        this.mains = mains;
        this.discount = discount;
    }

    /**
     * Returns the display label for this meal size.
     *
     * @return the meal size label
     * @author Sharaf Mehmood
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the number of mains included in this meal size.
     *
     * @return the number of mains
     * @author Sharaf Mehmood
     */
    public int getMains() {
        return mains;
    }

    /**
     * Returns the discount multiplier used for this meal size.
     *
     * @return the discount multiplier
     * @author Sharaf Mehmood
     */
    public BigDecimal getDiscount() {
        return discount;
    }
}
