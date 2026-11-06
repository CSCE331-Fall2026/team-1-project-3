package backend.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The API sends money as integer cents; the repositories use BigDecimal dollars. */
final class Money {
    /** $99,999,999.99, the database price limit. */
    static final long MAX_CENTS = 9_999_999_999L;

    private Money() {
    }

    static long toCents(BigDecimal dollars) {
        return dollars == null ? 0 : dollars.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2); // scale 2, as CashierRepository requires
    }

    static boolean valid(Long cents) {
        return cents != null && cents >= 0 && cents <= MAX_CENTS;
    }
}
