package backend.database;

import static org.assertj.core.api.Assertions.assertThat;

import backend.database.CheckoutPricing.Line;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutPricingTest {
    private static Line main(long price, int qty, long total) {
        return new Line(CheckoutPricing.MAIN, price, qty, total);
    }

    private static Line side(long price, int qty, long total) {
        return new Line(CheckoutPricing.SIDE, price, qty, total);
    }

    @Test
    void fullPriceOrderIsValid() {
        assertThat(CheckoutPricing.valid(List.of(main(830, 2, 1660), side(440, 1, 440), new Line(3, 210, 1, 210))))
                .isTrue();
    }

    @Test
    void discountWithoutAnySideIsRejected() {
        assertThat(CheckoutPricing.valid(List.of(main(830, 1, 829)))).isFalse();
    }

    @Test
    void sidesAndDrinksMustBeFullPrice() {
        assertThat(CheckoutPricing.valid(List.of(main(830, 1, 830), side(440, 1, 439)))).isFalse();
        assertThat(CheckoutPricing.valid(List.of(new Line(3, 210, 1, 0)))).isFalse();
    }

    @Test
    void chargingMoreThanListPriceIsRejected() {
        assertThat(CheckoutPricing.valid(List.of(main(830, 1, 831)))).isFalse();
    }

    @Test
    void biggerPlateDiscountCeilingIsExact() {
        // 3 mains + 1 side: list 3320, ceiling = 3320 * 0.61598 + 0.5 = 2045.55 cents of discount.
        Line sideLine = side(440, 1, 440);
        assertThat(CheckoutPricing.valid(List.of(main(830, 1, 0), main(950, 1, 0), main(1100, 1, 835), sideLine)))
                .isTrue();
        assertThat(CheckoutPricing.valid(List.of(main(830, 1, 0), main(950, 1, 0), main(1100, 1, 834), sideLine)))
                .isFalse();
    }

    @Test
    void discountOnlyCountsThreeMainsPerSide() {
        // 1 side + 10 mains: only the 3 priciest mains can be in the meal, so a 61% cut on all 10 is rejected.
        assertThat(CheckoutPricing.valid(List.of(main(830, 10, 3188), side(440, 1, 440)))).isFalse();
    }

    @Test
    void overflowIsRejectedNotWrapped() {
        assertThat(CheckoutPricing.valid(List.of(main(Long.MAX_VALUE / 2, 3, 0)))).isFalse();
        assertThat(CheckoutPricing.valid(List.of(main(9_999_999_999L, Integer.MAX_VALUE, 9_999_999_999L),
                side(9_999_999_999L, 1, 9_999_999_999L)))).isFalse();
    }

    @Test
    void zeroQuantityIsRejected() {
        assertThat(CheckoutPricing.valid(List.of(main(830, 0, 0)))).isFalse();
    }
}
