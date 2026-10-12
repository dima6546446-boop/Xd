package ru.clindergrief.core;

import org.bukkit.Bukkit;

import java.util.List;

/** Автообъявления в чат по кругу. */
public final class Broadcaster {
    private final ClinderCore plugin;
    private int index;

    public Broadcaster(ClinderCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long period = Math.max(30, plugin.getConfig().getLong("broadcast.interval-seconds", 300)) * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::next, period, period);
    }

    private void next() {
        if (!plugin.getConfig().getBoolean("broadcast.enabled", true) || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        List<String> messages = plugin.getConfig().getStringList("broadcast.messages");
        if (messages.isEmpty()) {
            return;
        }
        index = index % messages.size();
        Bukkit.broadcast(Text.of(plugin.getConfig().getString("prefix", "") + messages.get(index++)));
    }
}
