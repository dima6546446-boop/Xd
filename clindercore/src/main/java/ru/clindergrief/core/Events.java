package ru.clindergrief.core;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ивент «Мистический сундук»: раз в N минут в случайном месте мира появляется сундук с лутом.
 * Координаты объявляются в чате и боссбаре, сундук открывается после отсчёта, затем исчезает.
 */
public final class Events implements CommandExecutor, TabCompleter, Listener {
    private final ClinderCore plugin;
    private final BossBar bar = BossBar.bossBar(Text.of(""), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
    private Location chest;
    private long unlockAt;
    private long removeAt;
    private long nextAt;
    private boolean announcedOpen;

    public Events(ClinderCore plugin) {
        this.plugin = plugin;
        nextAt = System.currentTimeMillis() + intervalMs();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private long intervalMs() {
        return plugin.getConfig().getLong("events.interval-minutes", 60) * 60_000L;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (chest == null) {
            if (plugin.getConfig().getBoolean("events.enabled", true) && now >= nextAt) {
                if (Bukkit.getOnlinePlayers().size() >= plugin.getConfig().getInt("events.min-players", 1)) {
                    start();
                } else {
                    nextAt = now + 60_000L;
                }
            }
            return;
        }
        if (now >= removeAt) {
            finish();
            return;
        }
        Location c = chest.clone().add(0.5, 1.2, 0.5);
        c.getWorld().spawnParticle(Particle.END_ROD, c, 12, 0.3, 4, 0.3, 0.01);
        c.getWorld().spawnParticle(Particle.WITCH, c, 8, 0.6, 0.6, 0.6, 0.02);
        String xyz = chest.getBlockX() + " " + chest.getBlockY() + " " + chest.getBlockZ();
        if (now < unlockAt) {
            long left = (unlockAt - now) / 1000 + 1;
            bar.name(Text.of(Text.fill(plugin.raw("event-bar-locked"), Map.of("xyz", xyz, "time", String.format("%d:%02d", left / 60, left % 60)))));
            bar.progress(Math.max(0f, Math.min(1f, (unlockAt - now) / (float) unlockMs())));
        } else {
            if (!announcedOpen) {
                announcedOpen = true;
                Bukkit.broadcast(plugin.msg("event-unlocked", Map.of("xyz", xyz)));
                c.getWorld().playSound(c, Sound.BLOCK_ENDER_CHEST_OPEN, 2f, 0.8f);
            }
            long left = (removeAt - now) / 1000;
            bar.name(Text.of(Text.fill(plugin.raw("event-bar-open"), Map.of("xyz", xyz, "time", Text.duration(left)))));
            bar.progress(Math.max(0f, Math.min(1f, (removeAt - now) / (float) (removeAt - unlockAt))));
        }
    }

    private long unlockMs() {
        return plugin.getConfig().getLong("events.unlock-seconds", 120) * 1000L;
    }

    public boolean start() {
        if (chest != null) {
            return false;
        }
        Location loc = findSpot();
        if (loc == null) {
            plugin.getLogger().warning("Мистический сундук: не найдено подходящее место.");
            nextAt = System.currentTimeMillis() + 120_000L;
            return false;
        }
        Block b = loc.getBlock();
        b.setType(Material.CHEST);
        if (b.getState() instanceof Chest state) {
            fill(state.getBlockInventory());
        }
        loc.clone().add(0, -1, 0).getBlock().setType(Material.CRYING_OBSIDIAN);
        chest = loc;
        long now = System.currentTimeMillis();
        unlockAt = now + unlockMs();
        removeAt = unlockAt + plugin.getConfig().getLong("events.open-minutes", 10) * 60_000L;
        announcedOpen = false;
        String xyz = loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
        Bukkit.broadcast(plugin.msg("event-start", Map.of("xyz", xyz, "seconds", unlockMs() / 1000)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
        return true;
    }

    private void finish() {
        if (chest != null) {
            Block b = chest.getBlock();
            if (b.getState() instanceof Chest state) {
                state.getBlockInventory().clear();
            }
            b.setType(Material.AIR);
            Block under = chest.clone().add(0, -1, 0).getBlock();
            if (under.getType() == Material.CRYING_OBSIDIAN) {
                under.setType(Material.OBSIDIAN);
            }
            Bukkit.broadcast(plugin.msg("event-end"));
        }
        chest = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bar);
        }
        nextAt = System.currentTimeMillis() + intervalMs();
    }

    private Location findSpot() {
        World w = Bukkit.getWorld(plugin.getConfig().getString("events.world", "world"));
        if (w == null) {
            return null;
        }
        int min = plugin.getConfig().getInt("events.min-distance", 300);
        int max = plugin.getConfig().getInt("events.max-distance", 1500);
        Location center = w.getSpawnLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 30; attempt++) {
            double ang = r.nextDouble(Math.PI * 2);
            int dist = r.nextInt(min, max + 1);
            int x = center.getBlockX() + (int) (Math.cos(ang) * dist);
            int z = center.getBlockZ() + (int) (Math.sin(ang) * dist);
            Block top = w.getHighestBlockAt(x, z);
            if (top.isLiquid() || !top.getType().isSolid() || top.getType().name().contains("LEAVES") || top.getY() < w.getSeaLevel() - 2) {
                continue;
            }
            Location loc = top.getLocation().add(0, 1, 0);
            if (claimed(loc)) {
                continue;
            }
            return loc;
        }
        return null;
    }

    /** Не ставим сундук в чужом привате GriefPrevention (без жёсткой зависимости — через рефлексию). */
    private boolean claimed(Location loc) {
        try {
            Class<?> gp = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Object instance = gp.getField("instance").get(null);
            Object dataStore = gp.getField("dataStore").get(instance);
            Class<?> claimCls = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
            Method m = dataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, claimCls);
            return m.invoke(dataStore, loc, true, null) != null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private void fill(Inventory inv) {
        List<Map<?, ?>> loot = plugin.getConfig().getMapList("events.loot");
        List<ItemStack> items = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Map<?, ?> e : loot) {
            Material m = Material.matchMaterial(String.valueOf(e.get("item")));
            double chance = e.get("chance") instanceof Number n ? n.doubleValue() : 100;
            int minA = e.get("min") instanceof Number n ? n.intValue() : 1;
            int maxA = e.get("max") instanceof Number n ? n.intValue() : minA;
            if (m != null && r.nextDouble(100) < chance) {
                items.add(new ItemStack(m, r.nextInt(minA, Math.max(minA, maxA) + 1)));
            }
        }
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots);
        for (int i = 0; i < items.size() && i < slots.size(); i++) {
            inv.setItem(slots.get(i), items.get(i));
        }
    }

    private boolean isEventChest(Block b) {
        return chest != null && b.getLocation().equals(chest.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || !isEventChest(e.getClickedBlock())) {
            return;
        }
        if (System.currentTimeMillis() < unlockAt) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.msg("event-locked", Map.of("seconds", (unlockAt - System.currentTimeMillis()) / 1000 + 1)));
        } else {
            // открыть сундук можно даже если место кто-то заприватил после появления
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.ALLOW);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (isEventChest(e.getBlock()) || (chest != null && e.getBlock().getLocation().equals(chest.clone().add(0, -1, 0).getBlock().getLocation()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isEventChest);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isEventChest);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (chest != null) {
            e.getPlayer().showBossBar(bar);
        }
    }

    public void shutdown() {
        if (chest != null) {
            finish();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("start") && sender.hasPermission("clindercore.admin")) {
            if (!start()) {
                sender.sendMessage(plugin.msg("event-cant-start"));
            }
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("stop") && sender.hasPermission("clindercore.admin")) {
            finish();
            return true;
        }
        if (chest != null) {
            sender.sendMessage(plugin.msg("event-info-active", Map.of("xyz",
                    chest.getBlockX() + " " + chest.getBlockY() + " " + chest.getBlockZ())));
        } else {
            sender.sendMessage(plugin.msg("event-info-next", Map.of("time",
                    Text.duration(Math.max(0, (nextAt - System.currentTimeMillis()) / 1000)))));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 && sender.hasPermission("clindercore.admin") ? List.of("start", "stop") : List.of();
    }
}
