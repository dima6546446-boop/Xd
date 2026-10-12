package ru.clindergrief.ac;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Проверки мира:
 *  fastbreak — блок сломан заметно быстрее, чем позволяют инструмент, эффекты и зачарования;
 *  fastplace — слишком много поставленных блоков в секунду;
 *  xray      — подозрительно высокая доля алмазов/обломков среди добытого камня (только уведомление).
 */
public final class WorldChecks implements Listener {
    private static final Set<Material> STONE = EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.TUFF,
            Material.NETHERRACK, Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.BASALT, Material.BLACKSTONE);
    private static final Set<Material> VALUABLE = EnumSet.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.ANCIENT_DEBRIS, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);

    private final ClinderAC ac;

    public WorldChecks(ClinderAC ac) {
        this.ac = ac;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || ac.exempt(p)) {
            return;
        }
        PlayerData d = ac.data(p);
        Block b = e.getBlock();
        long now = System.currentTimeMillis();

        // --- fastbreak
        if (ac.enabled("fastbreak") && d.digBlock != null && d.digBlock.equals(b.getLocation())) {
            float perTick = b.getBreakSpeed(p);
            if (perTick > 0 && perTick < 1) {
                double expectedMs = Math.ceil(1.0 / perTick) * 50.0;
                long actual = now - d.digStart;
                if (actual < expectedMs * 0.7 - 100) {
                    ac.flag(p, "fastbreak", 2 + (expectedMs - actual) / 250.0,
                            String.format("%d мс вместо ~%.0f мс (%s)", actual, expectedMs, b.getType().name().toLowerCase()));
                    if (ac.check("fastbreak").getBoolean("cancel", true)) {
                        e.setCancelled(true);
                    }
                }
            }
        }
        d.digBlock = null;

        // --- xray (только подозрение)
        if (ac.enabled("xray")) {
            if (now - d.xrayWindowStart > 600_000) {
                d.xrayWindowStart = now;
                d.xrayStone = 0;
                d.xrayValuable = 0;
            }
            Material m = b.getType();
            if (STONE.contains(m)) {
                d.xrayStone++;
            } else if (VALUABLE.contains(m)) {
                d.xrayValuable++;
                double ratio = d.xrayValuable / (double) (d.xrayStone + d.xrayValuable);
                if (d.xrayValuable >= 8 && ratio > 0.04 && d.xrayValuable % 4 == 0) {
                    ac.flag(p, "xray", 1, String.format("ценных руд %d на %d камня за 10 мин (%.1f%%)",
                            d.xrayValuable, d.xrayStone, ratio * 100));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!ac.enabled("fastplace") || p.getGameMode() != GameMode.SURVIVAL || ac.exempt(p)) {
            return;
        }
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();
        d.places.addLast(now);
        while (!d.places.isEmpty() && now - d.places.peekFirst() > 1000) {
            d.places.removeFirst();
        }
        int max = ac.check("fastplace").getInt("max-per-second", 14);
        if (d.places.size() > max) {
            ac.flag(p, "fastplace", 1, d.places.size() + " блоков/с");
            if (ac.check("fastplace").getBoolean("cancel", true)) {
                e.setCancelled(true);
            }
        }
    }
}
