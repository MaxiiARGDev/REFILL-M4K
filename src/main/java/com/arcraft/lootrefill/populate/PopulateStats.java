package com.arcraft.lootrefill.populate;

import java.util.concurrent.atomic.AtomicInteger;

public class PopulateStats {

    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger populated = new AtomicInteger(0);
    private final AtomicInteger skippedNotEmpty = new AtomicInteger(0);
    private final AtomicInteger skippedNoLootTable = new AtomicInteger(0);
    private final AtomicInteger skippedPlayer = new AtomicInteger(0);
    private final AtomicInteger skippedBroken = new AtomicInteger(0);
    private final AtomicInteger skippedDisabled = new AtomicInteger(0);
    private final AtomicInteger skippedInvalid = new AtomicInteger(0);
    private final AtomicInteger skippedUnsupportedType = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);

    public void setTotal(int total) {
        this.total.set(total);
    }

    public void addResult(PopulateResult result) {
        processed.incrementAndGet();
        switch (result) {
            case POPULATED -> populated.incrementAndGet();
            case SKIPPED_NOT_EMPTY -> skippedNotEmpty.incrementAndGet();
            case SKIPPED_NO_LOOT_TABLE, SKIPPED_INVALID_LOOT_POOL -> skippedNoLootTable.incrementAndGet();
            case SKIPPED_PLAYER -> skippedPlayer.incrementAndGet();
            case SKIPPED_BROKEN -> skippedBroken.incrementAndGet();
            case SKIPPED_DISABLED -> skippedDisabled.incrementAndGet();
            case SKIPPED_INVALID -> skippedInvalid.incrementAndGet();
            case SKIPPED_UNSUPPORTED_TYPE -> skippedUnsupportedType.incrementAndGet();
            case SKIPPED_DOUBLE_CHEST_ALREADY_POPULATED -> {
                // Considerado procesado y parte del mismo cofre
            }
            case ERROR -> errors.incrementAndGet();
        }
    }

    public int getTotal() {
        return total.get();
    }

    public int getProcessed() {
        return processed.get();
    }

    public int getPopulated() {
        return populated.get();
    }

    public int getSkippedNotEmpty() {
        return skippedNotEmpty.get();
    }

    public int getSkippedNoLootTable() {
        return skippedNoLootTable.get();
    }

    public int getSkippedPlayer() {
        return skippedPlayer.get();
    }

    public int getSkippedBroken() {
        return skippedBroken.get();
    }

    public int getSkippedDisabled() {
        return skippedDisabled.get();
    }

    public int getSkippedInvalid() {
        return skippedInvalid.get();
    }

    public int getSkippedUnsupportedType() {
        return skippedUnsupportedType.get();
    }

    public int getErrors() {
        return errors.get();
    }

    public int getTotalSkipped() {
        return skippedNotEmpty.get() + skippedNoLootTable.get() + skippedPlayer.get()
                + skippedBroken.get() + skippedDisabled.get() + skippedInvalid.get() + skippedUnsupportedType.get();
    }
}
