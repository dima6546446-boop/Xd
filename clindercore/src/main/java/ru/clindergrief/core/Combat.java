package ru.clindergrief.core;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Режим боя: после удара по игроку — таймер в боссбаре, запрет телепортов, смерть при выходе. */
public final class Combat implements Listener {
    private final ClinderCore plugin;
    private final Map<UUID, Long> until = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public Combat(ClinderCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("combat.enabled", true);
    }

    private long seconds() {
        return plugin.getConfig().getLong("combat.seconds", 15);
    }

    public boolean inCombat(Player p) {
        Long t = until.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    private void tag(Player p) {
        if (p.hasPermission("clindercore.combat.bypass")) {
            return;
        }
        boolean was = inCombat(p);
        until.put(p.getUniqueId(), System.currentTimeMillis() + seconds() * 1000L);
        if (!was) {
            p.sendMessage(plugin.msg("combat-start", Map.of("seconds", seconds())));
        }
        bars.computeIfAbsent(p.getUniqueId(), k -> {
            BossBar bar = BossBar.bossBar(Text.of(""), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
            p.showBossBar(bar);
            return bar;
        });
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, BossBar> e : new HashMap<>(bars).entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            long left = until.getOrDefault(e.getKey(), 0L) - now;
            if (p == null || left <= 0) {
                if (p != null) {
                    p.hideBossBar(e.getValue());
                    p.sendMessage(plugin.msg("combat-end"));
                }
                bars.remove(e.getKey());
                until.remove(e.getKey());
                continue;
            }
            e.getValue().name(Text.of(Text.fill(plugin.raw("combat-bar"), Map.of("seconds", left / 1000 + 1))));
            e.getValue().progress(Math.max(0f, Math.min(1f, left / (seconds() * 1000f))));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!enabled() || !(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (e.getDamager() instanceof Player p) {
            attacker = p;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        tag(victim);
        tag(attacker);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!enabled() || !inCombat(e.getPlayer())) {
            return;
        }
        String cmd = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }
        List<String> blocked = plugin.getConfig().getStringList("combat.blocked-commands");
        if (blocked.contains(cmd)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.msg("combat-blocked"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (enabled() && inCombat(p) && plugin.getConfig().getBoolean("combat.kill-on-logout", true)) {
            p.setHealth(0);
            Bukkit.broadcast(plugin.msg("combat-logout", Map.of("player", p.getName())));
        }
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
        until.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        until.remove(p.getUniqueId());
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
    }
}
