package com.example.priceservice.util;

import java.security.SecureRandom;

/**
 * Utility for generating random test data using {@link SecureRandom}.
 * <p>
 * If the {@code test.deterministic} system property is set to {@code true},
 * the generator will use a fixed seed so that values are repeatable.
 */
public final class TestDataFactory {
    private static final String DETERMINISTIC_PROPERTY = "test.deterministic";
    private static final SecureRandom RANDOM;
    private static final char[] ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    static {
        if (Boolean.getBoolean(DETERMINISTIC_PROPERTY)) {
            RANDOM = new SecureRandom(new byte[] {0,1,2,3,4,5,6,7});
            RANDOM.setSeed(0L);
        } else {
            RANDOM = new SecureRandom();
        }
    }

    private TestDataFactory() {
    }

    /**
     * Generates a random alphanumeric instrument id.
     *
     * @return unique id value
     */
    public static String randomInstrumentId() {
        return randomInstrumentId(6);
    }

    /**
     * Generates a random alphanumeric instrument id of given length.
     */
    public static String randomInstrumentId(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM[RANDOM.nextInt(ALPHANUM.length)]);
        }
        return sb.toString();
    }
}
