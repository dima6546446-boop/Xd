package ru.clindergrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Общественная шахта: кубоид заполняется рудой по таймеру (как «шахта» на ReallyWorld/FunTime). */
public final class Mine {
    private final ClinderCore plugin;
    private long nextReset;
    private boolean warned;

    public Mine(ClinderCore plugin) {
        this.plugin = plugin;
        nextReset = System.currentTimeMillis() + 5_000L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("mine");
    }

    private void tick() {
        ConfigurationSection c = cfg();
        if (c == null || !c.getBoolean("enabled", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!warned && nextReset - now <= 30_000L && nextReset - now > 0) {
            warned = true;
            broadcastNear(plugin.msg("mine-warning"));
        }
        if (now >= nextReset) {
            reset();
        }
    }

    public long secondsLeft() {
        return Math.max(0, (nextReset - System.currentTimeMillis()) / 1000);
    }

    private void broadcastNear(net.kyori.adventure.text.Component msg) {
        World w = Bukkit.getWorld(cfg().getString("world", "world"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(w) && inside(p.getLocation(), 12)) {
                p.sendMessage(msg);
            }
        }
    }

    private boolean inside(Location l, int margin) {
        List<Integer> a = cfg().getIntegerList("min");
        List<Integer> b = cfg().getIntegerList("max");
        return l.getBlockX() >= a.get(0) - margin && l.getBlockX() <= b.get(0) + margin
                && l.getBlockY() >= a.get(1) - margin && l.getBlockY() <= b.get(1) + margin
                && l.getBlockZ() >= a.get(2) - margin && l.getBlockZ() <= b.get(2) + margin;
    }

    public void reset() {
        ConfigurationSection c = cfg();
        World w = Bukkit.getWorld(c.getString("world", "world"));
        List<Integer> a = c.getIntegerList("min");
        List<Integer> b = c.getIntegerList("max");
        if (w == null || a.size() != 3 || b.size() != 3) {
            return;
        }
        // шансы руды
        List<Material> mats = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        ConfigurationSection comp = c.getConfigurationSection("composition");
        if (comp != null) {
            for (String k : comp.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null && m.isBlock()) {
                    mats.add(m);
                    total += comp.getDouble(k);
                    weights.add(total);
                }
            }
        }
        if (mats.isEmpty()) {
            return;
        }
        // игроков внутри шахты — наверх, на безопасную точку
        List<Integer> safe = c.getIntegerList("safe-spot");
        for (Player p : w.getPlayers()) {
            if (inside(p.getLocation(), 0) && safe.size() == 3) {
                p.teleport(new Location(w, safe.get(0) + 0.5, safe.get(1), safe.get(2) + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch()));
            }
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int x = a.get(0); x <= b.get(0); x++) {
            for (int y = a.get(1); y <= b.get(1); y++) {
                for (int z = a.get(2); z <= b.get(2); z++) {
                    double roll = r.nextDouble(total);
                    int i = 0;
                    while (i < weights.size() - 1 && roll >= weights.get(i)) {
                        i++;
                    }
                    w.getBlockAt(x, y, z).setType(mats.get(i), false);
                }
            }
        }
        nextReset = System.currentTimeMillis() + c.getLong("reset-minutes", 10) * 60_000L;
        warned = false;
        broadcastNear(plugin.msg("mine-reset", Map.of("minutes", c.getLong("reset-minutes", 10))));
    }
}
