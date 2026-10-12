package ru.clindergrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /report — жалоба на игрока (уведомление модераторам + журнал), /reports — просмотр. */
public final class Reports implements CommandExecutor {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private final ClinderCore plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public Reports(ClinderCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reports")) {
            return view(sender, args);
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("report-usage"));
            return true;
        }
        String target = args[0];
        if (sender.getName().equalsIgnoreCase(target)) {
            sender.sendMessage(plugin.msg("report-self"));
            return true;
        }
        if (sender instanceof Player p) {
            long cd = plugin.getConfig().getLong("reports.cooldown-seconds", 60) * 1000L;
            long left = cooldown.getOrDefault(p.getUniqueId(), 0L) + cd - System.currentTimeMillis();
            if (left > 0) {
                sender.sendMessage(plugin.msg("report-cooldown", Map.of("seconds", left / 1000 + 1)));
                return true;
            }
            cooldown.put(p.getUniqueId(), System.currentTimeMillis());
        }
        Player online = Bukkit.getPlayerExact(target);
        if (online != null) {
            target = online.getName();
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("time", LocalDateTime.now().format(FMT));
        report.put("reporter", sender.getName());
        report.put("target", target);
        report.put("reason", reason);
        if (sender instanceof Player p) {
            report.put("location", p.getWorld().getName() + " " + p.getLocation().getBlockX() + " "
                    + p.getLocation().getBlockY() + " " + p.getLocation().getBlockZ());
        }
        plugin.store().addReport(report, plugin.getConfig().getInt("reports.keep-last", 200));
        sender.sendMessage(plugin.msg("report-sent"));
        var notice = plugin.msg("report-notify", Map.of("reporter", sender.getName(), "target", target, "reason", reason));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("clindercore.reports.notify")) {
                staff.sendMessage(notice);
                staff.playSound(staff.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            }
        }
        plugin.getLogger().info("Жалоба: " + sender.getName() + " -> " + target + ": " + reason);
        return true;
    }

    private boolean view(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clindercore.reports.view")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            plugin.store().clearReports();
            sender.sendMessage(plugin.msg("reports-cleared"));
            return true;
        }
        List<Map<?, ?>> list = plugin.store().reports();
        if (list.isEmpty()) {
            sender.sendMessage(plugin.msg("reports-empty"));
            return true;
        }
        List<Map<?, ?>> last = list.subList(Math.max(0, list.size() - 10), list.size());
        sender.sendMessage(plugin.msg("reports-header", Map.of("count", last.size())));
        for (Map<?, ?> r : last) {
            Map<String, Object> v = new HashMap<>();
            r.forEach((k, val) -> v.put(String.valueOf(k), val));
            sender.sendMessage(Text.of(Text.fill(plugin.raw("reports-line"), v)));
        }
        return true;
    }
}
