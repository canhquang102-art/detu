package vn.detu;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DiscipleListener implements Listener {

    private final DiscipleManager m;
    private final Map<UUID, Long> lastOpen = new HashMap<>();

    public DiscipleListener(DiscipleManager m) {
        this.m = m;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        m.onJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        m.onQuit(e.getPlayer());
    }

    @EventHandler
    public void onDamaged(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        LivingEntity attacker = null;
        if (e.getDamager() instanceof LivingEntity le) {
            attacker = le;
        } else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof LivingEntity le2) {
            attacker = le2;
        }
        if (attacker != null) m.notifyAttacked(victim, attacker);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null && m.isActive(killer.getUniqueId())) {
            m.ownerKill(killer, e.getEntity());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        handleInteract(e.getPlayer(), e.getRightClicked().getUniqueId(), e);
    }

    @EventHandler
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        handleInteract(e.getPlayer(), e.getRightClicked().getUniqueId(), e);
    }

    private void handleInteract(Player p, UUID entityId, org.bukkit.event.Cancellable ev) {
        if (!m.isBody(entityId)) return;
        ev.setCancelled(true);
        UUID owner = m.ownerOfBody(entityId);
        if (owner == null || !owner.equals(p.getUniqueId())) return;
        long now = System.currentTimeMillis();
        Long last = lastOpen.get(p.getUniqueId());
        if (last != null && now - last < 250) return;
        lastOpen.put(p.getUniqueId(), now);
        DiscipleMenu.open(m, p);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof DiscipleMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;

        int slot = e.getSlot();
        int amount = e.isShiftClick() ? 5 : 1;
        switch (slot) {
            case DiscipleMenu.SLOT_ATTACK -> m.setMode(p, Mode.ATTACK);
            case DiscipleMenu.SLOT_PROTECT -> m.setMode(p, Mode.PROTECT);
            case DiscipleMenu.SLOT_FOLLOW -> m.setMode(p, Mode.FOLLOW);
            case DiscipleMenu.SLOT_HP -> m.upgrade(p, true, amount);
            case DiscipleMenu.SLOT_ATK -> m.upgrade(p, false, amount);
            case DiscipleMenu.SLOT_CAPSULE -> {
                if (m.isActive(p.getUniqueId())) m.dismiss(p);
                else m.summon(p);
            }
            default -> {
                return;
            }
        }
        DiscipleMenu.open(m, p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof DiscipleMenu) e.setCancelled(true);
    }
}
