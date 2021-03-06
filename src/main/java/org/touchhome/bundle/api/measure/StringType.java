package org.touchhome.bundle.api.measure;

import lombok.Getter;

public class StringType implements State {
    public static StringType EMPTY = new StringType("");

    @Getter
    private final String value;

    public StringType(String value) {
        this.value = value != null ? value : "";
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public float floatValue() {
        throw new IllegalStateException("Unable to fetch float value from string");
    }

    @Override
    public int intValue() {
        throw new IllegalStateException("Unable to fetch int value from string");
    }

    @Override
    public boolean boolValue() {
        throw new IllegalStateException("Unable to fetch boolean value from String type");
    }
}
