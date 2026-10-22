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

public final class DiscipleMenu implements InventoryHolder {

    public static final int SLOT_ATTACK = 10;
    public static final int SLOT_PROTECT = 11;
    public static final int SLOT_FOLLOW = 12;
    public static final int SLOT_STATS = 13;
    public static final int SLOT_SKILLS = 14;
    public static final int SLOT_FUSE = 15;
    public static final int SLOT_CAPSULE = 16;
    public static final int SLOT_COSTUME = 22;

    private final Inventory inv;

    private DiscipleMenu(DiscipleManager m, Player p) {
        this.inv = Bukkit.createInventory(this, 27, Util.color("&0&lĐIỀU KHIỂN ĐỆ TỬ"));
        PlayerData pd = m.data(p.getUniqueId());

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "&7 ", new ArrayList<>(), false);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_ATTACK, item(Material.IRON_SWORD, "&cTrạng thái: TẤN CÔNG",
                List.of("&7Đệ tử chủ động đồ sát quái vật xung quanh.", "", "&eBấm để chọn"), pd.mode == Mode.ATTACK));
        inv.setItem(SLOT_PROTECT, item(Material.SHIELD, "&eTrạng thái: BẢO VỆ",
                List.of("&7Đệ tử sẽ lao vào đấm kẻ dám tấn công Sư phụ.", "", "&eBấm để chọn"), pd.mode == Mode.PROTECT));
        inv.setItem(SLOT_FOLLOW, item(Material.LEAD, "&aTrạng thái: ĐI THEO",
                List.of("&7Đệ tử sẽ luôn lóc cóc bám đuôi Sư phụ.", "", "&eBấm để chọn"), pd.mode == Mode.FOLLOW));

        List<String> stats = new ArrayList<>();
        stats.add("&7Sức mạnh hiện tại: &e" + Util.fmt(pd.sm) + " SM");
        stats.add("&7HP gốc: &aCấp " + pd.hpLevel + " &7[" + m.maxHp(pd) + " HP]");
        stats.add("&7Sức đánh gốc: &aCấp " + pd.atkLevel + " &7[" + String.format("%.1f", m.damage(pd)) + " SĐ]");
        stats.add("&7Tiềm năng hiện có: &b" + Util.fmt(pd.potential));
        DiscipleManager.Costume worn = m.costume(pd.costumeWorn);
        stats.add("&7Cải trang: " + (worn == null ? "&8chưa mặc" : "&d" + worn.name + " &7(+" + String.format("%.0f", worn.percent) + "% tiềm năng/SM)"));
        stats.add("&7Lần nâng tới: " + (pd.autoTarget == 1 ? "&cHP gốc" : pd.autoTarget == 2 ? "&6Sức đánh gốc" : "&fchưa bốc")
                + (pd.autoTarget == 0 ? "" : " &7(cần &e" + Util.fmt(m.upgradeCost(pd.autoTarget == 1, pd)) + "&7)"));
        stats.add("&7Tiềm năng cần cho lần nâng tới: &e" + Util.fmt(m.upgradeCost(true, pd)) + " &7(mỗi lần nâng ×" + String.format("%.2f", m.upgradeMultiplier()) + ")");
        stats.add("");
        stats.add("&dĐệ tử tự nâng ngẫu nhiên HP hoặc sức đánh");
        stats.add("&dkhi đủ tiềm năng.");
        inv.setItem(SLOT_STATS, item(Material.BOOK, "&6Chỉ số đệ tử", stats, false));

        List<String> sk = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            boolean open = pd.moves[i] >= 0;
            sk.add((open ? "&a✔ " : "&c✘ ") + "&fSkill " + (i + 2) + " &7(mở ở " + Util.fmt(m.skillUnlock(i)) + " SM): "
                    + (open ? "&b" + m.skillLabel(pd, i) : "&cChưa mở khóa"));
        }
        sk.add("");
        sk.add("&7Mỗi ô random 1 chiêu lúc mở khóa,");
        sk.add("&7chiêu sở trường của tộc hệ ra nhiều hơn.");
        sk.add("&7Sở trường Xayda: &fAntomic / Kaioken / Biến Khỉ");
        inv.setItem(SLOT_SKILLS, item(Material.NETHER_STAR, "&dKỹ năng đệ tử", sk, false));

        boolean fusedNow = m.isFused(p.getUniqueId());
        long cdLeft = m.fuseCooldownRemaining(pd);
        List<String> fuse = new ArrayList<>();
        fuse.add("&7Thu hồi đệ tử, hợp thể với Sư phụ.");
        fuse.add("&7Mỗi đòn đấm của bạn cộng thêm đúng");
        fuse.add("&7bằng sức đánh của đệ tử: &e+" + String.format("%.1f", m.damage(pd)));
        fuse.add("&7Cộng máu tối đa bằng máu đệ tử: &c+" + m.fusionHpBonus(pd));
        fuse.add("");
        if (fusedNow) {
            fuse.add("&7Trạng thái: &aĐANG HỢP THỂ");
            fuse.add("&7Còn lại: &f" + Util.fmtTime(m.fuseRemaining(pd)));
            fuse.add("&eBấm để tách ra (vẫn phải đợi hồi chiêu)");
        } else if (cdLeft > 0) {
            fuse.add("&7Trạng thái: &cĐang hồi chiêu");
            fuse.add("&7Còn lại: &f" + Util.fmtTime(cdLeft));
        } else {
            fuse.add("&7Trạng thái: &aSẵn sàng");
            fuse.add("&eBấm để hợp thể");
        }
        inv.setItem(SLOT_FUSE, item(Material.ENDER_EYE, "&dHỢP THỂ", fuse, fusedNow));

        inv.setItem(SLOT_COSTUME, item(Material.LEATHER_CHESTPLATE, "&dCải trang đệ tử",
                List.of("&7Mặc cải trang để tăng tiềm năng / SM", "&7đệ tử nhận được (30% / 45% / 60%).", "", "&eBấm để mở"), worn != null));

        boolean out = m.isActive(p.getUniqueId());
        inv.setItem(SLOT_CAPSULE, item(Material.BARREL, out ? "&cCất đệ tử vào Capsule" : "&aGọi đệ tử ra",
                List.of("&7Bấm để " + (out ? "cất" : "gọi") + " đệ tử."), false));
    }

    public static void open(DiscipleManager m, Player p) {
        p.openInventory(new DiscipleMenu(m, p).inv);
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
