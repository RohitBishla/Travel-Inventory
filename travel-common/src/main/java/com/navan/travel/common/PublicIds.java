package com.navan.travel.common;

import java.util.OptionalLong;

public final class PublicIds {

    private static final String TRANSACTION_PREFIX = "txn_";
    private static final String INVOICE_PREFIX = "inv_";

    private PublicIds() {
    }

    public static String transaction(long id) {
        return TRANSACTION_PREFIX + id;
    }

    public static String invoice(long id) {
        return INVOICE_PREFIX + id;
    }

    public static OptionalLong parseTransaction(String publicId) {
        return parse(TRANSACTION_PREFIX, publicId);
    }

    public static OptionalLong parseInvoice(String publicId) {
        return parse(INVOICE_PREFIX, publicId);
    }

    private static OptionalLong parse(String prefix, String publicId) {
        if (publicId == null || !publicId.startsWith(prefix)) {
            return OptionalLong.empty();
        }
        try {
            long id = Long.parseLong(publicId.substring(prefix.length()));
            return id > 0 ? OptionalLong.of(id) : OptionalLong.empty();
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
