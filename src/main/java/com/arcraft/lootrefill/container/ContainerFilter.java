package com.arcraft.lootrefill.container;

import java.util.Objects;

/**
 * Objeto inmutable que encapsula los criterios de búsqueda, filtrado y ordenamiento para Container Manager 2.0.
 */
public final class ContainerFilter {

    private final String world;
    private final ContainerType containerType;
    private final ContainerSource source;
    private final ContainerStatus status;
    private final Boolean managed;
    private final Boolean registered;
    private final Boolean refillEnabled;
    private final LootFilterType lootFilterType;
    private final String lootPoolId;
    private final String lootTableId;
    private final String query;
    private final ContainerSortField sortField;
    private final boolean ascending;

    public ContainerFilter(
            String world,
            ContainerType containerType,
            ContainerSource source,
            ContainerStatus status,
            Boolean managed,
            Boolean registered,
            Boolean refillEnabled,
            LootFilterType lootFilterType,
            String lootPoolId,
            String lootTableId,
            String query,
            ContainerSortField sortField,
            boolean ascending
    ) {
        this.world = (world != null && !world.trim().isEmpty()) ? world.trim().toLowerCase() : null;
        this.containerType = containerType;
        this.source = source;
        this.status = status;
        this.managed = managed;
        this.registered = registered;
        this.refillEnabled = refillEnabled;
        this.lootFilterType = lootFilterType != null ? lootFilterType : LootFilterType.ALL;
        this.lootPoolId = (lootPoolId != null && !lootPoolId.trim().isEmpty()) ? lootPoolId.trim().toLowerCase() : null;
        this.lootTableId = (lootTableId != null && !lootTableId.trim().isEmpty()) ? lootTableId.trim().toLowerCase() : null;
        this.query = (query != null && !query.trim().isEmpty()) ? query.trim().toLowerCase() : null;
        this.sortField = sortField != null ? sortField : ContainerSortField.CREATED_AT;
        this.ascending = ascending;
    }

    public static ContainerFilter empty() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean test(LootContainer c) {
        if (c == null) return false;

        // Filtro por mundo
        if (world != null && !c.getWorld().equalsIgnoreCase(world)) {
            return false;
        }

        // Filtro por tipo de contenedor
        if (containerType != null && c.getContainerType() != containerType) {
            return false;
        }

        // Filtro por origen (source)
        if (source != null && c.getSource() != source) {
            return false;
        }

        // Filtro por estado (status)
        if (status != null && c.getStatus() != status) {
            return false;
        }

        // Filtro por managed
        if (managed != null && c.isManaged() != managed) {
            return false;
        }

        // Filtro por registered
        if (registered != null && c.isRegistered() != registered) {
            return false;
        }

        // Filtro por refillEnabled
        if (refillEnabled != null && c.isRefillEnabled() != refillEnabled) {
            return false;
        }

        // Filtro por tipo de loot
        switch (lootFilterType) {
            case HAS_LOOT -> {
                if (!c.hasLootConfigured()) return false;
            }
            case NO_LOOT -> {
                if (c.hasLootConfigured()) return false;
            }
            case LOOT_POOL -> {
                if (c.getLootPoolId() == null) return false;
            }
            case LOOT_TABLE -> {
                // Considera configuración legacy donde loot_table_id no es nulo
                if (c.getLootTableId() == null) return false;
            }
            case ALL -> {}
        }

        // Filtro específico por LootPool ID
        if (lootPoolId != null) {
            if (c.getLootPoolId() == null || !c.getLootPoolId().equalsIgnoreCase(lootPoolId)) {
                return false;
            }
        }

        // Filtro específico por LootTable ID
        if (lootTableId != null) {
            if (c.getLootTableId() == null || !c.getLootTableId().equalsIgnoreCase(lootTableId)) {
                return false;
            }
        }

        // Búsqueda textual sobre ID, coordenadas, mundo, tabla y pool
        if (query != null) {
            boolean matches = false;
            if (c.getId().toString().toLowerCase().contains(query)) matches = true;
            else if (c.getWorld().toLowerCase().contains(query)) matches = true;
            else if (c.getContainerType().name().toLowerCase().contains(query)) matches = true;
            else if (c.getLootPoolId() != null && c.getLootPoolId().toLowerCase().contains(query)) matches = true;
            else if (c.getLootTableId() != null && c.getLootTableId().toLowerCase().contains(query)) matches = true;
            else {
                // Coordenadas: "x,y,z" o partes "x, z"
                String coords1 = c.getX() + "," + c.getY() + "," + c.getZ();
                String coords2 = c.getX() + ", " + c.getY() + ", " + c.getZ();
                String coords3 = c.getX() + "," + c.getZ();
                if (coords1.contains(query) || coords2.contains(query) || coords3.contains(query)) {
                    matches = true;
                }
            }
            if (!matches) return false;
        }

        return true;
    }

    public String getWorld() {
        return world;
    }

    public ContainerType getContainerType() {
        return containerType;
    }

    public ContainerSource getSource() {
        return source;
    }

    public ContainerStatus getStatus() {
        return status;
    }

    public Boolean getManaged() {
        return managed;
    }

    public Boolean getRegistered() {
        return registered;
    }

    public Boolean getRefillEnabled() {
        return refillEnabled;
    }

    public LootFilterType getLootFilterType() {
        return lootFilterType;
    }

    public String getLootPoolId() {
        return lootPoolId;
    }

    public String getLootTableId() {
        return lootTableId;
    }

    public String getQuery() {
        return query;
    }

    public ContainerSortField getSortField() {
        return sortField;
    }

    public boolean isAscending() {
        return ascending;
    }

    public boolean hasActiveFilters() {
        return getActiveFilterCount() > 0;
    }

    public int getActiveFilterCount() {
        int count = 0;
        if (world != null) count++;
        if (containerType != null) count++;
        if (source != null) count++;
        if (status != null) count++;
        if (managed != null) count++;
        if (registered != null) count++;
        if (refillEnabled != null) count++;
        if (lootFilterType != null && lootFilterType != LootFilterType.ALL) count++;
        if (lootPoolId != null) count++;
        if (lootTableId != null) count++;
        if (query != null) count++;
        return count;
    }

    public Builder toBuilder() {
        return new Builder()
                .world(this.world)
                .containerType(this.containerType)
                .source(this.source)
                .status(this.status)
                .managed(this.managed)
                .registered(this.registered)
                .refillEnabled(this.refillEnabled)
                .lootFilterType(this.lootFilterType)
                .lootPoolId(this.lootPoolId)
                .lootTableId(this.lootTableId)
                .query(this.query)
                .sortField(this.sortField)
                .ascending(this.ascending);
    }

    public static class Builder {
        private String world;
        private ContainerType containerType;
        private ContainerSource source;
        private ContainerStatus status;
        private Boolean managed;
        private Boolean registered;
        private Boolean refillEnabled;
        private LootFilterType lootFilterType = LootFilterType.ALL;
        private String lootPoolId;
        private String lootTableId;
        private String query;
        private ContainerSortField sortField = ContainerSortField.CREATED_AT;
        private boolean ascending = false; // Más recientes primero por defecto

        public String getWorld() {
            return world;
        }

        public ContainerType getContainerType() {
            return containerType;
        }

        public ContainerSource getSource() {
            return source;
        }

        public ContainerStatus getStatus() {
            return status;
        }

        public Boolean getManaged() {
            return managed;
        }

        public Boolean getRegistered() {
            return registered;
        }

        public Boolean getRefillEnabled() {
            return refillEnabled;
        }

        public LootFilterType getLootFilterType() {
            return lootFilterType;
        }

        public String getLootPoolId() {
            return lootPoolId;
        }

        public String getLootTableId() {
            return lootTableId;
        }

        public String getQuery() {
            return query;
        }

        public ContainerSortField getSortField() {
            return sortField;
        }

        public boolean isAscending() {
            return ascending;
        }

        public Builder world(String world) {
            this.world = world;
            return this;
        }

        public Builder containerType(ContainerType containerType) {
            this.containerType = containerType;
            return this;
        }

        public Builder source(ContainerSource source) {
            this.source = source;
            return this;
        }

        public Builder status(ContainerStatus status) {
            this.status = status;
            return this;
        }

        public Builder managed(Boolean managed) {
            this.managed = managed;
            return this;
        }

        public Builder registered(Boolean registered) {
            this.registered = registered;
            return this;
        }

        public Builder refillEnabled(Boolean refillEnabled) {
            this.refillEnabled = refillEnabled;
            return this;
        }

        public Builder lootFilterType(LootFilterType lootFilterType) {
            this.lootFilterType = lootFilterType;
            return this;
        }

        public Builder lootPoolId(String lootPoolId) {
            this.lootPoolId = lootPoolId;
            return this;
        }

        public Builder lootTableId(String lootTableId) {
            this.lootTableId = lootTableId;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder sortField(ContainerSortField sortField) {
            this.sortField = sortField;
            return this;
        }

        public Builder ascending(boolean ascending) {
            this.ascending = ascending;
            return this;
        }

        public ContainerFilter build() {
            return new ContainerFilter(
                    world, containerType, source, status,
                    managed, registered, refillEnabled,
                    lootFilterType, lootPoolId, lootTableId,
                    query, sortField, ascending
            );
        }
    }
}
