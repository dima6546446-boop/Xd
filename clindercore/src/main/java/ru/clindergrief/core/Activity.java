package ru.clindergrief.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Учёт активности (не AFK) и награда за каждые N минут активной игры. */
public final class Activity implements Listener {
    private final ClinderCore plugin;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> activeMinutes = new ConcurrentHashMap<>();

    public Activity(ClinderCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("playtime-rewards.enabled", true)) {
            return;
        }
        int interval = Math.max(1, plugin.getConfig().getInt("playtime-rewards.interval-minutes", 30));
        double reward = plugin.getConfig().getDouble("playtime-rewards.reward", 75);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAfk(p)) {
                continue;
            }
            int minutes = activeMinutes.merge(p.getUniqueId(), 1, Integer::sum);
            if (minutes >= interval) {
                activeMinutes.put(p.getUniqueId(), 0);
                plugin.economy().depositPlayer(p, reward);
                p.sendActionBar(Text.of(Text.fill(plugin.raw("playtime-reward"),
                        Map.of("amount", Text.money(reward), "minutes", interval))));
            }
        }
    }

    public boolean isAfk(Player p) {
        long afterMs = plugin.getConfig().getLong("playtime-rewards.afk-after-minutes", 5) * 60_000L;
        return System.currentTimeMillis() - lastActive.getOrDefault(p.getUniqueId(), 0L) > afterMs;
    }

    private void touch(Player p) {
        lastActive.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastActive.remove(e.getPlayer().getUniqueId());
        activeMinutes.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.hasChangedBlock() || e.hasChangedOrientation()) {
            touch(e.getPlayer());
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        touch(e.getPlayer());
    }
}
