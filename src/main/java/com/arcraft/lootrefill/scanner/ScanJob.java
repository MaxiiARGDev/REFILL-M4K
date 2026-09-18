package com.arcraft.lootrefill.scanner;

import java.util.UUID;

public class ScanJob {

    public enum Status {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETED,
        CANCELLED,
        ERROR
    }

    private final UUID id;
    private final String world;
    private int currentChunkX;
    private int currentChunkZ;
    private int totalChunks;
    private int processedChunks;
    private int foundContainers;
    private Status status;
    private final long startedAt;
    private long updatedAt;

    public ScanJob(UUID id, String world, int currentChunkX, int currentChunkZ, int totalChunks, int processedChunks, int foundContainers, Status status, long startedAt, long updatedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.world = world != null ? world : "world";
        this.currentChunkX = currentChunkX;
        this.currentChunkZ = currentChunkZ;
        this.totalChunks = Math.max(0, totalChunks);
        this.processedChunks = Math.max(0, processedChunks);
        this.foundContainers = Math.max(0, foundContainers);
        this.status = status != null ? status : Status.IDLE;
        this.startedAt = startedAt > 0 ? startedAt : System.currentTimeMillis();
        this.updatedAt = updatedAt > 0 ? updatedAt : this.startedAt;
    }

    public static ScanJob createNew(String world, int totalChunks) {
        long now = System.currentTimeMillis();
        return new ScanJob(UUID.randomUUID(), world, 0, 0, totalChunks, 0, 0, Status.RUNNING, now, now);
    }

    public UUID getId() {
        return id;
    }

    public String getWorld() {
        return world;
    }

    public int getCurrentChunkX() {
        return currentChunkX;
    }

    public void setCurrentChunkX(int currentChunkX) {
        this.currentChunkX = currentChunkX;
    }

    public int getCurrentChunkZ() {
        return currentChunkZ;
    }

    public void setCurrentChunkZ(int currentChunkZ) {
        this.currentChunkZ = currentChunkZ;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getProcessedChunks() {
        return processedChunks;
    }

    public void setProcessedChunks(int processedChunks) {
        this.processedChunks = processedChunks;
    }

    public void incrementProcessedChunks() {
        this.processedChunks++;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getFoundContainers() {
        return foundContainers;
    }

    public void setFoundContainers(int foundContainers) {
        this.foundContainers = foundContainers;
    }

    public void addFoundContainers(int count) {
        this.foundContainers += count;
        this.updatedAt = System.currentTimeMillis();
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = System.currentTimeMillis();
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

    public double getProgressPercentage() {
        if (totalChunks <= 0) return 0.0;
        return Math.min(100.0, ((double) processedChunks / totalChunks) * 100.0);
    }

    public long getElapsedTimeSeconds() {
        return Math.max(0, (System.currentTimeMillis() - startedAt) / 1000L);
    }

    public long getEstimatedTimeRemainingSeconds() {
        if (processedChunks <= 0 || totalChunks <= 0) return 0;
        long elapsed = getElapsedTimeSeconds();
        if (elapsed <= 0) return 0;
        double rate = (double) processedChunks / elapsed; // chunks por segundo
        if (rate <= 0) return 0;
        int remainingChunks = Math.max(0, totalChunks - processedChunks);
        return (long) (remainingChunks / rate);
    }
}
