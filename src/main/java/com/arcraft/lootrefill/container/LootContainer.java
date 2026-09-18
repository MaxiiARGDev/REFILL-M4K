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
    private String lootPoolId;
    private boolean enabled;
    private boolean looted;
    private long lastLoot;
    private Long nextRefill;
    private boolean refillEnabled;
    private int refillIntervalSeconds;

    // Campos de identidad de la Etapa 2.1
    private ContainerSource source;
    private ContainerStatus status;
    private boolean managed;
    private boolean registered;
    private final long createdAt;
    private long updatedAt;

    // Historial de selección de pool para debug y auditoría (últimas 5 selecciones)
    private final java.util.Deque<String> recentSelections = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public LootContainer(UUID id, String world, int x, int y, int z, ContainerType containerType, String lootTableId, boolean enabled, boolean looted, long lastLoot, Long nextRefill) {
        this(id, world, x, y, z, containerType, lootTableId, null, enabled, looted, lastLoot, nextRefill, true, 1800, ContainerSource.MAP, ContainerStatus.ACTIVE, true, true, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public LootContainer(UUID id, String world, int x, int y, int z, ContainerType containerType, String lootTableId, boolean enabled, boolean looted, long lastLoot, Long nextRefill, boolean refillEnabled, int refillIntervalSeconds) {
        this(id, world, x, y, z, containerType, lootTableId, null, enabled, looted, lastLoot, nextRefill, refillEnabled, refillIntervalSeconds, ContainerSource.MAP, ContainerStatus.ACTIVE, true, true, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public LootContainer(UUID id, String world, int x, int y, int z, ContainerType containerType, String lootTableId, boolean enabled, boolean looted, long lastLoot, Long nextRefill, boolean refillEnabled, int refillIntervalSeconds, ContainerSource source, ContainerStatus status, boolean managed, boolean registered, long createdAt, long updatedAt) {
        this(id, world, x, y, z, containerType, lootTableId, null, enabled, looted, lastLoot, nextRefill, refillEnabled, refillIntervalSeconds, source, status, managed, registered, createdAt, updatedAt);
    }

    public LootContainer(UUID id, String world, int x, int y, int z, ContainerType containerType, String lootTableId, String lootPoolId, boolean enabled, boolean looted, long lastLoot, Long nextRefill, boolean refillEnabled, int refillIntervalSeconds, ContainerSource source, ContainerStatus status, boolean managed, boolean registered, long createdAt, long updatedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.world = world != null ? world : "world";
        this.x = x;
        this.y = y;
        this.z = z;
        this.containerType = containerType != null ? containerType : ContainerType.CHEST;
        this.lootTableId = (lootTableId != null && !lootTableId.trim().isEmpty()) ? lootTableId.trim() : null;
        this.lootPoolId = (lootPoolId != null && !lootPoolId.trim().isEmpty()) ? lootPoolId.trim().toLowerCase() : null;
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
        return new LootContainer(UUID.randomUUID(), worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), type, lootTableId, null, true, false, 0L, null, true, 1800, ContainerSource.MAP, ContainerStatus.ACTIVE, true, true, now, now);
    }

    public static LootContainer fromLocationWithPool(Location loc, ContainerType type, String lootPoolId) {
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        long now = System.currentTimeMillis();
        return new LootContainer(UUID.randomUUID(), worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), type, null, lootPoolId, true, false, 0L, null, true, 1800, ContainerSource.MAP, ContainerStatus.ACTIVE, true, true, now, now);
    }

    public static LootContainer createPlayerContainer(Location loc, ContainerType type) {
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        long now = System.currentTimeMillis();
        return new LootContainer(UUID.randomUUID(), worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), type, null, null, false, false, 0L, null, false, 1800, ContainerSource.PLAYER, ContainerStatus.ACTIVE, false, false, now, now);
    }

    public boolean hasLootConfigured() {
        return (lootPoolId != null && !lootPoolId.trim().isEmpty())
            || (lootTableId != null && !lootTableId.trim().isEmpty());
    }

    public boolean isAdministrable() {
        return registered && managed && source == ContainerSource.MAP && status == ContainerStatus.ACTIVE;
    }

    public boolean isDue() {
        return nextRefill != null && nextRefill <= System.currentTimeMillis();
    }

    public boolean isEligibleForRefill() {
        return isAdministrable() && refillEnabled && hasLootConfigured() && isDue();
    }

    public String getEligibilityReason() {
        if (source != ContainerSource.MAP) return "source (" + source + ") != MAP";
        if (status != ContainerStatus.ACTIVE) return "status (" + status + ") != ACTIVE";
        if (!managed) return "managed = false";
        if (!registered) return "registered = false";
        if (!refillEnabled) return "refill_enabled = false";
        if (!hasLootConfigured()) return "Sin Loot configurado (loot_table_id y loot_pool_id son null)";
        if (nextRefill == null) return "next_refill es null";
        long now = System.currentTimeMillis();
        if (nextRefill > now) {
            long diffSec = (nextRefill - now) / 1000;
            return "next_refill en el futuro (faltan " + diffSec + "s / " + (diffSec / 60) + "m)";
        }
        return "ELEGIBLE_AND_DUE";
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
        this.lootTableId = (lootTableId != null && !lootTableId.trim().isEmpty()) ? lootTableId.trim() : null;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getLootPoolId() {
        return lootPoolId;
    }

    public void setLootPoolId(String lootPoolId) {
        this.lootPoolId = (lootPoolId != null && !lootPoolId.trim().isEmpty()) ? lootPoolId.trim().toLowerCase() : null;
        this.updatedAt = System.currentTimeMillis();
    }

    public java.util.List<String> getRecentSelections() {
        return new java.util.ArrayList<>(recentSelections);
    }

    public void addRecentSelection(String selectionSummary) {
        if (selectionSummary == null || selectionSummary.trim().isEmpty()) return;
        recentSelections.addFirst(selectionSummary.trim());
        while (recentSelections.size() > 5) {
            recentSelections.removeLast();
        }
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

    public Long getNextRefill() {
        return nextRefill;
    }

    public void setNextRefill(Long nextRefill) {
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
