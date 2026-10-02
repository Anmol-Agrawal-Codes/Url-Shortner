package com.example.urlshortener.url;

public final class Base62 {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private Base62() {
    }

    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must not be negative");
        }
        StringBuilder code = new StringBuilder();
        do {
            code.append(ALPHABET.charAt((int) (id % 62)));
            id /= 62;
        } while (id > 0);
        return code.reverse().toString();
    }
}
