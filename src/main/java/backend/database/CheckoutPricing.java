package backend.database;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Checks client line totals against database prices. The client sends the flattened cart (one line per item), so
 * which items formed which meal is lost; this enforces the bounds every real cart satisfies (see client/src/lib.js
 * mealLine and flattenCart):
 * <ul>
 * <li>no line is charged more than quantity x price, or less than zero;</li>
 * <li>only mains are discounted (the meal side and singles always pay full price);</li>
 * <li>each meal has one side and at most 3 mains, at a discount of at most BIGGER_PLATE's 61.598%.</li>
 * </ul>
 * shortcut: the meal bound is a ceiling, not an exact price, so a client can still pick a bigger meal discount than
 * it ordered. Exact pricing needs the cart's meal structure in the request (a frontend change).
 */
final class CheckoutPricing {
    static final int MAIN = 1;
    static final int SIDE = 2;
    /** BIGGER_PLATE discount in client/src/lib.js, scaled by 1e5. */
    static final long MAX_MEAL_DISCOUNT = 61_598;
    private static final long SCALE = 100_000;

    record Line(int categoryId, long priceCents, int quantity, long totalCents) {
    }

    private CheckoutPricing() {
    }

    static boolean valid(Collection<Line> lines) {
        try {
            long sides = 0;
            long sideList = 0;
            long discount = 0;
            List<Line> mains = new ArrayList<>();
            for (Line line : lines) {
                long list = Math.multiplyExact(line.priceCents(), (long) line.quantity());
                if (line.quantity() < 1 || line.totalCents() < 0 || line.totalCents() > list)
                    return false;
                if (line.categoryId() == MAIN) {
                    mains.add(line);
                    discount = Math.addExact(discount, list - line.totalCents());
                } else if (line.totalCents() != list) {
                    return false;
                } else if (line.categoryId() == SIDE) {
                    sides += line.quantity();
                    sideList = Math.addExact(sideList, list);
                }
            }
            if (discount == 0)
                return true;

            // At most `sides` meals, so at most 3 * sides discounted mains; assume the priciest ones.
            long slots = Math.multiplyExact(3, sides);
            long mealList = sideList;
            mains.sort(Comparator.comparingLong(Line::priceCents).reversed());
            for (Line main : mains) {
                long taken = Math.min(slots, main.quantity());
                mealList = Math.addExact(mealList, Math.multiplyExact(taken, main.priceCents()));
                slots -= taken;
            }
            // mealLine rounds each meal HALF_UP, which can add up to half a cent of discount per meal.
            long ceiling = Math.addExact(Math.multiplyExact(mealList, MAX_MEAL_DISCOUNT),
                    Math.multiplyExact(sides, SCALE / 2));
            return Math.multiplyExact(discount, SCALE) <= ceiling;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }
}
