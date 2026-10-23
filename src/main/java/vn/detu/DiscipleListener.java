package vn.detu;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

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
    public void onPlayerDeath(PlayerDeathEvent e) {
        m.onDeath(e.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        m.onRespawn(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        m.onWorldChange(e.getPlayer());
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerAttack(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player pl) {
            attacker = pl;
        } else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2) {
            attacker = pl2;
        }
        if (attacker != null) m.ownerAttacked(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFusedHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!m.isFused(p.getUniqueId())) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (target instanceof Player && !m.fusionAffectsPlayers()) return;
        e.setDamage(m.fusedDamage(p, e.getDamage()));
        if (!(target instanceof Player)) m.fusedHit(p);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return; // giet nguoi choi khong cong SM
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
        if (m.canUse(p)) DiscipleMenu.open(m, p);
        else ShopMenu.open(m, p);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof DiscipleMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;

        int slot = e.getSlot();
        switch (slot) {
            case DiscipleMenu.SLOT_ATTACK -> m.setMode(p, Mode.ATTACK);
            case DiscipleMenu.SLOT_PROTECT -> m.setMode(p, Mode.PROTECT);
            case DiscipleMenu.SLOT_FOLLOW -> m.setMode(p, Mode.FOLLOW);
            case DiscipleMenu.SLOT_FUSE -> m.toggleFuse(p);
            case DiscipleMenu.SLOT_CAPSULE -> {
                if (m.isActive(p.getUniqueId())) m.dismiss(p);
                else m.summon(p);
            }
            case DiscipleMenu.SLOT_COSTUME -> {
                CostumeMenu.open(m, p);
                return;
            }
            case DiscipleMenu.SLOT_REROLL -> {
                ConfirmMenu.open(m, p);
                return;
            }
            default -> {
                return;
            }
        }
        DiscipleMenu.open(m, p);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ShopMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        if (e.getSlot() == ShopMenu.SLOT_BUY) {
            m.buy(p);
            ShopMenu.open(m, p);
        }
    }

    @EventHandler
    public void onConfirmClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ConfirmMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        if (e.getSlot() == ConfirmMenu.SLOT_YES) {
            m.reroll(p);
            DiscipleMenu.open(m, p);
        } else if (e.getSlot() == ConfirmMenu.SLOT_NO) {
            DiscipleMenu.open(m, p);
        }
    }

    @EventHandler
    public void onCostumeClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof CostumeMenu cm)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        int slot = e.getSlot();
        if (slot == CostumeMenu.SLOT_BACK) {
            DiscipleMenu.open(m, p);
            return;
        }
        String id = cm.idAt(slot);
        if (id == null) return;
        m.costumeAction(p, id);
        CostumeMenu.open(m, p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof DiscipleMenu || e.getInventory().getHolder() instanceof ShopMenu
                || e.getInventory().getHolder() instanceof CostumeMenu
                || e.getInventory().getHolder() instanceof ConfirmMenu) {
            e.setCancelled(true);
        }
    }
}
