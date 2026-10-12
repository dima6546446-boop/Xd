package ru.clindergrief.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Плейсхолдеры PlaceholderAPI:
 *  %clinder_tag% %clinder_streak% %clinder_bounty% %clinder_playtime% %clinder_kills% %clinder_deaths%
 *  %clinder_afk%  — «AFK» или пусто
 *  %clinder_top_<money|playtime|kills>_<name|value>_<1..N>% — топы для голограмм
 */
public final class ClinderExpansion extends PlaceholderExpansion {
    private final ClinderCore plugin;

    public ClinderExpansion(ClinderCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "clinder";
    }

    @Override
    public String getAuthor() {
        return "ClinderGrief";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase();
        if (p.startsWith("top_")) {
            return top(p.substring(4));
        }
        if (player == null) {
            return "";
        }
        return switch (p) {
            case "streak" -> String.valueOf(plugin.store().dailyStreak(player.getUniqueId()));
            case "bounty" -> Text.money(plugin.store().bounty(player.getUniqueId()));
            case "tag" -> player instanceof Player online ? tagOf(online) : "";
            case "playtime" -> Text.duration(player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L);
            case "kills" -> String.valueOf(player.getStatistic(Statistic.PLAYER_KILLS));
            case "deaths" -> String.valueOf(player.getStatistic(Statistic.DEATHS));
            case "afk" -> player instanceof Player online && plugin.activity().isAfk(online) ? "AFK" : "";
            default -> null;
        };
    }

    private String tagOf(Player p) {
        String id = plugin.store().tag(p.getUniqueId());
        if (id == null) {
            return "";
        }
        return plugin.getConfig().getString("tags.list." + id + ".display", "");
    }

    /** money_name_1 / playtime_value_3 ... */
    private String top(String rest) {
        String[] parts = rest.split("_");
        if (parts.length != 3) {
            return null;
        }
        Leaderboards.Type type = Leaderboards.parse(parts[0]);
        int pos;
        try {
            pos = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (type == null || pos < 1) {
            return null;
        }
        List<Leaderboards.Entry> list = plugin.leaderboards().get(type);
        if (pos > list.size()) {
            return parts[1].equals("name") ? "—" : "";
        }
        Leaderboards.Entry e = list.get(pos - 1);
        return parts[1].equals("name") ? e.name() : Leaderboards.format(type, e.value());
    }
}
