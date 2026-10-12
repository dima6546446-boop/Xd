package ru.clindergrief.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Аукцион /ah: выставить предмет из руки за цену, купить чужой лот (Shift+ЛКМ),
 * снять свой (ПКМ). Просроченные и снятые лоты попадают в хранилище /ah storage.
 */
public final class Auction implements CommandExecutor, TabCompleter, Listener {
    record Lot(String id, UUID seller, String sellerName, double price, ItemStack item, long created, long expires) {
    }

    private enum View { MARKET, STORAGE }

    private static final class Holder implements InventoryHolder {
        final View view;
        final int page;
        final List<String> ids = new ArrayList<>();
        Inventory inv;

        Holder(View view, int page) {
            this.view = view;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ClinderCore plugin;
    private final File file;
    private final Map<String, Lot> lots = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> storage = new HashMap<>();

    public Auction(ClinderCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auction.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 1200L, 1200L);
    }

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("auction");
    }

    // ------------------------------------------------------------ хранение
    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ls = y.getConfigurationSection("lots");
        if (ls != null) {
            for (String id : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(id);
                ItemStack item = s == null ? null : s.getItemStack("item");
                if (item == null) {
                    continue;
                }
                lots.put(id, new Lot(id, UUID.fromString(s.getString("seller")), s.getString("seller-name", "?"),
                        s.getDouble("price"), item, s.getLong("created"), s.getLong("expires")));
            }
        }
        ConfigurationSection st = y.getConfigurationSection("storage");
        if (st != null) {
            for (String k : st.getKeys(false)) {
                List<ItemStack> items = new ArrayList<>();
                for (Object o : st.getList(k, List.of())) {
                    if (o instanceof ItemStack is) {
                        items.add(is);
                    }
                }
                storage.put(UUID.fromString(k), items);
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Lot l : lots.values()) {
            String p = "lots." + l.id() + ".";
            y.set(p + "seller", l.seller().toString());
            y.set(p + "seller-name", l.sellerName());
            y.set(p + "price", l.price());
            y.set(p + "item", l.item());
            y.set(p + "created", l.created());
            y.set(p + "expires", l.expires());
        }
        storage.forEach((k, v) -> {
            if (!v.isEmpty()) {
                y.set("storage." + k, v);
            }
        });
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить auction.yml", e);
        }
    }

    private void expire() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Iterator<Lot> it = lots.values().iterator(); it.hasNext(); ) {
            Lot l = it.next();
            if (l.expires() <= now) {
                it.remove();
                storage.computeIfAbsent(l.seller(), k -> new ArrayList<>()).add(l.item());
                changed = true;
                Player p = Bukkit.getPlayer(l.seller());
                if (p != null) {
                    p.sendMessage(plugin.msg("ah-expired"));
                }
            }
        }
        if (changed) {
            save();
        }
    }

    // ------------------------------------------------------------ команды
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (args.length == 0) {
            open(player, View.MARKET, 0);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "sell", "продать" -> sell(player, args);
            case "storage", "хранилище" -> open(player, View.STORAGE, 0);
            default -> player.sendMessage(plugin.msg("ah-usage"));
        }
        return true;
    }

    private void sell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.msg("ah-usage"));
            return;
        }
        if (plugin.combat() != null && plugin.combat().inCombat(player)) {
            player.sendMessage(plugin.msg("combat-blocked"));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            player.sendMessage(plugin.msg("ah-empty-hand"));
            return;
        }
        double price;
        try {
            price = Math.floor(Double.parseDouble(args[1].replace(',', '.')));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.msg("ah-usage"));
            return;
        }
        double min = cfg().getDouble("min-price", 1);
        double max = cfg().getDouble("max-price", 100_000_000);
        if (!(price >= min && price <= max)) {
            player.sendMessage(plugin.msg("ah-price-range", Map.of("min", Text.money(min), "max", Text.money(max))));
            return;
        }
        int limit = cfg().getInt("max-lots-per-player", 7);
        long mine = lots.values().stream().filter(l -> l.seller().equals(player.getUniqueId())).count();
        if (mine >= limit) {
            player.sendMessage(plugin.msg("ah-limit", Map.of("limit", limit)));
            return;
        }
        ItemStack item = hand.clone();
        player.getInventory().setItemInMainHand(null);
        long now = System.currentTimeMillis();
        long expires = now + cfg().getLong("duration-hours", 48) * 3_600_000L;
        String id = Long.toString(now, 36) + Integer.toString((int) (Math.random() * 1296), 36);
        lots.put(id, new Lot(id, player.getUniqueId(), player.getName(), price, item, now, expires));
        save();
        player.sendMessage(plugin.msg("ah-listed", Map.of("amount", item.getAmount(), "price", Text.money(price))));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
    }

    // ------------------------------------------------------------ интерфейс
    private void open(Player player, View view, int page) {
        Holder h = new Holder(view, page);
        String title = view == View.MARKET ? plugin.raw("ah-title") : plugin.raw("ah-storage-title");
        Inventory inv = Bukkit.createInventory(h, 54, Text.of(title + " &8(" + (page + 1) + ")"));
        h.inv = inv;
        long now = System.currentTimeMillis();
        if (view == View.MARKET) {
            List<Lot> list = new ArrayList<>(lots.values());
            list.removeIf(l -> l.expires() <= now);
            list.sort(Comparator.comparingLong(Lot::created).reversed());
            int from = page * 45;
            for (int i = from; i < Math.min(from + 45, list.size()); i++) {
                Lot l = list.get(i);
                ItemStack shown = l.item().clone();
                ItemMeta meta = shown.getItemMeta();
                List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Text.item("&8&m                        "));
                lore.add(Text.item("&7Цена: &a" + Text.money(l.price()) + "$"));
                lore.add(Text.item("&7Продавец: &f" + l.sellerName()));
                lore.add(Text.item("&7Истекает через: &f" + Text.duration((l.expires() - now) / 1000)));
                lore.add(Component.empty());
                lore.add(Text.item(l.seller().equals(player.getUniqueId()) ? "&cПКМ — снять с продажи" : "&eShift + ЛКМ — купить"));
                meta.lore(lore);
                shown.setItemMeta(meta);
                inv.setItem(i - from, shown);
                h.ids.add(l.id());
            }
            nav(inv, page > 0, from + 45 < list.size());
            inv.setItem(49, button(Material.CHEST, "&6Хранилище", "&7Снятые и просроченные лоты", "&7Нажмите, чтобы забрать"));
            inv.setItem(47, button(Material.SUNFLOWER, "&eВаш баланс: &a" + Text.money(plugin.economy().getBalance(player)) + "$",
                    "&7Выставить предмет из руки:", "&f/ah sell <цена>"));
        } else {
            List<ItemStack> items = storage.getOrDefault(player.getUniqueId(), List.of());
            int from = page * 45;
            for (int i = from; i < Math.min(from + 45, items.size()); i++) {
                inv.setItem(i - from, items.get(i).clone());
                h.ids.add(String.valueOf(i));
            }
            nav(inv, page > 0, from + 45 < items.size());
            inv.setItem(49, button(Material.ARROW, "&cНазад к аукциону"));
        }
        player.openInventory(inv);
    }

    private static void nav(Inventory inv, boolean prev, boolean next) {
        for (int s = 45; s < 54; s++) {
            inv.setItem(s, button(Material.GRAY_STAINED_GLASS_PANE, "&r"));
        }
        if (prev) {
            inv.setItem(45, button(Material.ARROW, "&eПредыдущая страница"));
        }
        if (next) {
            inv.setItem(53, button(Material.ARROW, "&eСледующая страница"));
        }
    }

    private static ItemStack button(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Text.item(s));
        }
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot == 45 && h.page > 0) {
            open(player, h.view, h.page - 1);
            return;
        }
        if (slot == 53) {
            open(player, h.view, h.page + 1);
            return;
        }
        if (slot == 49) {
            open(player, h.view == View.MARKET ? View.STORAGE : View.MARKET, 0);
            return;
        }
        if (slot >= h.ids.size()) {
            return;
        }
        if (h.view == View.STORAGE) {
            takeFromStorage(player, h.page * 45 + slot);
            open(player, View.STORAGE, h.page);
            return;
        }
        Lot lot = lots.get(h.ids.get(slot));
        if (lot == null || lot.expires() <= System.currentTimeMillis()) {
            player.sendMessage(plugin.msg("ah-gone"));
            open(player, View.MARKET, h.page);
            return;
        }
        if (lot.seller().equals(player.getUniqueId())) {
            if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                lots.remove(lot.id());
                storage.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(lot.item());
                save();
                player.sendMessage(plugin.msg("ah-removed"));
                open(player, View.MARKET, h.page);
            }
            return;
        }
        if (e.getClick() != ClickType.SHIFT_LEFT) {
            player.sendMessage(plugin.msg("ah-confirm-hint"));
            return;
        }
        buy(player, lot);
        open(player, View.MARKET, h.page);
    }

    private void buy(Player player, Lot lot) {
        if (plugin.combat() != null && plugin.combat().inCombat(player)) {
            player.sendMessage(plugin.msg("combat-blocked"));
            return;
        }
        if (!plugin.economy().has(player, lot.price())
                || !plugin.economy().withdrawPlayer(player, lot.price()).transactionSuccess()) {
            player.sendMessage(plugin.msg("ah-no-money"));
            return;
        }
        lots.remove(lot.id());
        double tax = Math.floor(lot.price() * cfg().getDouble("tax-percent", 5) / 100.0);
        OfflinePlayer seller = Bukkit.getOfflinePlayer(lot.seller());
        plugin.economy().depositPlayer(seller, lot.price() - tax);
        save();
        for (ItemStack left : player.getInventory().addItem(lot.item().clone()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        player.sendMessage(plugin.msg("ah-bought", Map.of("price", Text.money(lot.price()), "seller", lot.sellerName())));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        Player online = seller.getPlayer();
        if (online != null) {
            online.sendMessage(plugin.msg("ah-sold", Map.of("buyer", player.getName(),
                    "price", Text.money(lot.price() - tax), "tax", Text.money(tax))));
        }
    }

    private void takeFromStorage(Player player, int index) {
        List<ItemStack> items = storage.get(player.getUniqueId());
        if (items == null || index >= items.size()) {
            return;
        }
        ItemStack item = items.remove(index);
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        save();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("sell", "storage").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }

    public int lotCount() {
        return lots.size();
    }
}
