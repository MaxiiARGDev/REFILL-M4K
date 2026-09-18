package com.arcraft.lootrefill.util;

import com.arcraft.lootrefill.LootRefillPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class MessageUtil {

    private MessageUtil() {}

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public static Component color(String text) {
        if (text == null) {
            return Component.empty();
        }
        return SERIALIZER.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> color(List<String> lines) {
        if (lines == null) {
            return List.of();
        }
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(color(line));
        }
        return components;
    }

    public static void sendMessage(CommandSender sender, String message) {
        String prefix = LootRefillPlugin.getInstance().getConfig().getString("messages.prefix", "&6&lLootRefill &8» ");
        sender.sendMessage(color(prefix + message));
    }

    public static void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }
}
