package com.izhar.splitledger.logic;

import com.izhar.splitledger.error.ApiException;

import java.math.BigDecimal;

/** Money is handled as integer minor units (1.00 = 100) so no floating-point error can ever creep in. */
public final class Money {

    private Money() {
    }

    public static long toMinor(BigDecimal value) {
        if (value.stripTrailingZeros().scale() > 2) {
            throw ApiException.unprocessable("Amounts can have at most 2 decimal places: " + value.toPlainString());
        }
        return value.movePointRight(2).longValueExact();
    }

    public static BigDecimal fromMinor(long minor) {
        return BigDecimal.valueOf(minor, 2);
    }
}
