package ru.clindergrief.ac;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

/**
 * Проверки движения:
 *  fly         — подъём в воздухе дольше прыжка и «зависание» без опоры;
 *  speed       — горизонтальная скорость за секунду выше возможной;
 *  groundspoof — клиент сообщает «на земле», а под ногами пусто (обход урона от падения);
 *  timer       — пакетов движения больше, чем позволяет реальное время (ускорение клиента).
 */
public final class MovementChecks implements Listener {
    private final ClinderAC ac;

    public MovementChecks(ClinderAC ac) {
        this.ac = ac;
    }

    // ------------------------------------------------------------ геометрия
    /** Есть ли твёрдая опора в слое [y - depth, y + 0.01] под хитбоксом игрока. */
    static boolean supported(Location loc, double depth) {
        World w = loc.getWorld();
        BoundingBox feet = new BoundingBox(loc.getX() - 0.3, loc.getY() - depth, loc.getZ() - 0.3,
                loc.getX() + 0.3, loc.getY() + 0.01, loc.getZ() + 0.3);
        int minX = (int) Math.floor(feet.getMinX()), maxX = (int) Math.floor(feet.getMaxX());
        int minY = (int) Math.floor(feet.getMinY()) - 1, maxY = (int) Math.floor(feet.getMaxY());
        int minZ = (int) Math.floor(feet.getMinZ()), maxZ = (int) Math.floor(feet.getMaxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir()) {
                        continue;
                    }
                    VoxelShape shape = b.getCollisionShape();
                    if (shape.overlaps(feet.clone().shift(-x, -y, -z))) {
                        return true;
                    }
                }
            }
        }
        // стоит на лодке/шалкере
        for (Entity e : w.getNearbyEntities(loc, 1.5, 1.5, 1.5)) {
            if (e instanceof Boat || e instanceof Shulker) {
                return true;
            }
        }
        return false;
    }

    /** Блоки, меняющие физику: жидкости, лестницы, паутина, слизь, лёд, мёд, снег, пузыри, подмости. */
    private static int special(Location loc) {
        int result = 0;
        World w = loc.getWorld();
        for (double dx = -0.4; dx <= 0.4; dx += 0.4) {
            for (double dz = -0.4; dz <= 0.4; dz += 0.4) {
                for (int dy = -2; dy <= 2; dy++) {
                    Block b = w.getBlockAt((int) Math.floor(loc.getX() + dx), (int) Math.floor(loc.getY()) + dy, (int) Math.floor(loc.getZ() + dz));
                    Material m = b.getType();
                    if (dy >= -1 && dy <= 1 && (b.isLiquid() || Tag.CLIMBABLE.isTagged(m) || m == Material.COBWEB
                            || m == Material.POWDER_SNOW || m == Material.BUBBLE_COLUMN || m == Material.SCAFFOLDING
                            || m == Material.SWEET_BERRY_BUSH || isWaterlogged(b))) {
                        result |= 1;   // жидкость/лазание
                    }
                    if (dy <= 0 && (m == Material.SLIME_BLOCK || m == Material.HONEY_BLOCK || Tag.ICE.isTagged(m)
                            || Tag.BEDS.isTagged(m) || m == Material.SOUL_SAND || m == Material.SOUL_SOIL)) {
                        result |= 2;   // скользкие/пружинящие
                    }
                }
            }
        }
        return result;
    }

    private static boolean isWaterlogged(Block b) {
        return b.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged();
    }

    private static int amp(Player p, PotionEffectType type) {
        PotionEffect e = p.getPotionEffect(type);
        return e == null ? -1 : e.getAmplifier();
    }

    // ------------------------------------------------------------ события-исключения
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerData d = ac.data(e.getPlayer());
        if (System.currentTimeMillis() - d.lastSetback < 100) {
            return;   // это наш собственный откат — иммунитет после телепорта не даём
        }
        d.lastTeleport = System.currentTimeMillis();
        d.lastGround = e.getTo();
        d.airTicks = 0;
        d.timerBalance = 0;
        d.windowDistance = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        PlayerData d = ac.data(e.getPlayer());
        d.lastTeleport = System.currentTimeMillis();
        d.lastGround = e.getRespawnLocation();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        ac.data(e.getPlayer()).lastVelocity = System.currentTimeMillis()
                + (long) Math.min(4000, e.getVelocity().length() * 2500);
    }

    // ------------------------------------------------------------ проверки
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location from = e.getFrom();
        Location to = e.getTo();
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();

        // --- timer: каждые 50 мс реального времени дают «кредит» на один пакет движения
        if (ac.enabled("timer")) {
            d.timerBalance += now - d.timerLast;
            d.timerLast = now;
            d.timerBalance = Math.min(d.timerBalance, 1000);
            d.timerBalance -= 50;
            if (d.timerBalance < -600 && now - d.lastTeleport > 2000 && !ac.exempt(p)) {
                ac.flag(p, "timer", 2, "лишних пакетов: " + (int) (-d.timerBalance / 50));
                d.timerBalance = 0;
                if (ac.check("timer").getBoolean("setback", true)) {
                    setback(e, d);
                    return;
                }
            }
        }

        if (to.getWorld() != from.getWorld()) {
            return;
        }
        boolean exempt = ac.exempt(p) || now - d.lastTeleport < 1500 || now < d.lastVelocity;
        if (p.isInsideVehicle()) {
            d.lastVehicle = now;
        }
        if (p.isGliding() || p.isRiptiding() || hasElytraBoost(p)) {
            d.lastRiptideOrGlide = now;
        }
        int sp = special(to);
        if ((sp & 1) != 0) {
            d.lastLiquidOrClimb = now;
        }
        if ((sp & 2) != 0) {
            d.lastSpecialBlock = now;
        }
        exempt |= now - d.lastVehicle < 1500 || now - d.lastRiptideOrGlide < 3000 || now - d.lastLiquidOrClimb < 800;

        boolean ground = supported(to, 0.05);
        double dy = to.getY() - from.getY();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        d.lastHorizontal = horizontal;

        if (ground) {
            d.airTicks = 0;
            if (!exempt) {
                d.lastGround = to.clone();
            }
        } else {
            d.airTicks++;
        }
        if (exempt) {
            d.windowDistance = 0;
            d.windowStart = now;
            d.windowStartLoc = to.clone();
            return;
        }

        // --- fly
        if (ac.enabled("fly") && !ground && amp(p, PotionEffectType.LEVITATION) < 0 && amp(p, PotionEffectType.SLOW_FALLING) < 0) {
            int jump = amp(p, PotionEffectType.JUMP_BOOST) + 1;
            boolean bounce = now - d.lastSpecialBlock < 3000;
            if (d.airTicks > 10 + jump * 4 && dy > 0.02 && !bounce) {
                ac.flag(p, "fly", 1 + dy * 4, String.format("подъём в воздухе, %d тиков, dy=%.2f", d.airTicks, dy));
                if (ac.check("fly").getBoolean("setback", true)) {
                    setback(e, d);
                    return;
                }
            } else if (d.airTicks > 30 && Math.abs(dy) < 0.03 && !supported(to, 0.7)) {
                ac.flag(p, "fly", 1.5, "зависание без опоры, " + d.airTicks + " тиков");
                if (ac.check("fly").getBoolean("setback", true)) {
                    setback(e, d);
                    return;
                }
            }
        }

        // --- groundspoof: клиент утверждает, что стоит на земле, хотя под ним пусто
        if (ac.enabled("groundspoof") && p.isOnGround() && !supported(to, 0.6) && !supported(from, 0.6) && d.airTicks > 3) {
            ac.flag(p, "groundspoof", 1, "«на земле» без опоры");
            if (ac.check("groundspoof").getBoolean("setback", true)) {
                setback(e, d);
                return;
            }
        }

        // --- speed: сумма горизонтального пути за ~1 секунду
        if (ac.enabled("speed")) {
            d.windowDistance += horizontal;
            long elapsed = now - d.windowStart;
            if (elapsed >= 1000) {
                double perSecond = d.windowDistance * 1000.0 / elapsed;
                double limit = 7.9;                                    // бег с прыжками ≈ 7.1 бл/с
                int speedAmp = amp(p, PotionEffectType.SPEED);
                if (speedAmp >= 0) {
                    limit *= 1 + 0.2 * (speedAmp + 1);
                }
                if (amp(p, PotionEffectType.DOLPHINS_GRACE) >= 0) {
                    limit *= 1.6;
                }
                if (now - d.lastSpecialBlock < 2500) {
                    limit *= 2.6;                                      // лёд, слизь, песок душ со скоростью душ
                }
                if (perSecond > limit + 1.2) {
                    d.speedStrikes++;
                    // явный спидхак (в 1.5 раза выше лимита) — сразу, умеренное превышение — со второй секунды подряд
                    if (d.speedStrikes >= 2 || perSecond > limit * 1.5) {
                        ac.flag(p, "speed", Math.min(10, perSecond - limit), String.format("%.1f бл/с при лимите %.1f", perSecond, limit));
                        if (ac.check("speed").getBoolean("setback", true)) {
                            // откат в точку начала секундного окна (а не на последний шаг по земле)
                            Location back = d.windowStartLoc;
                            d.windowDistance = 0;
                            d.windowStart = now;
                            if (back != null && back.getWorld() == from.getWorld() && back.distanceSquared(from) < 64 * 64) {
                                d.lastGround = back;
                            }
                            setback(e, d);
                            d.windowStartLoc = d.lastGround == null ? from.clone() : d.lastGround.clone();
                            return;
                        }
                    }
                } else {
                    d.speedStrikes = 0;
                }
                d.windowDistance = 0;
                d.windowStart = now;
                d.windowStartLoc = to.clone();
            }
        }
    }

    private static boolean hasElytraBoost(Player p) {
        ItemStack chest = p.getInventory().getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) {
            return false;
        }
        ItemMeta m = chest.getItemMeta();
        return m != null && p.isGliding();
    }

    private void setback(PlayerMoveEvent e, PlayerData d) {
        Location back = d.lastGround;
        if (back == null || back.getWorld() != e.getFrom().getWorld() || back.distanceSquared(e.getFrom()) > 64 * 64) {
            back = e.getFrom();
        }
        Location target = back.clone();
        target.setYaw(e.getTo().getYaw());
        target.setPitch(e.getTo().getPitch());
        d.lastSetback = System.currentTimeMillis();
        e.setTo(target);
        d.airTicks = 0;
    }
}
