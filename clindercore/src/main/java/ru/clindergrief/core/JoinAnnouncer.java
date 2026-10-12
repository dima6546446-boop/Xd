package ru.clindergrief.core;

import net.kyori.adventure.title.Title;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Duration;
import java.util.Map;

/** Приветственный заголовок и объявления о входе обладателей старших привилегий. */
public final class JoinAnnouncer implements Listener {
    private final ClinderCore plugin;

    public JoinAnnouncer(ClinderCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Map<String, String> v = Map.of("player", p.getName());
        var cfg = plugin.getConfig();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (cfg.getBoolean("join.title-for-player", true)) {
                p.showTitle(Title.title(Text.of(Text.fill(cfg.getString("join.title", ""), v)),
                        Text.of(Text.fill(cfg.getString("join.subtitle", ""), v)),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(800))));
            }
            String first = cfg.getString("join.first-join-broadcast", "");
            if (!p.hasPlayedBefore() && !first.isEmpty()) {
                Bukkit.broadcast(Text.of(Text.fill(first, v)));
            }
            announce(p, v);
        }, 20L);
    }

    private void announce(Player p, Map<String, String> v) {
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
        if (rsp == null) {
            return;
        }
        Permission perms = rsp.getProvider();
        for (Map<?, ?> a : plugin.getConfig().getMapList("join.announcements")) {
            Object group = a.get("group");
            if (group == null || !perms.playerInGroup(p, String.valueOf(group))) {
                continue;
            }
            Bukkit.broadcast(Text.of(Text.fill(String.valueOf(a.get("message")), v)));
            Object soundName = a.get("sound");
            if (soundName != null) {
                NamespacedKey key = NamespacedKey.fromString(String.valueOf(soundName).toLowerCase());
                Sound sound = key == null ? null : Registry.SOUNDS.get(key);
                if (sound != null) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.playSound(online.getLocation(), sound, 0.6f, 1f);
                    }
                }
            }
            return;
        }
    }
}
