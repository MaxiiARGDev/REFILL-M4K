package com.arcraft.lootrefill.region;

import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.container.LootContainer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RegionScanResult {

    private final RegionSelection selection;
    private final List<LootContainer> newCandidates;
    private final List<LootContainer> playerCandidates;
    private final int alreadyMapCount;
    private final int brokenCount;
    private final Map<ContainerType, Integer> countersByType;
    private final long scanTimestamp;

    public RegionScanResult(RegionSelection selection,
                            List<LootContainer> newCandidates,
                            List<LootContainer> playerCandidates,
                            int alreadyMapCount,
                            int brokenCount,
                            Map<ContainerType, Integer> countersByType) {
        this.selection = selection;
        this.newCandidates = newCandidates != null ? Collections.unmodifiableList(newCandidates) : Collections.emptyList();
        this.playerCandidates = playerCandidates != null ? Collections.unmodifiableList(playerCandidates) : Collections.emptyList();
        this.alreadyMapCount = alreadyMapCount;
        this.brokenCount = brokenCount;
        this.countersByType = countersByType != null ? Collections.unmodifiableMap(countersByType) : Collections.emptyMap();
        this.scanTimestamp = System.currentTimeMillis();
    }

    public RegionSelection getSelection() {
        return selection;
    }

    public List<LootContainer> getNewCandidates() {
        return newCandidates;
    }

    public List<LootContainer> getPlayerCandidates() {
        return playerCandidates;
    }

    public int getAlreadyMapCount() {
        return alreadyMapCount;
    }

    public int getPlayerCount() {
        return playerCandidates.size();
    }

    public int getBrokenCount() {
        return brokenCount;
    }

    public Map<ContainerType, Integer> getCountersByType() {
        return countersByType;
    }

    public int getTotalContainers() {
        return newCandidates.size() + alreadyMapCount + playerCandidates.size() + brokenCount;
    }

    public long getScanTimestamp() {
        return scanTimestamp;
    }
}
