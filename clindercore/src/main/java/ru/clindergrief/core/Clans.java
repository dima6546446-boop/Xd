package ru.clindergrief.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Кланы: /clan create|invite|accept|deny|leave|kick|disband|info|list|top, /cc — клановый чат. */
public final class Clans implements CommandExecutor, TabCompleter, Listener {
    static final class Clan {
        final String tag;
        String name;
        UUID leader;
        final Set<UUID> members = new LinkedHashSet<>();
        int kills;
        long created;

        Clan(String tag) {
            this.tag = tag;
        }
    }

    private static final Pattern TAG = Pattern.compile("^[A-Za-zА-Яа-яЁё0-9]{2,6}$");

    private final ClinderCore plugin;
    private final File file;
    private final Map<String, Clan> clans = new LinkedHashMap<>();
    /** Быстрый поиск клана игрока (используется и из асинхронного чата). */
    private final Map<UUID, Clan> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();

    public Clans(ClinderCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
        load();
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("clans");
        if (s == null) {
            return;
        }
        for (String key : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(key);
            if (c == null) {
                continue;
            }
            Clan clan = new Clan(c.getString("tag", key));
            clan.name = c.getString("name", clan.tag);
            clan.leader = UUID.fromString(c.getString("leader"));
            for (String m : c.getStringList("members")) {
                clan.members.add(UUID.fromString(m));
            }
            clan.kills = c.getInt("kills");
            clan.created = c.getLong("created");
            put(clan);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Clan c : clans.values()) {
            String p = "clans." + c.tag.toLowerCase() + ".";
            y.set(p + "tag", c.tag);
            y.set(p + "name", c.name);
            y.set(p + "leader", c.leader.toString());
            y.set(p + "members", c.members.stream().map(UUID::toString).toList());
            y.set(p + "kills", c.kills);
            y.set(p + "created", c.created);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить clans.yml", e);
        }
    }

    private void put(Clan c) {
        clans.put(c.tag.toLowerCase(), c);
        for (UUID m : c.members) {
            byPlayer.put(m, c);
        }
    }

    public Clan of(UUID player) {
        return byPlayer.get(player);
    }

    public String tagOf(UUID player) {
        Clan c = byPlayer.get(player);
        return c == null ? "" : Text.fill(plugin.getConfig().getString("clans.tag-format", "&8[&6{tag}&8] "), Map.of("tag", c.tag));
    }

    public List<Clan> top() {
        List<Clan> list = new ArrayList<>(clans.values());
        list.sort((a, b) -> Integer.compare(b.kills, a.kills));
        return list;
    }

    private static String name(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "?" : op.getName();
    }

    private void tell(Clan c, Component msg) {
        for (UUID m : c.members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) {
                p.sendMessage(msg);
            }
        }
    }

    // ------------------------------------------------------------ команды
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (command.getName().equalsIgnoreCase("cc")) {
            clanChat(p, String.join(" ", args));
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        Clan mine = of(p.getUniqueId());
        switch (sub) {
            case "create" -> create(p, args, mine);
            case "invite" -> {
                if (!leader(p, mine)) {
                    return true;
                }
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) {
                    p.sendMessage(plugin.msg("player-not-found"));
                } else if (of(t.getUniqueId()) != null) {
                    p.sendMessage(plugin.msg("clan-target-in-clan"));
                } else if (mine.members.size() >= plugin.getConfig().getInt("clans.max-members", 15)) {
                    p.sendMessage(plugin.msg("clan-full"));
                } else {
                    invites.put(t.getUniqueId(), mine.tag.toLowerCase());
                    p.sendMessage(plugin.msg("clan-invited", Map.of("player", t.getName())));
                    t.sendMessage(plugin.msg("clan-invite", Map.of("tag", mine.tag, "name", mine.name, "player", p.getName())));
                }
            }
            case "accept" -> {
                String key = invites.remove(p.getUniqueId());
                Clan c = key == null ? null : clans.get(key);
                if (c == null) {
                    p.sendMessage(plugin.msg("clan-no-invite"));
                } else if (mine != null) {
                    p.sendMessage(plugin.msg("clan-already"));
                } else {
                    c.members.add(p.getUniqueId());
                    byPlayer.put(p.getUniqueId(), c);
                    save();
                    tell(c, plugin.msg("clan-joined", Map.of("player", p.getName(), "tag", c.tag)));
                }
            }
            case "deny" -> {
                invites.remove(p.getUniqueId());
                p.sendMessage(plugin.msg("clan-denied"));
            }
            case "leave" -> {
                if (mine == null) {
                    p.sendMessage(plugin.msg("clan-none"));
                } else if (mine.leader.equals(p.getUniqueId())) {
                    p.sendMessage(plugin.msg("clan-leader-leave"));
                } else {
                    mine.members.remove(p.getUniqueId());
                    byPlayer.remove(p.getUniqueId());
                    save();
                    p.sendMessage(plugin.msg("clan-left", Map.of("tag", mine.tag)));
                    tell(mine, plugin.msg("clan-member-left", Map.of("player", p.getName())));
                }
            }
            case "kick" -> {
                if (!leader(p, mine) || args.length < 2) {
                    return true;
                }
                UUID target = mine.members.stream().filter(m -> name(m).equalsIgnoreCase(args[1])).findFirst().orElse(null);
                if (target == null || target.equals(p.getUniqueId())) {
                    p.sendMessage(plugin.msg("player-not-found"));
                } else {
                    mine.members.remove(target);
                    byPlayer.remove(target);
                    save();
                    tell(mine, plugin.msg("clan-kicked", Map.of("player", name(target))));
                    Player t = Bukkit.getPlayer(target);
                    if (t != null) {
                        t.sendMessage(plugin.msg("clan-you-kicked", Map.of("tag", mine.tag)));
                    }
                }
            }
            case "disband" -> {
                if (!leader(p, mine)) {
                    return true;
                }
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    p.sendMessage(plugin.msg("clan-disband-confirm"));
                    return true;
                }
                tell(mine, plugin.msg("clan-disbanded", Map.of("tag", mine.tag)));
                clans.remove(mine.tag.toLowerCase());
                mine.members.forEach(byPlayer::remove);
                save();
            }
            case "info" -> {
                Clan c = args.length > 1 ? clans.get(args[1].toLowerCase()) : mine;
                if (c == null) {
                    p.sendMessage(plugin.msg("clan-not-found"));
                    return true;
                }
                List<String> names = new ArrayList<>();
                for (UUID m : c.members) {
                    names.add((Bukkit.getPlayer(m) != null ? "&a" : "&7") + name(m));
                }
                p.sendMessage(plugin.msg("clan-info", Map.of("tag", c.tag, "name", c.name, "leader", name(c.leader),
                        "count", c.members.size(), "kills", c.kills, "members", String.join("&8, ", names))));
            }
            case "list", "top" -> {
                p.sendMessage(plugin.msg("clan-top-header"));
                int n = 1;
                for (Clan c : top().subList(0, Math.min(10, clans.size()))) {
                    p.sendMessage(Text.of(Text.fill(plugin.raw("clan-top-line"),
                            Map.of("n", n++, "tag", c.tag, "name", c.name, "kills", c.kills, "count", c.members.size()))));
                }
            }
            default -> {
                for (String line : plugin.getConfig().getStringList("clans.help")) {
                    p.sendMessage(Text.of(line));
                }
            }
        }
        return true;
    }

    private boolean leader(Player p, Clan mine) {
        if (mine == null) {
            p.sendMessage(plugin.msg("clan-none"));
            return false;
        }
        if (!mine.leader.equals(p.getUniqueId())) {
            p.sendMessage(plugin.msg("clan-not-leader"));
            return false;
        }
        return true;
    }

    private void create(Player p, String[] args, Clan mine) {
        if (mine != null) {
            p.sendMessage(plugin.msg("clan-already"));
            return;
        }
        if (args.length < 2 || !TAG.matcher(args[1]).matches()) {
            p.sendMessage(plugin.msg("clan-bad-tag"));
            return;
        }
        if (clans.containsKey(args[1].toLowerCase())) {
            p.sendMessage(plugin.msg("clan-tag-taken"));
            return;
        }
        double cost = plugin.getConfig().getDouble("clans.create-cost", 5000);
        if (cost > 0 && (!plugin.economy().has(p, cost) || !plugin.economy().withdrawPlayer(p, cost).transactionSuccess())) {
            p.sendMessage(plugin.msg("clan-no-money", Map.of("cost", Text.money(cost))));
            return;
        }
        Clan c = new Clan(args[1]);
        c.name = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : args[1];
        if (c.name.length() > 24) {
            c.name = c.name.substring(0, 24);
        }
        c.leader = p.getUniqueId();
        c.members.add(p.getUniqueId());
        c.created = System.currentTimeMillis();
        put(c);
        save();
        Bukkit.broadcast(plugin.msg("clan-created", Map.of("player", p.getName(), "tag", c.tag, "name", c.name)));
    }

    private void clanChat(Player p, String message) {
        Clan c = of(p.getUniqueId());
        if (c == null) {
            p.sendMessage(plugin.msg("clan-none"));
            return;
        }
        if (message.isBlank()) {
            p.sendMessage(plugin.msg("clan-chat-usage"));
            return;
        }
        tell(c, Text.of(Text.fill(plugin.raw("clan-chat-format"), Map.of("tag", c.tag, "player", p.getName())))
                .append(Component.text(message)));
    }

    // ------------------------------------------------------------ события
    /** Тег клана перед строкой чата: оборачиваем итоговый рендерер (EssentialsChat задаёт его раньше). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        String tag = tagOf(e.getPlayer().getUniqueId());
        if (tag.isEmpty()) {
            return;
        }
        ChatRenderer inner = e.renderer();
        Component prefix = Text.of(tag);
        e.renderer((source, displayName, message, viewer) -> prefix.append(inner.render(source, displayName, message, viewer)));
    }

    /** Нет урона по соклановцам. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = e.getDamager() instanceof Player p ? p
                : e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p2 ? p2 : null;
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        Clan a = of(attacker.getUniqueId());
        if (a != null && a == of(victim.getUniqueId()) && !plugin.getConfig().getBoolean("clans.friendly-fire", false)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        Clan k = of(killer.getUniqueId());
        if (k != null && k != of(e.getEntity().getUniqueId())) {
            k.kills++;
            save();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("clan") && args.length == 1) {
            return List.of("create", "invite", "accept", "deny", "leave", "kick", "disband", "info", "top").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
