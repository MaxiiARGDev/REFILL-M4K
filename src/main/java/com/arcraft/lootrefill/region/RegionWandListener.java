package com.arcraft.lootrefill.region;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class RegionWandListener implements Listener {

    private final LootRefillPlugin plugin;
    private final RegionManager regionManager;

    public RegionWandListener(LootRefillPlugin plugin, RegionManager regionManager) {
        this.plugin = plugin;
        this.regionManager = regionManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!regionManager.isWandItem(event.getItem())) {
            return;
        }

        if (!player.hasPermission("lootrefill.admin")) {
            MessageUtil.sendMessage(player, "&cNo tienes permisos para utilizar esta herramienta.");
            event.setCancelled(true);
            return;
        }

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // Cancelar el evento para evitar romper bloques o abrir inventarios
        event.setCancelled(true);

        Location loc = clickedBlock.getLocation();
        RegionSelection selection = regionManager.getSelection(player.getUniqueId());

        if (action == Action.LEFT_CLICK_BLOCK) {
            // Establecer Punto A
            if (selection.getPointB() != null && !selection.getPointB().getWorld().equals(loc.getWorld())) {
                selection.setPointB(null);
                MessageUtil.sendMessage(player, "&eEl mundo cambió. Se reinició el Punto B anterior.");
            }

            selection.setPointA(loc);
            MessageUtil.sendMessage(player, "&6[Wand] &aPunto A &7establecido en: &e" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + " &8(" + loc.getWorld().getName() + ")");

            if (selection.isComplete()) {
                sendSelectionSummary(player, selection);
            }
        } else {
            // Establecer Punto B
            if (selection.getPointA() != null && !selection.getPointA().getWorld().equals(loc.getWorld())) {
                selection.setPointA(null);
                MessageUtil.sendMessage(player, "&eEl mundo cambió. Se reinició el Punto A anterior.");
            }

            selection.setPointB(loc);
            MessageUtil.sendMessage(player, "&6[Wand] &aPunto B &7establecido en: &e" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + " &8(" + loc.getWorld().getName() + ")");

            if (selection.isComplete()) {
                sendSelectionSummary(player, selection);
            }
        }
    }

    private void sendSelectionSummary(Player player, RegionSelection selection) {
        MessageUtil.sendMessage(player, "&a¡Región completa seleccionada! &7Tamaño: &e" + selection.getWidthX() + "x" + selection.getHeightY() + "x" + selection.getLengthZ() + " &7(Volumen: &f" + selection.getVolume() + " &7bloques en &b" + selection.getTotalChunks() + " &7chunks).");
        MessageUtil.sendMessage(player, "&7Usa &e/loot region scan &7para escanear contenedores en la región.");
    }
}
