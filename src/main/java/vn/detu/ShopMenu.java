package vn.detu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ShopMenu implements InventoryHolder {

    public static final int SLOT_BUY = 13;

    private final Inventory inv;

    private ShopMenu(DiscipleManager m, Player p) {
        inv = Bukkit.createInventory(this, 27, Util.color("&0&lSHOP ĐỆ TỬ"));
        PlayerData pd = m.data(p.getUniqueId());
        boolean hasRank = p.hasPermission(m.shopPermission());

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "&7 ", new ArrayList<>());
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> lore = new ArrayList<>();
        lore.add("&7Một đệ tử có skin riêng, đi theo và chiến đấu cùng bạn.");
        lore.add("&7Mỗi người chỉ mua được &e1 &7đệ tử.");
        lore.add("");
        lore.add("&7Giá: &e" + Util.fmt((long) m.shopPrice()));
        lore.add("&7Yêu cầu: &bRank Bạch Kim");
        lore.add("");

        Material mat;
        String name;
        if (pd.owned) {
            mat = Material.EMERALD_BLOCK;
            name = "&aBạn đã sở hữu đệ tử";
            lore.add("&aDùng /detu goi để gọi đệ tử.");
        } else if (!hasRank) {
            mat = Material.BARRIER;
            name = "&cChưa đủ rank";
            lore.add("&cBạn cần đạt rank Bạch Kim để mua.");
        } else {
            mat = Material.NETHER_STAR;
            name = "&6Mua đệ tử";
            lore.add("&eBấm để mua");
        }
        inv.setItem(SLOT_BUY, item(mat, name, lore));
    }

    public static void open(DiscipleManager m, Player p) {
        p.openInventory(new ShopMenu(m, p).inv);
    }

    private static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Util.color(name));
        meta.lore(Util.colorAll(lore));
        it.setItemMeta(meta);
        return it;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
