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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Кейсы: /cases — открыть меню. Ключи виртуальные (хранятся в data.yml).
 * Ключи выдаются за задания, серию /daily и командой /cases give (для магазина доната).
 * Шансы каждой награды показываются игроку (ПКМ по кейсу).
 */
public final class Cases implements CommandExecutor, TabCompleter, Listener {
    record Reward(String name, Material icon, int amount, double weight, double money, Material item,
                  List<String> commands, Map<String, Integer> keys, String unlessPermission,
                  Map<String, Integer> fallbackKeys, boolean broadcast) {
    }

    record CaseType(String id, String name, Material icon, List<String> description, List<Reward> rewards) {
        double totalWeight() {
            return rewards.stream().mapToDouble(Reward::weight).sum();
        }
    }

    private enum View { MAIN, PREVIEW, SPIN }

    private static final class Holder implements InventoryHolder {
        final View view;
        final List<String> ids = new ArrayList<>();
        Inventory inv;

        Holder(View view) {
            this.view = view;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ClinderCore plugin;
    private final Map<String, CaseType> cases = new LinkedHashMap<>();

    public Cases(ClinderCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cases.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("cases.types");
        if (s == null) {
            return;
        }
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            List<Reward> rewards = new ArrayList<>();
            for (Map<?, ?> r : c.getMapList("rewards")) {
                rewards.add(new Reward(
                        str(r.get("name"), "?"),
                        mat(r.get("icon"), Material.PAPER),
                        r.get("amount") instanceof Number n ? n.intValue() : 1,
                        r.get("weight") instanceof Number n ? n.doubleValue() : 1,
                        r.get("money") instanceof Number n ? n.doubleValue() : 0,
                        r.get("item") == null ? null : mat(r.get("item"), null),
                        r.get("commands") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of(),
                        intMap(r.get("keys")),
                        r.get("unless-permission") == null ? null : String.valueOf(r.get("unless-permission")),
                        intMap(r.get("fallback-keys")),
                        Boolean.TRUE.equals(r.get("broadcast"))));
            }
            cases.put(id, new CaseType(id, c.getString("name", id), mat(c.getString("icon"), Material.CHEST),
                    c.getStringList("description"), rewards));
        }
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static Material mat(Object o, Material def) {
        Material m = o == null ? null : Material.matchMaterial(String.valueOf(o));
        return m == null ? def : m;
    }

    private static Map<String, Integer> intMap(Object o) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) {
            m.forEach((k, v) -> out.put(String.valueOf(k), v instanceof Number n ? n.intValue() : 1));
        }
        return out;
    }

    // ------------------------------------------------------------ ключи
    public int keys(UUID id, String caseId) {
        return plugin.store().raw().getInt("players." + id + ".keys." + caseId, 0);
    }

    public void addKeys(UUID id, String caseId, int amount) {
        plugin.store().raw().set("players." + id + ".keys." + caseId, Math.max(0, keys(id, caseId) + amount));
        plugin.store().save();
    }

    public String caseName(String id) {
        CaseType c = cases.get(id);
        return c == null ? id : c.name();
    }

    // ------------------------------------------------------------ команды
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 4 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("clindercore.cases.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (!cases.containsKey(args[2])) {
                sender.sendMessage(plugin.msg("cases-unknown"));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.msg("cases-usage"));
                return true;
            }
            addKeys(target.getUniqueId(), args[2], amount);
            sender.sendMessage(plugin.msg("cases-given", Map.of("player", args[1], "amount", amount, "case", caseName(args[2]))));
            Player online = target.getPlayer();
            if (online != null) {
                online.sendMessage(plugin.msg("cases-received", Map.of("amount", amount, "case", caseName(args[2]))));
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("cases-usage"));
            return true;
        }
        openMain(p);
        return true;
    }

    private static ItemStack icon(Material m, String name, List<String> lore) {
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

    private void openMain(Player p) {
        Holder h = new Holder(View.MAIN);
        Inventory inv = Bukkit.createInventory(h, 27, Text.of(plugin.raw("cases-title")));
        h.inv = inv;
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, icon(Material.BLACK_STAINED_GLASS_PANE, "&r", List.of()));
        }
        int[] slots = {11, 13, 15, 10, 12, 14, 16};
        int i = 0;
        for (CaseType c : cases.values()) {
            if (i >= slots.length) {
                break;
            }
            List<String> lore = new ArrayList<>(c.description());
            lore.add("");
            lore.add("&7Ваших ключей: &e" + keys(p.getUniqueId(), c.id()));
            lore.add("");
            lore.add("&aЛКМ &7— открыть");
            lore.add("&eПКМ &7— содержимое и шансы");
            inv.setItem(slots[i], icon(c.icon(), c.name(), lore));
            while (h.ids.size() <= slots[i]) {
                h.ids.add(null);
            }
            h.ids.set(slots[i], c.id());
            i++;
        }
        p.openInventory(inv);
    }

    private void openPreview(Player p, CaseType c) {
        Holder h = new Holder(View.PREVIEW);
        int rows = Math.min(6, (c.rewards().size() + 8) / 9 + 1);
        Inventory inv = Bukkit.createInventory(h, rows * 9, Text.of("&8Содержимое: " + c.name()));
        h.inv = inv;
        double total = c.totalWeight();
        int slot = 0;
        for (Reward r : c.rewards()) {
            if (slot >= inv.getSize() - 9) {
                break;
            }
            ItemStack it = icon(r.icon(), r.name(), List.of("", String.format("&7Шанс: &e%.2f%%", r.weight() / total * 100)));
            it.setAmount(Math.max(1, Math.min(64, r.amount())));
            inv.setItem(slot++, it);
        }
        inv.setItem(inv.getSize() - 5, icon(Material.ARROW, "&cНазад", List.of()));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        if (h.view == View.PREVIEW) {
            if (e.getRawSlot() == e.getInventory().getSize() - 5) {
                openMain(p);
            }
            return;
        }
        if (h.view != View.MAIN || e.getRawSlot() >= h.ids.size() || h.ids.get(e.getRawSlot()) == null) {
            return;
        }
        CaseType c = cases.get(h.ids.get(e.getRawSlot()));
        if (c == null) {
            return;
        }
        if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
            openPreview(p, c);
            return;
        }
        if (keys(p.getUniqueId(), c.id()) <= 0) {
            p.sendMessage(plugin.msg("cases-no-keys", Map.of("case", c.name())));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        open(p, c);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ открытие
    private Reward roll(CaseType c) {
        double r = ThreadLocalRandom.current().nextDouble(c.totalWeight());
        for (Reward rw : c.rewards()) {
            r -= rw.weight();
            if (r < 0) {
                return rw;
            }
        }
        return c.rewards().get(c.rewards().size() - 1);
    }

    private void open(Player p, CaseType c) {
        if (c.rewards().isEmpty()) {
            return;
        }
        addKeys(p.getUniqueId(), c.id(), -1);
        Reward win = roll(c);
        // награда выдаётся сразу — анимация лишь показывает результат (закрытие окна ничего не ломает)
        give(p, c, win);

        Holder h = new Holder(View.SPIN);
        Inventory inv = Bukkit.createInventory(h, 27, Text.of("&8Открытие: " + c.name()));
        h.inv = inv;
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, icon(i == 4 || i == 22 ? Material.HOPPER : Material.BLACK_STAINED_GLASS_PANE, "&r", List.of()));
        }
        p.openInventory(inv);
        List<Reward> strip = new ArrayList<>();
        int steps = 34;
        for (int i = 0; i < steps + 9; i++) {
            strip.add(roll(c));
        }
        strip.set(steps + 4, win);   // на шаге steps в центре (слот 13) окажется выигрыш
        new BukkitRunnable() {
            int step;
            int wait;

            @Override
            public void run() {
                if (!p.isOnline() || p.getOpenInventory().getTopInventory() != inv) {
                    cancel();
                    return;
                }
                if (wait-- > 0) {
                    return;
                }
                for (int i = 0; i < 9; i++) {
                    Reward r = strip.get(step + i);
                    inv.setItem(9 + i, icon(r.icon(), r.name(), List.of()));
                }
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
                if (step >= steps) {
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    cancel();
                    return;
                }
                step++;
                wait = step < 18 ? 0 : step < 26 ? 1 : step < 31 ? 2 : 4;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void give(Player p, CaseType c, Reward r) {
        if (r.unlessPermission() != null && p.hasPermission(r.unlessPermission())) {
            r.fallbackKeys().forEach((k, v) -> addKeys(p.getUniqueId(), k, v));
            p.sendMessage(plugin.msg("cases-duplicate", Map.of("reward", r.name())));
            return;
        }
        if (r.money() > 0) {
            plugin.economy().depositPlayer(p, r.money());
        }
        if (r.item() != null) {
            for (ItemStack left : p.getInventory().addItem(new ItemStack(r.item(), r.amount())).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
        for (String cmd : r.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }
        r.keys().forEach((k, v) -> addKeys(p.getUniqueId(), k, v));
        p.sendMessage(plugin.msg("cases-won", Map.of("reward", r.name(), "case", c.name())));
        if (r.broadcast()) {
            Bukkit.broadcast(plugin.msg("cases-broadcast", Map.of("player", p.getName(), "reward", r.name(), "case", c.name())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("clindercore.cases.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("give");
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3) {
            return new ArrayList<>(cases.keySet());
        }
        return List.of();
    }
}
