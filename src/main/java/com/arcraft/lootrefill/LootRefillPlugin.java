package com.arcraft.lootrefill;

import com.arcraft.lootrefill.command.LootCommand;
import com.arcraft.lootrefill.container.ContainerManager;
import com.arcraft.lootrefill.container.ContainerRegistry;
import com.arcraft.lootrefill.gui.GuiManager;
import com.arcraft.lootrefill.loot.LootManager;
import com.arcraft.lootrefill.loot.LootTableStorage;
import com.arcraft.lootrefill.populate.PopulateManager;
import com.arcraft.lootrefill.refill.RefillManager;
import com.arcraft.lootrefill.scanner.ScanJob;
import com.arcraft.lootrefill.scanner.WorldScanner;
import com.arcraft.lootrefill.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class LootRefillPlugin extends JavaPlugin {

    private static LootRefillPlugin instance;

    private DatabaseManager databaseManager;
    private LootTableStorage lootTableStorage;
    private LootManager lootManager;
    private com.arcraft.lootrefill.pool.LootPoolStorage lootPoolStorage;
    private com.arcraft.lootrefill.pool.LootPoolManager lootPoolManager;
    private ContainerManager containerManager;
    private ContainerRegistry containerRegistry;
    private RefillManager refillManager;
    private WorldScanner worldScanner;
    private PopulateManager populateManager;
    private GuiManager guiManager;
    private com.arcraft.lootrefill.region.RegionManager regionManager;
    private com.arcraft.lootrefill.region.RegionScanner regionScanner;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Cargar archivo de configuración
        saveDefaultConfig();

        // 2. Inicializar persistencia SQLite y tablas
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();

        // 3. Inicializar managers de Loot, Pools y Contenedores
        this.lootTableStorage = new LootTableStorage(this, databaseManager);
        this.lootManager = new LootManager(this, lootTableStorage);
        this.lootManager.load();

        this.lootPoolStorage = new com.arcraft.lootrefill.pool.LootPoolStorage(this, databaseManager);
        this.lootPoolManager = new com.arcraft.lootrefill.pool.LootPoolManager(this, lootPoolStorage);
        this.lootPoolManager.load();

        this.containerManager = new ContainerManager(this, databaseManager);
        this.containerManager.load();

        this.containerRegistry = new ContainerRegistry(this, containerManager);

        // 4. Inicializar World Scanner liviano
        this.worldScanner = new WorldScanner(this, containerManager);

        // 5. Inicializar sistema de Refill escalonado
        this.refillManager = new RefillManager(this, containerManager, lootManager);
        this.refillManager.start();

        // 6. Inicializar Populate Manager (Etapa 3)
        this.populateManager = new PopulateManager(this, containerManager);

        // 7. Inicializar Region Wand & Scanner (Etapa 4.1)
        this.regionManager = new com.arcraft.lootrefill.region.RegionManager(this);
        this.regionScanner = new com.arcraft.lootrefill.region.RegionScanner(this, containerManager, regionManager);

        // 8. Inicializar sistema de GUIs, capturas de chat y listeners de bloques
        this.guiManager = new GuiManager(this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);
        Bukkit.getPluginManager().registerEvents(guiManager.getChatInputHandler(), this);
        Bukkit.getPluginManager().registerEvents(new com.arcraft.lootrefill.container.ContainerBlockListener(this, containerManager, containerRegistry), this);
        Bukkit.getPluginManager().registerEvents(new com.arcraft.lootrefill.region.RegionWandListener(this, regionManager), this);

        // 9. Registrar comandos
        LootCommand lootCommand = new LootCommand(this);
        Objects.requireNonNull(getCommand("loot")).setExecutor(lootCommand);
        Objects.requireNonNull(getCommand("loot")).setTabCompleter(lootCommand);

        // 10. Detección de scans previos incompletos tras reinicio
        ScanJob incompleteJob = databaseManager.loadIncompleteScanJob();
        if (incompleteJob != null) {
            getLogger().warning("==========================================================");
            getLogger().warning("   ¡ATENCIÓN! Se detectó un escaneo incompleto:");
            getLogger().warning("   Mundo: " + incompleteJob.getWorld());
            getLogger().warning("   Estado: " + incompleteJob.getStatus().name() + " | Progreso: " + incompleteJob.getProcessedChunks() + "/" + incompleteJob.getTotalChunks() + " chunks");
            getLogger().warning("   Usa '/loot scan resume' para continuar o '/loot scan cancel'");
            getLogger().warning("==========================================================");
        }

        getLogger().info("===============================================");
        getLogger().info("   LootRefill v" + getDescription().getVersion() + " (Etapa 4: Auto Refill)");
        getLogger().info("   Servidor: Arcraft - Estado: Habilitado");
        getLogger().info("===============================================");
    }

    @Override
    public void onDisable() {
        if (worldScanner != null && worldScanner.isScanning()) {
            worldScanner.stopTask();
            ScanJob job = worldScanner.getActiveJob();
            if (job != null) {
                job.setStatus(ScanJob.Status.PAUSED);
                databaseManager.saveScanJob(job);
                getLogger().info("Escaneo activo guardado como PAUSED debido al apagado del servidor.");
            }
        }

        if (populateManager != null && populateManager.isPopulating()) {
            populateManager.getPopulateQueue().cancel();
            getLogger().info("Proceso de Populate activo cancelado de forma segura debido al apagado del servidor.");
        }

        if (refillManager != null) {
            refillManager.stop();
        }

        getLogger().info("LootRefill deshabilitado correctamente.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        lootManager.load();
        lootPoolManager.load();
        containerManager.load();
        refillManager.loadConfig();
        refillManager.start();
    }

    public static LootRefillPlugin getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public com.arcraft.lootrefill.pool.LootPoolManager getLootPoolManager() {
        return lootPoolManager;
    }

    public ContainerManager getContainerManager() {
        return containerManager;
    }

    public ContainerRegistry getContainerRegistry() {
        return containerRegistry;
    }

    public RefillManager getRefillManager() {
        return refillManager;
    }

    public WorldScanner getWorldScanner() {
        return worldScanner;
    }

    public PopulateManager getPopulateManager() {
        return populateManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public com.arcraft.lootrefill.region.RegionManager getRegionManager() {
        return regionManager;
    }

    public com.arcraft.lootrefill.region.RegionScanner getRegionScanner() {
        return regionScanner;
    }
}
