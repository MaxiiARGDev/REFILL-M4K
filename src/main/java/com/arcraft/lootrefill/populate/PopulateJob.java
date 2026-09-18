package com.arcraft.lootrefill.populate;

import java.util.UUID;

public class PopulateJob {

    public enum Status {
        RUNNING,
        PAUSED,
        COMPLETED,
        CANCELLED,
        ERROR
    }

    private final UUID id;
    private final String world;
    private Status status;
    private int total;
    private int processed;
    private int populated;
    private int skipped;
    private final long startedAt;
    private long updatedAt;
    private long completedAt;

    public PopulateJob(UUID id, String world, Status status, int total, int processed, int populated, int skipped, long startedAt, long updatedAt, long completedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.world = world != null ? world : "world";
        this.status = status != null ? status : Status.RUNNING;
        this.total = Math.max(0, total);
        this.processed = Math.max(0, processed);
        this.populated = Math.max(0, populated);
        this.skipped = Math.max(0, skipped);
        this.startedAt = startedAt > 0 ? startedAt : System.currentTimeMillis();
        this.updatedAt = updatedAt > 0 ? updatedAt : this.startedAt;
        this.completedAt = completedAt;
    }

    public static PopulateJob createNew(String world, int total) {
        long now = System.currentTimeMillis();
        return new PopulateJob(UUID.randomUUID(), world, Status.RUNNING, total, 0, 0, 0, now, now, 0L);
    }

    public UUID getId() {
        return id;
    }

    public String getWorld() {
        return world;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = System.currentTimeMillis();
        if (status == Status.COMPLETED || status == Status.CANCELLED || status == Status.ERROR) {
            this.completedAt = System.currentTimeMillis();
        }
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getPopulated() {
        return populated;
    }

    public void setPopulated(int populated) {
        this.populated = populated;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public double getProgressPercentage() {
        if (total <= 0) return 0.0;
        return Math.min(100.0, ((double) processed / total) * 100.0);
    }
}
