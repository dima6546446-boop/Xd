package ru.clindergrief.core;

import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/** /daily — ежедневная награда с серией дней. */
public final class Daily implements CommandExecutor {
    private final ClinderCore plugin;

    public Daily(ClinderCore plugin) {
        this.plugin = plugin;
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("daily.timezone", "Europe/Moscow"));
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!plugin.getConfig().getBoolean("daily.enabled", true)) {
            return true;
        }
        DataStore store = plugin.store();
        ZoneId zone = zone();
        long today = LocalDate.now(zone).toEpochDay();
        long last = store.dailyLastDay(player.getUniqueId());
        if (last == today) {
            ZonedDateTime now = ZonedDateTime.now(zone);
            long left = java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).getSeconds();
            player.sendMessage(plugin.msg("daily-already", Map.of("time", Text.duration(left))));
            return true;
        }
        int streak = (last == today - 1) ? store.dailyStreak(player.getUniqueId()) + 1 : 1;
        if (streak == 1 && last != Long.MIN_VALUE && last < today - 1) {
            player.sendMessage(plugin.msg("daily-broken"));
        }
        List<Integer> rewards = plugin.getConfig().getIntegerList("daily.rewards");
        int amount = rewards.isEmpty() ? 100 : rewards.get(Math.min(streak, rewards.size()) - 1);
        plugin.economy().depositPlayer(player, amount);
        store.setDaily(player.getUniqueId(), today, streak);
        player.sendMessage(plugin.msg("daily-claimed", Map.of("day", streak, "amount", Text.money(amount))));

        int every = plugin.getConfig().getInt("daily.milestone-every", 7);
        int bonus = plugin.getConfig().getInt("daily.milestone-bonus", 0);
        if (every > 0 && bonus > 0 && streak % every == 0) {
            plugin.economy().depositPlayer(player, bonus);
            player.sendMessage(plugin.msg("daily-milestone", Map.of("day", streak, "amount", Text.money(bonus))));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        }
        return true;
    }
}
