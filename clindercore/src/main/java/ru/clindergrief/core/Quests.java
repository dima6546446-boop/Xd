package ru.clindergrief.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Ежедневные задания /quests (как на HolyWorld/FunTime): каждый день игроку выпадают 3 задания из пула.
 * Типы: break (сломать блоки), kill (убить мобов), fish (поймать рыбу). Награда — деньги и ключи кейсов.
 * Блоки, поставленные игроками, не засчитываются (защита от фарма «поставил-сломал»).
 */
public final class Quests implements CommandExecutor, Listener {
    record Quest(String id, String name, String type, List<String> targets, int amount, double money,
                 Map<String, Integer> keys, Material icon) {
    }

    private static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ClinderCore plugin;
    private final Map<String, Quest> pool = new LinkedHashMap<>();
    /** Недавно поставленные игроками блоки (не засчитываются в задания). */
    private final Map<Location, Long> placed = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Location, Long> eldest) {
            return size() > 50_000;
        }
    });

    public Quests(ClinderCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        pool.clear();
        for (Map<?, ?> m : plugin.getConfig().getMapList("quests.pool")) {
            String id = String.valueOf(m.get("id"));
            List<String> targets = m.get("targets") instanceof List<?> l ? l.stream().map(o -> String.valueOf(o).toUpperCase()).toList() : List.of();
            Map<String, Integer> keys = new LinkedHashMap<>();
            if (m.get("keys") instanceof Map<?, ?> km) {
                km.forEach((k, v) -> keys.put(String.valueOf(k), v instanceof Number n ? n.intValue() : 1));
            }
            Material icon = Material.matchMaterial(String.valueOf(m.get("icon")));
            pool.put(id, new Quest(id, String.valueOf(m.get("name")), String.valueOf(m.get("type")).toLowerCase(), targets,
                    m.get("amount") instanceof Number n ? n.intValue() : 1,
                    m.get("money") instanceof Number n ? n.doubleValue() : 0, keys, icon == null ? Material.PAPER : icon));
        }
    }

    private long today() {
        String tz = plugin.getConfig().getString("daily.timezone", "Europe/Moscow");
        try {
            return LocalDate.now(ZoneId.of(tz)).toEpochDay();
        } catch (Exception e) {
            return LocalDate.now().toEpochDay();
        }
    }

    private YamlConfiguration data() {
        return plugin.store().raw();
    }

    /** Задания игрока на сегодня (детерминированно по дню и UUID). */
    private List<Quest> todays(UUID id) {
        List<Quest> all = new ArrayList<>(pool.values());
        Collections.shuffle(all, new Random(today() * 31 + id.hashCode()));
        return all.subList(0, Math.min(plugin.getConfig().getInt("quests.per-day", 3), all.size()));
    }

    private String base(UUID id) {
        return "players." + id + ".quests.";
    }

    private int progress(UUID id, Quest q) {
        if (data().getLong(base(id) + "day", -1) != today()) {
            return 0;
        }
        return data().getInt(base(id) + "progress." + q.id(), 0);
    }

    private void addProgress(Player p, String type, String target) {
        UUID id = p.getUniqueId();
        long day = today();
        if (data().getLong(base(id) + "day", -1) != day) {
            data().set(base(id), null);
            data().set(base(id) + "day", day);
        }
        boolean changed = false;
        for (Quest q : todays(id)) {
            if (!q.type().equals(type) || (!q.targets().isEmpty() && !q.targets().contains(target))) {
                continue;
            }
            int cur = progress(id, q);
            if (cur >= q.amount()) {
                continue;
            }
            cur++;
            data().set(base(id) + "progress." + q.id(), cur);
            changed = true;
            if (cur >= q.amount()) {
                complete(p, q);
            } else if (cur % Math.max(1, q.amount() / 4) == 0) {
                p.sendActionBar(Text.of("&e" + q.name() + " &7— &f" + cur + "/" + q.amount()));
            }
        }
        if (changed) {
            plugin.store().save();
        }
    }

    private void complete(Player p, Quest q) {
        if (q.money() > 0) {
            plugin.economy().depositPlayer(p, q.money());
        }
        q.keys().forEach((k, v) -> plugin.cases().addKeys(p.getUniqueId(), k, v));
        p.sendMessage(plugin.msg("quest-done", Map.of("quest", q.name(), "money", Text.money(q.money()))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        boolean all = todays(p.getUniqueId()).stream().allMatch(x -> progress(p.getUniqueId(), x) >= x.amount());
        if (all) {
            Map<String, Integer> bonus = new LinkedHashMap<>();
            var sec = plugin.getConfig().getConfigurationSection("quests.all-done-keys");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    bonus.put(k, sec.getInt(k));
                }
            }
            bonus.forEach((k, v) -> plugin.cases().addKeys(p.getUniqueId(), k, v));
            p.sendMessage(plugin.msg("quest-all-done"));
        }
    }

    // ------------------------------------------------------------ события
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        placed.put(e.getBlock().getLocation(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (placed.remove(b.getLocation()) != null) {
            return;
        }
        if (b.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
            return;   // незрелый урожай не считается
        }
        addProgress(e.getPlayer(), "break", b.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null && !(e.getEntity() instanceof Player)) {
            addProgress(killer, "kill", e.getEntityType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            addProgress(e.getPlayer(), "fish", "");
        }
    }

    // ------------------------------------------------------------ меню
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Text.of(plugin.raw("quests-title")));
        h.inv = inv;
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Text.item("&r"));
        pane.setItemMeta(pm);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }
        int[] slots = {11, 13, 15, 12, 14};
        int i = 0;
        for (Quest q : todays(p.getUniqueId())) {
            int cur = progress(p.getUniqueId(), q);
            boolean done = cur >= q.amount();
            ItemStack it = new ItemStack(done ? Material.LIME_DYE : q.icon());
            ItemMeta m = it.getItemMeta();
            m.displayName(Text.item((done ? "&a✔ " : "&e") + q.name()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            int bars = 20;
            int filled = (int) Math.round(bars * Math.min(1.0, cur / (double) q.amount()));
            lore.add(Text.item("&a" + "|".repeat(filled) + "&8" + "|".repeat(bars - filled) + " &f" + Math.min(cur, q.amount()) + "/" + q.amount()));
            lore.add(Component.empty());
            lore.add(Text.item("&7Награда: &a" + Text.money(q.money()) + "$"));
            q.keys().forEach((k, v) -> lore.add(Text.item("&7+ ключ: " + plugin.cases().caseName(k) + " &7x" + v)));
            lore.add(Component.empty());
            lore.add(Text.item(done ? "&aВыполнено!" : "&7Задания обновляются каждый день"));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(slots[i++], it);
        }
        p.openInventory(inv);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
