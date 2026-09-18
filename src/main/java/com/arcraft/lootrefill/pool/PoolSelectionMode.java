package com.arcraft.lootrefill.pool;

public enum PoolSelectionMode {
    SINGLE_RANDOM,
    MIXED_RANDOM;

    public static PoolSelectionMode fromString(String modeStr) {
        if (modeStr == null) return SINGLE_RANDOM;
        try {
            return PoolSelectionMode.valueOf(modeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SINGLE_RANDOM;
        }
    }
}
