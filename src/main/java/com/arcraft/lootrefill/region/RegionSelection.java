package com.arcraft.lootrefill.region;

import org.bukkit.Location;
import org.bukkit.World;

public class RegionSelection {

    private Location pointA;
    private Location pointB;

    public RegionSelection() {
    }

    public Location getPointA() {
        return pointA;
    }

    public void setPointA(Location pointA) {
        this.pointA = pointA;
    }

    public Location getPointB() {
        return pointB;
    }

    public void setPointB(Location pointB) {
        this.pointB = pointB;
    }

    public boolean isComplete() {
        return pointA != null && pointB != null && pointA.getWorld() != null && pointA.getWorld().equals(pointB.getWorld());
    }

    public World getWorld() {
        return isComplete() ? pointA.getWorld() : (pointA != null ? pointA.getWorld() : (pointB != null ? pointB.getWorld() : null));
    }

    public int getMinX() {
        return Math.min(pointA.getBlockX(), pointB.getBlockX());
    }

    public int getMaxX() {
        return Math.max(pointA.getBlockX(), pointB.getBlockX());
    }

    public int getMinY() {
        return Math.min(pointA.getBlockY(), pointB.getBlockY());
    }

    public int getMaxY() {
        return Math.max(pointA.getBlockY(), pointB.getBlockY());
    }

    public int getMinZ() {
        return Math.min(pointA.getBlockZ(), pointB.getBlockZ());
    }

    public int getMaxZ() {
        return Math.max(pointA.getBlockZ(), pointB.getBlockZ());
    }

    public int getWidthX() {
        return isComplete() ? (getMaxX() - getMinX() + 1) : 0;
    }

    public int getHeightY() {
        return isComplete() ? (getMaxY() - getMinY() + 1) : 0;
    }

    public int getLengthZ() {
        return isComplete() ? (getMaxZ() - getMinZ() + 1) : 0;
    }

    public long getVolume() {
        return isComplete() ? ((long) getWidthX() * getHeightY() * getLengthZ()) : 0L;
    }

    public boolean contains(int x, int y, int z) {
        if (!isComplete()) return false;
        return x >= getMinX() && x <= getMaxX()
                && y >= getMinY() && y <= getMaxY()
                && z >= getMinZ() && z <= getMaxZ();
    }

    public int getMinChunkX() {
        return getMinX() >> 4;
    }

    public int getMaxChunkX() {
        return getMaxX() >> 4;
    }

    public int getMinChunkZ() {
        return getMinZ() >> 4;
    }

    public int getMaxChunkZ() {
        return getMaxZ() >> 4;
    }

    public int getTotalChunks() {
        if (!isComplete()) return 0;
        return (getMaxChunkX() - getMinChunkX() + 1) * (getMaxChunkZ() - getMinChunkZ() + 1);
    }
}
