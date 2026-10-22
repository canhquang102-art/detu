package vn.detu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CostumeMenu implements InventoryHolder {

    public static final int SLOT_BACK = 22;
    private static final int[] SLOTS = {11, 13, 15, 10, 12, 14, 16};

    private final Inventory inv;
    private final Map<Integer, String> bySlot = new HashMap<>();

    private CostumeMenu(DiscipleManager m, Player p) {
        inv = Bukkit.createInventory(this, 27, Util.color("&0&lCẢI TRANG ĐỆ TỬ"));
        PlayerData pd = m.data(p.getUniqueId());

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "&7 ", new ArrayList<>(), false);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<DiscipleManager.Costume> list = m.costumes();
        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            DiscipleManager.Costume c = list.get(i);
            boolean owned = pd.costumesOwned.contains(c.id);
            boolean worn = c.id.equals(pd.costumeWorn);

            List<String> lore = new ArrayList<>();
            lore.add("&7Tăng &e+" + String.format("%.0f", c.percent) + "% &7tiềm năng / SM nhận được.");
            lore.add("");
            if (worn) {
                lore.add("&aĐang mặc");
                lore.add("&eBấm để cởi");
            } else if (owned) {
                lore.add("&aĐã sở hữu");
                lore.add("&eBấm để mặc");
            } else {
                lore.add("&7Giá: &e" + Util.fmt((long) c.price));
                lore.add("&eBấm để mua và mặc");
            }

            Material mat = Material.matchMaterial(c.material);
            if (mat == null) mat = Material.LEATHER_CHESTPLATE;
            int slot = SLOTS[i];
            bySlot.put(slot, c.id);
            inv.setItem(slot, item(mat, (worn ? "&a" : "&d") + c.name, lore, worn));
        }

        inv.setItem(SLOT_BACK, item(Material.ARROW, "&cQuay lại", List.of("&7Về menu đệ tử."), false));
    }

    public static void open(DiscipleManager m, Player p) {
        p.openInventory(new CostumeMenu(m, p).inv);
    }

    public String idAt(int slot) {
        return bySlot.get(slot);
    }

    private static ItemStack item(Material mat, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Util.color(name));
        meta.lore(Util.colorAll(lore));
        if (glow) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
