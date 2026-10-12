package ru.clindergrief.core;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Топы игроков (деньги, время в игре, убийства игроков). Пересчитываются по таймеру. */
public final class Leaderboards {
    public enum Type {
        MONEY("богачей"), PLAYTIME("по времени в игре"), KILLS("убийц");

        final String title;

        Type(String title) {
            this.title = title;
        }
    }

    public record Entry(String name, double value) {
    }

    private final ClinderCore plugin;
    private volatile Map<Type, List<Entry>> cache = new EnumMap<>(Type.class);

    public Leaderboards(ClinderCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long period = Math.max(1, plugin.getConfig().getLong("leaderboards.update-minutes", 5)) * 1200L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 100L, period);
    }

    public void refresh() {
        int size = plugin.getConfig().getInt("leaderboards.size", 10);
        Map<Type, List<Entry>> next = new EnumMap<>(Type.class);
        for (Type t : Type.values()) {
            next.put(t, new ArrayList<>());
        }
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            String name = p.getName();
            if (name == null) {
                continue;
            }
            try {
                next.get(Type.MONEY).add(new Entry(name, plugin.economy().getBalance(p)));
                next.get(Type.PLAYTIME).add(new Entry(name, p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0));
                next.get(Type.KILLS).add(new Entry(name, p.getStatistic(Statistic.PLAYER_KILLS)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Пропуск игрока в топе: " + name, e);
            }
        }
        for (List<Entry> list : next.values()) {
            list.sort((a, b) -> Double.compare(b.value(), a.value()));
            if (list.size() > size) {
                list.subList(size, list.size()).clear();
            }
        }
        cache = next;
    }

    public List<Entry> get(Type type) {
        return Collections.unmodifiableList(cache.getOrDefault(type, List.of()));
    }

    public static String format(Type type, double value) {
        return switch (type) {
            case MONEY -> Text.money(value) + "$";
            case PLAYTIME -> Text.duration((long) value);
            case KILLS -> String.valueOf((long) value);
        };
    }

    public static Type parse(String s) {
        return switch (s.toLowerCase()) {
            case "money", "balance", "dengi" -> Type.MONEY;
            case "playtime", "time", "online" -> Type.PLAYTIME;
            case "kills", "pvp" -> Type.KILLS;
            default -> null;
        };
    }
}
