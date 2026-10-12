package ru.clindergrief.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** /tags — выбор титула; титул показывается суффиксом LuckPerms (чат, TAB, скорборд). */
public final class Tags implements CommandExecutor, Listener {
    record Tag(String id, String display, int hours, String permission, Material icon) {
    }

    private static final class Holder implements InventoryHolder {
        final List<String> slots = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ClinderCore plugin;
    private final Map<String, Tag> tags = new LinkedHashMap<>();

    public Tags(ClinderCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        tags.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("tags.list");
        if (s == null) {
            return;
        }
        for (String id : s.getKeys(false)) {
            ConfigurationSection t = s.getConfigurationSection(id);
            if (t == null) {
                continue;
            }
            Material icon = Material.matchMaterial(t.getString("icon", "NAME_TAG"));
            tags.put(id, new Tag(id, t.getString("display", id), t.getInt("hours", 0),
                    t.getString("permission"), icon == null ? Material.NAME_TAG : icon));
        }
    }

    public static double hoursPlayed(Player p) {
        return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
    }

    private boolean unlocked(Player p, Tag t) {
        if (t.permission() != null && !p.hasPermission(t.permission())) {
            return false;
        }
        return hoursPlayed(p) >= t.hours();
    }

    public String displayOf(Player p) {
        String id = plugin.store().tag(p.getUniqueId());
        Tag t = id == null ? null : tags.get(id);
        return t == null ? "" : t.display();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("off")) {
            apply(player, null);
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        Holder holder = new Holder();
        int rows = Math.min(6, Math.max(2, (tags.size() + 1 + 8) / 9 + 1));
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.of(plugin.raw("tags-gui-title")));
        holder.inv = inv;
        String current = plugin.store().tag(player.getUniqueId());
        double have = hoursPlayed(player);
        int slot = 0;
        for (Tag t : tags.values()) {
            if (slot >= inv.getSize() - 9) {
                break;
            }
            boolean open = unlocked(player, t);
            ItemStack item = new ItemStack(open ? t.icon() : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.item(t.display()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            if (t.permission() != null) {
                lore.add(Text.item("&7Для привилегии"));
            }
            if (t.hours() > 0) {
                lore.add(Text.item("&7Наиграть: &f" + t.hours() + " ч &8(у вас " + String.format("%.1f", have) + " ч)"));
            }
            lore.add(Component.empty());
            lore.add(Text.item(t.id().equals(current) ? "&a✔ Выбран" : open ? "&eНажмите, чтобы выбрать" : "&cЗакрыто"));
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            holder.slots.add(t.id());
            slot++;
        }
        ItemStack off = new ItemStack(Material.BARRIER);
        ItemMeta m = off.getItemMeta();
        m.displayName(Text.item("&cСнять титул"));
        off.setItemMeta(m);
        inv.setItem(inv.getSize() - 5, off);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        int raw = e.getRawSlot();
        if (raw == e.getInventory().getSize() - 5) {
            apply(player, null);
            player.closeInventory();
            return;
        }
        if (raw < 0 || raw >= holder.slots.size()) {
            return;
        }
        Tag t = tags.get(holder.slots.get(raw));
        if (t == null) {
            return;
        }
        if (!unlocked(player, t)) {
            if (t.permission() != null && !player.hasPermission(t.permission())) {
                player.sendMessage(plugin.msg("tag-locked-perm"));
            } else {
                player.sendMessage(plugin.msg("tag-locked-hours",
                        Map.of("hours", t.hours(), "have", String.format("%.1f", hoursPlayed(player)))));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        apply(player, t);
        player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    /** Повторная проверка при входе: если привилегия закончилась — титул снимается. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String id = plugin.store().tag(p.getUniqueId());
        if (id == null) {
            return;
        }
        Tag t = tags.get(id);
        if (t == null || !unlocked(p, t)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> apply(p, null), 40L);
        }
    }

    private void apply(Player player, Tag t) {
        int prio = plugin.getConfig().getInt("tags.suffix-priority", 50);
        String name = player.getName();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " meta removesuffix " + prio);
        if (t == null) {
            plugin.store().setTag(player.getUniqueId(), null);
            player.sendMessage(plugin.msg("tag-removed"));
            return;
        }
        String suffix = " " + t.display().replace("\"", "");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " meta setsuffix " + prio + " \"" + suffix + "\"");
        plugin.store().setTag(player.getUniqueId(), t.id());
        player.sendMessage(plugin.msg("tag-selected", Map.of("tag", t.display())));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }
}
