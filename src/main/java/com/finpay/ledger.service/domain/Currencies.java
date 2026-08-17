package com.finpay.ledger.service.domain;

import java.util.regex.Pattern;

/** ISO-4217 currency validation shared by the ledger value objects. */
final class Currencies {

    private static final Pattern ISO_4217 = Pattern.compile("^[A-Z]{3}$");

    private Currencies() {
    }

    static void requireIso4217(String code) {
        if (code == null || !ISO_4217.matcher(code).matches()) {
            throw new IllegalArgumentException("Currency must be a 3-letter ISO-4217 code, got: " + code);
        }
    }
}