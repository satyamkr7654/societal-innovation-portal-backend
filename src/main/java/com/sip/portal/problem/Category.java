package com.sip.portal.problem;

import java.util.Arrays;
import java.util.Optional;

public enum Category {
    WATER("Water", "WTR"), SANITATION("Sanitation", "SAN"), HEALTH("Health", "HLT"),
    AGRICULTURE("Agriculture", "AGR"), EDUCATION("Education", "EDU"), INFRASTRUCTURE("Infrastructure", "INF"),
    ENVIRONMENT("Environment", "ENV"), ENERGY("Energy", "NRG");

    private final String label;
    private final String code;

    Category(String label, String code) {
        this.label = label;
        this.code = code;
    }

    public String label() { return label; }
    public String code() { return code; }

    public static Optional<Category> fromLabel(String label) {
        return Arrays.stream(values()).filter(c -> c.label.equals(label)).findFirst();
    }
}
