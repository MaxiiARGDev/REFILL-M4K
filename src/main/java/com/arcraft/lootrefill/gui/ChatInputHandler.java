package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputHandler implements Listener {

    private final LootRefillPlugin plugin;
    private final Map<UUID, Consumer<String>> pendingInputs;

    public ChatInputHandler(LootRefillPlugin plugin) {
        this.plugin = plugin;
        this.pendingInputs = new ConcurrentHashMap<>();
    }

    public void awaitInput(Player player, String promptMessage, Consumer<String> onInput) {
        pendingInputs.put(player.getUniqueId(), onInput);
        player.closeInventory();
        MessageUtil.sendMessage(player, promptMessage);
        MessageUtil.sendMessage(player, "&7(Escribe &ccancel &7para cancelar la acción)");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = pendingInputs.remove(player.getUniqueId());
        if (callback == null) {
            return;
        }

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                MessageUtil.sendMessage(player, "&cOperación cancelada.");
                return;
            }
            try {
                callback.accept(message);
            } catch (Exception e) {
                MessageUtil.sendMessage(player, "&cOcurrió un error al procesar la entrada.");
                plugin.getLogger().warning("Error procesando chat input de " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }
}
