package ru.clindergrief.ac;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Боевые проверки:
 *  reach       — удар дальше дистанции взаимодействия (с учётом пинга и движения цели);
 *  killaura    — удар по цели далеко вне поля зрения и переключение между целями быстрее реакции;
 *  autoclicker — слишком высокий или «машинно ровный» темп кликов.
 */
public final class CombatChecks implements Listener {
    private final ClinderAC ac;

    public CombatChecks(ClinderAC ac) {
        this.ac = ac;
    }

    private static double distanceToBox(Vector eye, BoundingBox box) {
        double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        return eye.distance(new Vector(x, y, z));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        if (p.getGameMode() == GameMode.CREATIVE || ac.exempt(p)) {
            return;
        }
        PlayerData d = ac.data(p);
        Vector eye = p.getEyeLocation().toVector();
        BoundingBox box = victim.getBoundingBox();
        long now = System.currentTimeMillis();

        // --- reach
        if (ac.enabled("reach") && e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            AttributeInstance attr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
            double range = attr != null ? attr.getValue() : 3.0;
            double base = Math.max(range, ac.check("reach").getDouble("max-distance", 3.4) - 0.4);
            double victimSpeed = victim instanceof Player vp ? ac.data(vp).lastHorizontal : victim.getVelocity().length();
            double ping = Math.min(400, p.getPing());
            double allowed = base + 0.4 + Math.min(1.2, victimSpeed * (ping / 50.0 + 1));
            double dist = distanceToBox(eye, box);
            if (dist > allowed) {
                ac.flag(p, "reach", 1 + (dist - allowed) * 3, String.format("%.2f бл при допуске %.2f, пинг %d", dist, allowed, p.getPing()));
                if (ac.check("reach").getBoolean("cancel", true)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        // --- killaura: угол между взглядом и целью
        if (ac.enabled("killaura")) {
            Vector toVictim = box.getCenter().subtract(eye);
            double dist = toVictim.length();
            if (dist > 1.3) {
                double angle = Math.toDegrees(p.getEyeLocation().getDirection().angle(toVictim.normalize()));
                double max = ac.check("killaura").getDouble("max-angle", 70);
                if (angle > max) {
                    ac.flag(p, "killaura", 2, String.format("удар вне обзора: %.0f°", angle));
                    if (ac.check("killaura").getBoolean("cancel", true)) {
                        e.setCancelled(true);
                    }
                }
            }
            // переключение между разными целями быстрее 120 мс
            Entity prev = d.lastVictim == null ? null : p.getWorld().getEntity(d.lastVictim);
            if (prev != null && !prev.equals(victim) && now - d.lastHit < 120) {
                ac.flag(p, "killaura", 3, "несколько целей за " + (now - d.lastHit) + " мс");
            }
            d.lastVictim = victim.getUniqueId();
            d.lastHit = now;
        }
    }

    /** Взмахи рукой: считаем только клики «в воздух/по сущностям», копание блока не учитывается. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING || !ac.enabled("autoclicker")) {
            return;
        }
        Player p = e.getPlayer();
        if (ac.exempt(p) || p.getTargetBlockExact(5) != null) {
            return;
        }
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();
        if (d.lastSwing > 0 && now - d.lastSwing < 1000) {
            d.intervals.addLast(now - d.lastSwing);
            while (d.intervals.size() > 20) {
                d.intervals.removeFirst();
            }
        }
        d.lastSwing = now;
        d.swings.addLast(now);
        while (!d.swings.isEmpty() && now - d.swings.peekFirst() > 1000) {
            d.swings.removeFirst();
        }
        int cps = d.swings.size();
        int max = ac.check("autoclicker").getInt("max-cps", 20);
        if (cps > max) {
            ac.flag(p, "autoclicker", (cps - max) / 2.0, cps + " CPS");
        } else if (cps >= 13 && d.intervals.size() >= 20) {
            double mean = d.intervals.stream().mapToLong(Long::longValue).average().orElse(0);
            double var = d.intervals.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
            double std = Math.sqrt(var);
            if (std < 6) {
                ac.flag(p, "autoclicker", 2, String.format("%d CPS, разброс интервалов %.1f мс", cps, std));
                d.intervals.clear();
            }
        }
    }

    /** Начало копания блока — для fastbreak. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageBlock(BlockDamageEvent e) {
        PlayerData d = ac.data(e.getPlayer());
        d.digBlock = e.getBlock().getLocation();
        d.digStart = System.currentTimeMillis();
    }
}
