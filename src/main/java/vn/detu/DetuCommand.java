package vn.detu;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class DetuCommand implements CommandExecutor, TabCompleter {

    private final DiscipleManager m;

    public DetuCommand(DiscipleManager m) {
        this.m = m;
    }

    private void openMain(Player p) {
        if (m.canUse(p)) DiscipleMenu.open(m, p);
        else ShopMenu.open(m, p);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String name = c.getName().toLowerCase();

        if (name.equals("hopthe")) {
            if (s instanceof Player p) m.toggleFuse(p);
            return true;
        }

        if (name.equals("ditheo") || name.equals("baove") || name.equals("tancong")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Chi nguoi choi moi dung duoc lenh nay.");
                return true;
            }
            if (!m.canUse(p)) {
                m.msg(p, "&c[Đệ tử] Bạn chưa sở hữu đệ tử. Dùng &e/detu shop &cđể mua.");
                return true;
            }
            m.setMode(p, Mode.parse(name));
            return true;
        }

        if (a.length == 0) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Chi nguoi choi moi dung duoc lenh nay.");
                return true;
            }
            openMain(p);
            return true;
        }

        switch (a[0].toLowerCase()) {
            case "goi" -> {
                if (s instanceof Player p) {
                    if (m.isActive(p.getUniqueId())) m.msg(p, "&e[Đệ tử] Đệ tử đã ở bên cạnh rồi.");
                    else m.summon(p);
                }
            }
            case "cat" -> {
                if (s instanceof Player p) {
                    if (!m.dismiss(p)) m.msg(p, "&e[Đệ tử] Đệ tử chưa được gọi ra.");
                }
            }
            case "mode" -> {
                if (s instanceof Player p && a.length >= 2) {
                    if (!m.canUse(p)) {
                        m.msg(p, "&c[Đệ tử] Bạn chưa sở hữu đệ tử.");
                        return true;
                    }
                    Mode mode = Mode.parse(a[1]);
                    if (mode == null) m.msg(p, "&cDùng: /detu mode <tancong|baove|ditheo>");
                    else m.setMode(p, mode);
                }
            }
            case "hopthe" -> {
                if (s instanceof Player p) m.toggleFuse(p);
            }
            case "resetfuse" -> {
                if (!s.hasPermission("detu.admin")) {
                    s.sendMessage("Ban khong co quyen.");
                    return true;
                }
                if (a.length < 2) {
                    s.sendMessage("Dung: /detu resetfuse <nguoi_choi>");
                    return true;
                }
                Player t = Bukkit.getPlayer(a[1]);
                if (t == null) {
                    s.sendMessage("Khong tim thay nguoi choi.");
                    return true;
                }
                m.resetFusionCooldown(t);
                s.sendMessage("Da xoa thoi gian hoi hop the cua " + t.getName() + ".");
            }
            case "caitrang" -> {
                if (s instanceof Player p) {
                    if (!m.canUse(p)) m.msg(p, "&c[Cải trang] Bạn chưa sở hữu đệ tử.");
                    else CostumeMenu.open(m, p);
                }
            }
            case "givecostume" -> {
                if (!s.hasPermission("detu.admin")) {
                    s.sendMessage("Ban khong co quyen.");
                    return true;
                }
                if (a.length < 3) {
                    s.sendMessage("Dung: /detu givecostume <nguoi_choi> <id: trang1|trang2|trang3>");
                    return true;
                }
                Player t = Bukkit.getPlayer(a[1]);
                if (t == null || m.costume(a[2]) == null) {
                    s.sendMessage("Khong tim thay nguoi choi hoac cai trang.");
                    return true;
                }
                m.giveCostume(t, a[2]);
                s.sendMessage("Da tang cai trang " + a[2] + " cho " + t.getName() + ".");
            }
            case "shop" -> {
                if (s instanceof Player p) ShopMenu.open(m, p);
            }
            case "give" -> {
                if (!s.hasPermission("detu.admin")) {
                    s.sendMessage("Ban khong co quyen.");
                    return true;
                }
                if (a.length < 2) {
                    s.sendMessage("Dung: /detu give <nguoi_choi>");
                    return true;
                }
                Player t = Bukkit.getPlayer(a[1]);
                if (t == null) {
                    s.sendMessage("Khong tim thay nguoi choi.");
                    return true;
                }
                m.giveOwnership(t);
                s.sendMessage("Da tang de tu cho " + t.getName() + ".");
                m.msg(t, "&a[Đệ tử] Bạn đã được tặng đệ tử! Dùng &e/detu goi&a.");
            }
            case "addsm" -> {
                if (!s.hasPermission("detu.admin")) {
                    s.sendMessage("Ban khong co quyen.");
                    return true;
                }
                if (a.length < 3) {
                    s.sendMessage("Dung: /detu addsm <nguoi_choi> <so>");
                    return true;
                }
                Player t = Bukkit.getPlayer(a[1]);
                if (t == null) {
                    s.sendMessage("Khong tim thay nguoi choi.");
                    return true;
                }
                try {
                    m.awardRaw(t, Long.parseLong(a[2]), true);
                    s.sendMessage("Da cong SM.");
                } catch (NumberFormatException e) {
                    s.sendMessage("So khong hop le.");
                }
            }
            case "reload" -> {
                if (!s.hasPermission("detu.admin")) {
                    s.sendMessage("Ban khong co quyen.");
                    return true;
                }
                m.reloadConfig();
                s.sendMessage("Da nap lai config.");
            }
            default -> {
                if (s instanceof Player p) openMain(p);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!c.getName().equalsIgnoreCase("detu")) return out;
        if (a.length == 1) {
            for (String x : new String[]{"goi", "cat", "mode", "hopthe", "caitrang", "givecostume", "resetfuse", "shop", "give", "addsm", "reload"}) {
                if (x.startsWith(a[0].toLowerCase())) out.add(x);
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("mode")) {
            for (String x : new String[]{"tancong", "baove", "ditheo"}) {
                if (x.startsWith(a[1].toLowerCase())) out.add(x);
            }
        }
        return out;
    }
}
