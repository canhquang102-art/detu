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

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String name = c.getName().toLowerCase();

        if (name.equals("ditheo") || name.equals("baove") || name.equals("tancong")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Chi nguoi choi moi dung duoc lenh nay.");
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
            DiscipleMenu.open(m, p);
            return true;
        }

        switch (a[0].toLowerCase()) {
            case "goi" -> {
                if (s instanceof Player p) {
                    if (!m.summon(p)) m.msg(p, "&e[Đệ tử] Đệ tử đã ở bên cạnh rồi.");
                }
            }
            case "cat" -> {
                if (s instanceof Player p) {
                    if (!m.dismiss(p)) m.msg(p, "&e[Đệ tử] Đệ tử chưa được gọi ra.");
                }
            }
            case "mode" -> {
                if (s instanceof Player p && a.length >= 2) {
                    Mode mode = Mode.parse(a[1]);
                    if (mode == null) m.msg(p, "&cDùng: /detu mode <tancong|baove|ditheo>");
                    else m.setMode(p, mode);
                }
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
                    m.award(t, Long.parseLong(a[2]), true);
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
                if (s instanceof Player p) DiscipleMenu.open(m, p);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!c.getName().equalsIgnoreCase("detu")) return out;
        if (a.length == 1) {
            for (String x : new String[]{"goi", "cat", "mode", "addsm", "reload"}) {
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
