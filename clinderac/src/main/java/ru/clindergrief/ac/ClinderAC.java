package ru.clindergrief.ac;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClinderAC — собственный серверный античит ClinderGrief.
 * Эвристики с «очками нарушений» (VL): уведомления персоналу, откат позиции, отмена ударов/блоков, кик.
 */
public final class ClinderAC extends JavaPlugin implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final Set<UUID> alertsOff = new HashSet<>();
    private final Set<UUID> punished = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new MovementChecks(this), this);
        getServer().getPluginManager().registerEvents(new CombatChecks(this), this);
        getServer().getPluginManager().registerEvents(new WorldChecks(this), this);
        for (Player p : Bukkit.getOnlinePlayers()) {
            data.put(p.getUniqueId(), new PlayerData(p));
        }
        Bukkit.getScheduler().runTaskTimer(this, this::decay, 200L, 200L);
        getLogger().info("ClinderAC запущен: полёт, скорость, подмена земли, таймер, дальность, киллаура, автокликер, быстрая копка/постановка, X-Ray.");
    }

    public PlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> new PlayerData(p));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        data.put(e.getPlayer().getUniqueId(), new PlayerData(e.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        data.remove(e.getPlayer().getUniqueId());
        punished.remove(e.getPlayer().getUniqueId());
    }

    /** Общие исключения: творческий режим, разрешённый полёт, право обхода, только что зашёл. */
    public boolean exempt(Player p) {
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || p.getAllowFlight() || p.isFlying()) {
            return true;
        }
        if (p.hasPermission("clinderac.bypass")) {
            return true;
        }
        return System.currentTimeMillis() - data(p).joinedAt < 3000;
    }

    public ConfigurationSection check(String name) {
        ConfigurationSection s = getConfig().getConfigurationSection("checks." + name);
        return s == null ? getConfig().createSection("checks." + name) : s;
    }

    public boolean enabled(String check) {
        return check(check).getBoolean("enabled", true);
    }

    /** Добавить VL проверке и выполнить действия (уведомление, кик, наказание). Возвращает текущий VL. */
    public double flag(Player p, String check, double amount, String info) {
        PlayerData d = data(p);
        double vl = d.vl.merge(check, amount, Double::sum);
        ConfigurationSection c = check(check);
        double alertVl = c.getDouble("alert-vl", 10);
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[debug] " + p.getName() + " " + check + " +" + String.format("%.1f", amount) + " (" + info + ") VL " + String.format("%.1f", vl));
        }
        if (vl >= alertVl && d.shouldAlert(check)) {
            String text = fill(getConfig().getString("messages.alert", ""), Map.of(
                    "player", p.getName(), "check", check, "info", info, "vl", String.format("%.1f", vl)));
            Component msg = LEGACY.deserialize(getConfig().getString("prefix", "") + text);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("clinderac.alerts") && !alertsOff.contains(staff.getUniqueId())) {
                    staff.sendMessage(msg);
                }
            }
            getLogger().info(p.getName() + " " + check + " (" + info + ") VL " + String.format("%.1f", vl));
            log(p.getName() + " " + check + " " + info + " VL=" + String.format("%.1f", vl));
        }
        double kickVl = c.getDouble("kick-vl", 0);
        if (kickVl > 0 && vl >= kickVl) {
            d.vl.put(check, 0.0);
            log(p.getName() + " KICK " + check);
            Bukkit.getScheduler().runTask(this, () -> p.kick(LEGACY.deserialize(
                    fill(getConfig().getString("messages.kick", ""), Map.of("check", check)))));
        }
        double punishVl = getConfig().getDouble("punish-vl", 0);
        String cmd = getConfig().getString("punish-command", "");
        if (punishVl > 0 && vl >= punishVl && !cmd.isBlank() && punished.add(p.getUniqueId())) {
            log(p.getName() + " PUNISH " + check);
            Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName())));
        }
        return vl;
    }

    private void decay() {
        double dec = getConfig().getDouble("decay-per-10s", 2.0);
        for (PlayerData d : data.values()) {
            d.vl.replaceAll((k, v) -> Math.max(0, v - dec));
        }
    }

    private synchronized void log(String line) {
        if (!getConfig().getBoolean("log-to-file", true)) {
            return;
        }
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(getDataFolder(), "violations.log"), true))) {
            w.println("[" + LocalDateTime.now().format(TS) + "] " + line);
        } catch (IOException ignored) {
            // лог не критичен
        }
    }

    private static String fill(String s, Map<String, String> v) {
        for (Map.Entry<String, String> e : v.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue());
        }
        return s;
    }

    private Component msg(String key, Map<String, String> v) {
        return LEGACY.deserialize(getConfig().getString("prefix", "") + fill(getConfig().getString("messages." + key, key), v));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "alerts" -> {
                if (sender instanceof Player p) {
                    if (alertsOff.remove(p.getUniqueId())) {
                        sender.sendMessage(msg("alerts-on", Map.of()));
                    } else {
                        alertsOff.add(p.getUniqueId());
                        sender.sendMessage(msg("alerts-off", Map.of()));
                    }
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("clinderac.admin")) {
                    sender.sendMessage(msg("no-permission", Map.of()));
                    return true;
                }
                reloadConfig();
                sender.sendMessage(msg("reloaded", Map.of()));
            }
            case "info" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) {
                    sender.sendMessage(msg("usage", Map.of()));
                    return true;
                }
                sender.sendMessage(msg("info-header", Map.of("player", t.getName())));
                boolean any = false;
                for (Map.Entry<String, Double> e : data(t).vl.entrySet()) {
                    if (e.getValue() >= 0.1) {
                        any = true;
                        sender.sendMessage(LEGACY.deserialize(fill(getConfig().getString("messages.info-line", ""),
                                Map.of("check", e.getKey(), "vl", String.format("%.1f", e.getValue())))));
                    }
                }
                if (!any) {
                    sender.sendMessage(LEGACY.deserialize(getConfig().getString("messages.info-none", "")));
                }
            }
            default -> sender.sendMessage(msg("usage", Map.of()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("alerts", "info", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
