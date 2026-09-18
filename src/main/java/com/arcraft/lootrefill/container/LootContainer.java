package com.arcraft.lootrefill.container;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class LootContainer {

    private final UUID id;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private ContainerType containerType;
    private String lootTableId;
    private boolean enabled;
    private boolean looted;
    private long lastLoot;
    private long nextRefill;
    private boolean refillEnabled;
    private int refillIntervalSeconds;

    // Campos de identidad de la Etapa 2.1
    private ContainerSource source;
    private ContainerStatus status;
    private boolean managed;
    private boolean registered;
    private final long createdAt;
    private long updatedAt;

    public LootContainer(UUID id, String world, int x, int y, int z, ContainerType containerType, String lootTableId, boolean enabled, boolean looted, long lastLoot, long nextRefill) {
        this(id, world, x, y, z, containerType, lootTableId, enabled, looted, lastLoot, nextRefill, true, 1800, ContainerSource.MAP, ContainerStatus.ACTIVE, true, true, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public LootContainer(UUID id, String world, int x, int y, int z, ContainerType containerType, String lootTableId, boolean enabled, boolean looted, long lastLoot, long nextRefill, boolean refillEnabled, int refillIntervalSeconds) {
        this(id, world, x, y, z, containerType, lootTableId, enabled, looted, lastLoot, nextRefill, refillEnabled, refillIntervalSeconds, ContainerSource.MAP, ContainerStatus.ACTIVE, true, true, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public LootContainer(UUID id, String world, int x, int y, int z, ContainerType containerType, String lootTableId, boolean enabled, boolean looted, long lastLoot, long nextRefill, boolean refillEnabled, int refillIntervalSeconds, ContainerSource source, ContainerStatus status, boolean managed, boolean registered, long createdAt, long updatedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.world = world != null ? world : "world";
        this.x = x;
        this.y = y;
        this.z = z;
        this.containerType = containerType != null ? containerType : ContainerType.CHEST;
        this.lootTableId = lootTableId != null ? lootTableId : "";
        this.enabled = enabled;
        this.looted = looted;
        this.lastLoot = lastLoot;
        this.nextRefill = nextRefill;
        this.refillEnabled = refillEnabled;
        this.refillIntervalSeconds = refillIntervalSeconds > 0 ? refillIntervalSeconds : 1800;
        this.source = source != null ? source : ContainerSource.MAP;
        this.status = status != null ? status : ContainerStatus.ACTIVE;
        this.managed = managed;
        this.registered = registered;
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.updatedAt = updatedAt > 0 ? updatedAt : this.createdAt;
    }

    public static LootContainer fromLocation(Location loc, ContainerType type, String lootTableId) {
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        long now = System.currentTimeMillis();
        return new LootContainer(UUID.randomUUID(), worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), type, lootTableId, true, false, 0L, 0L, true, 1800, ContainerSource.MAP, ContainerStatus.ACTIVE, true, true, now, now);
    }

    public static LootContainer createPlayerContainer(Location loc, ContainerType type) {
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        long now = System.currentTimeMillis();
        return new LootContainer(UUID.randomUUID(), worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), type, "", false, false, 0L, 0L, false, 1800, ContainerSource.PLAYER, ContainerStatus.ACTIVE, false, false, now, now);
    }

    public boolean isAdministrable() {
        return registered && managed && source == ContainerSource.MAP && status == ContainerStatus.ACTIVE && lootTableId != null && !lootTableId.isEmpty();
    }

    public boolean isEligibleForRefill() {
        return isAdministrable() && refillEnabled && nextRefill <= System.currentTimeMillis();
    }

    public UUID getId() {
        return id;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public ContainerType getContainerType() {
        return containerType;
    }

    public void setContainerType(ContainerType containerType) {
        this.containerType = containerType;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getLootTableId() {
        return lootTableId;
    }

    public void setLootTableId(String lootTableId) {
        this.lootTableId = lootTableId != null ? lootTableId : "";
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isLooted() {
        return looted;
    }

    public void setLooted(boolean looted) {
        this.looted = looted;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getLastLoot() {
        return lastLoot;
    }

    public void setLastLoot(long lastLoot) {
        this.lastLoot = lastLoot;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getNextRefill() {
        return nextRefill;
    }

    public void setNextRefill(long nextRefill) {
        this.nextRefill = nextRefill;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isRefillEnabled() {
        return refillEnabled;
    }

    public void setRefillEnabled(boolean refillEnabled) {
        this.refillEnabled = refillEnabled;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getRefillIntervalSeconds() {
        return refillIntervalSeconds;
    }

    public void setRefillIntervalSeconds(int refillIntervalSeconds) {
        this.refillIntervalSeconds = Math.max(10, refillIntervalSeconds);
        this.updatedAt = System.currentTimeMillis();
    }

    public ContainerSource getSource() {
        return source;
    }

    public void setSource(ContainerSource source) {
        this.source = source != null ? source : ContainerSource.UNKNOWN;
        this.updatedAt = System.currentTimeMillis();
    }

    public ContainerStatus getStatus() {
        return status;
    }

    public void setStatus(ContainerStatus status) {
        this.status = status != null ? status : ContainerStatus.ACTIVE;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isManaged() {
        return managed;
    }

    public void setManaged(boolean managed) {
        this.managed = managed;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    public String getLocationKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
