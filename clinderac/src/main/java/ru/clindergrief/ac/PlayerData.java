package ru.clindergrief.ac;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Состояние игрока для проверок античита. */
public final class PlayerData {
    final long joinedAt = System.currentTimeMillis();
    final Map<String, Double> vl = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlert = new HashMap<>();

    // движение
    Location lastGround;
    int airTicks;
    long lastTeleport = System.currentTimeMillis();
    long lastSetback;
    org.bukkit.Location windowStartLoc;
    long lastVelocity;
    long lastSpecialBlock;   // слизь, лёд, мёд, батут-кровать и т.п. под ногами
    long lastLiquidOrClimb;
    long lastRiptideOrGlide;
    long lastVehicle;
    double lastHorizontal;   // горизонтальное смещение за последнее движение
    long windowStart = System.currentTimeMillis();
    double windowDistance;
    int speedStrikes;
    // таймер (баланс пакетов движения)
    long timerLast = System.currentTimeMillis();
    double timerBalance;

    // бой
    final Deque<Long> swings = new ArrayDeque<>();
    final Deque<Long> intervals = new ArrayDeque<>();
    long lastSwing;
    UUID lastVictim;
    long lastHit;

    // блоки
    Location digBlock;
    long digStart;
    final Deque<Long> places = new ArrayDeque<>();
    long xrayWindowStart = System.currentTimeMillis();
    int xrayStone;
    int xrayValuable;

    PlayerData(Player p) {
        this.lastGround = p.getLocation();
    }

    /** Не чаще одного уведомления в 2 секунды на проверку. */
    boolean shouldAlert(String check) {
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(check);
        if (last != null && now - last < 2000) {
            return false;
        }
        lastAlert.put(check, now);
        return true;
    }
}
