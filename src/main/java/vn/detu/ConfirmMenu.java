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

public final class ConfirmMenu implements InventoryHolder {

    public static final int SLOT_YES = 11;
    public static final int SLOT_INFO = 13;
    public static final int SLOT_NO = 15;

    private final Inventory inv;

    private ConfirmMenu(DiscipleManager m, Player p) {
        inv = Bukkit.createInventory(this, 27, Util.color("&0&lĐỔI ĐỆ TỬ"));

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "&7 ", new ArrayList<>());
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> info = new ArrayList<>();
        info.add("&7Giá: &e" + Util.fmt((long) m.rerollPrice()));
        info.add("");
        info.add("&cĐệ tử hiện tại sẽ MẤT.");
        info.add("&cHP gốc và sức đánh gốc về cấp 0.");
        info.add("&cSM về &e" + Util.fmt(m.rerollStartSm()) + "&c, tiềm năng về 0.");
        info.add("&7Cải trang đã mua vẫn được giữ.");
        inv.setItem(SLOT_INFO, item(Material.BOOK, "&6Đổi sang đệ tử mới?", info));

        inv.setItem(SLOT_YES, item(Material.LIME_CONCRETE, "&aXÁC NHẬN ĐỔI",
                List.of("&7Trừ &e" + Util.fmt((long) m.rerollPrice()) + " &7và đổi đệ tử mới.")));
        inv.setItem(SLOT_NO, item(Material.RED_CONCRETE, "&cHỦY", List.of("&7Quay lại menu đệ tử.")));
    }

    public static void open(DiscipleManager m, Player p) {
        p.openInventory(new ConfirmMenu(m, p).inv);
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
