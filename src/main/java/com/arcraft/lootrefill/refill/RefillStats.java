package com.arcraft.lootrefill.refill;

import java.util.concurrent.atomic.LongAdder;

public class RefillStats {

    private final LongAdder successfulRefills = new LongAdder();
    private final LongAdder skippedNotEmpty = new LongAdder();
    private final LongAdder skippedPlayerNearby = new LongAdder();
    private final LongAdder skippedChunkNotLoaded = new LongAdder();
    private final LongAdder skippedInvalidBlock = new LongAdder();
    private final LongAdder skippedTypeMismatch = new LongAdder();
    private final LongAdder skippedDisabled = new LongAdder();
    private final LongAdder skippedNoLootTable = new LongAdder();
    private final LongAdder skippedBurning = new LongAdder();
    private final LongAdder skippedDoubleChestPair = new LongAdder();
    private final LongAdder errors = new LongAdder();

    private final LongAdder chunksLoadedByRefill = new LongAdder();
    private final LongAdder chunksUnloadedByRefill = new LongAdder();

    private final LongAdder totalProcessingTimeNanos = new LongAdder();
    private final LongAdder totalProcessedCount = new LongAdder();

    private volatile long lastCycleTimestamp = 0;
    private volatile int lastCycleProcessedCount = 0;
    private volatile int lastCycleRefilledCount = 0;
    private volatile long lastCycleDurationMs = 0;

    public void recordResult(RefillResult result) {
        totalProcessedCount.increment();
        switch (result) {
            case SUCCESS -> successfulRefills.increment();
            case SKIPPED_NOT_EMPTY -> skippedNotEmpty.increment();
            case SKIPPED_PLAYER_NEARBY -> skippedPlayerNearby.increment();
            case SKIPPED_CHUNK_NOT_LOADED -> skippedChunkNotLoaded.increment();
            case SKIPPED_INVALID_BLOCK -> skippedInvalidBlock.increment();
            case SKIPPED_TYPE_MISMATCH -> skippedTypeMismatch.increment();
            case SKIPPED_DISABLED -> skippedDisabled.increment();
            case SKIPPED_NO_LOOT_TABLE, SKIPPED_INVALID_LOOT_POOL -> skippedNoLootTable.increment();
            case SKIPPED_BURNING -> skippedBurning.increment();
            case SKIPPED_DOUBLE_CHEST_PAIR -> skippedDoubleChestPair.increment();
            case ERROR -> errors.increment();
        }
    }

    public void recordChunkLoaded() {
        chunksLoadedByRefill.increment();
    }

    public void recordChunkUnloaded() {
        chunksUnloadedByRefill.increment();
    }

    public void recordCycle(int processed, int refilled, long durationNanos) {
        this.lastCycleTimestamp = System.currentTimeMillis();
        this.lastCycleProcessedCount = processed;
        this.lastCycleRefilledCount = refilled;
        this.lastCycleDurationMs = durationNanos / 1_000_000L;
        this.totalProcessingTimeNanos.add(durationNanos);
    }

    public long getSuccessfulRefills() {
        return successfulRefills.sum();
    }

    public long getSkippedNotEmpty() {
        return skippedNotEmpty.sum();
    }

    public long getSkippedPlayerNearby() {
        return skippedPlayerNearby.sum();
    }

    public long getSkippedChunkNotLoaded() {
        return skippedChunkNotLoaded.sum();
    }

    public long getSkippedInvalidBlock() {
        return skippedInvalidBlock.sum();
    }

    public long getSkippedTypeMismatch() {
        return skippedTypeMismatch.sum();
    }

    public long getSkippedDisabled() {
        return skippedDisabled.sum();
    }

    public long getSkippedNoLootTable() {
        return skippedNoLootTable.sum();
    }

    public long getSkippedBurning() {
        return skippedBurning.sum();
    }

    public long getSkippedDoubleChestPair() {
        return skippedDoubleChestPair.sum();
    }

    public long getTotalSkipped() {
        return getSkippedNotEmpty() + getSkippedPlayerNearby() + getSkippedChunkNotLoaded()
                + getSkippedInvalidBlock() + getSkippedTypeMismatch() + getSkippedDisabled()
                + getSkippedNoLootTable() + getSkippedBurning() + getSkippedDoubleChestPair();
    }

    public long getErrors() {
        return errors.sum();
    }

    public long getChunksLoadedByRefill() {
        return chunksLoadedByRefill.sum();
    }

    public long getChunksUnloadedByRefill() {
        return chunksUnloadedByRefill.sum();
    }

    public long getTotalProcessed() {
        return totalProcessedCount.sum();
    }

    public double getAverageProcessingTimeMs() {
        long processed = totalProcessedCount.sum();
        if (processed == 0) return 0.0;
        return (totalProcessingTimeNanos.sum() / 1_000_000.0) / processed;
    }

    public long getLastCycleTimestamp() {
        return lastCycleTimestamp;
    }

    public int getLastCycleProcessedCount() {
        return lastCycleProcessedCount;
    }

    public int getLastCycleRefilledCount() {
        return lastCycleRefilledCount;
    }

    public long getLastCycleDurationMs() {
        return lastCycleDurationMs;
    }

    public void reset() {
        successfulRefills.reset();
        skippedNotEmpty.reset();
        skippedPlayerNearby.reset();
        skippedChunkNotLoaded.reset();
        skippedInvalidBlock.reset();
        skippedTypeMismatch.reset();
        skippedDisabled.reset();
        skippedNoLootTable.reset();
        skippedBurning.reset();
        skippedDoubleChestPair.reset();
        errors.reset();
        chunksLoadedByRefill.reset();
        chunksUnloadedByRefill.reset();
        totalProcessingTimeNanos.reset();
        totalProcessedCount.reset();
        lastCycleTimestamp = 0;
        lastCycleProcessedCount = 0;
        lastCycleRefilledCount = 0;
        lastCycleDurationMs = 0;
    }
}
