package vn.detu;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeTuPlugin extends JavaPlugin {

    private DiscipleManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new DiscipleManager(this);
        manager.loadData();

        Bukkit.getPluginManager().registerEvents(new DiscipleListener(manager), this);

        DetuCommand cmd = new DetuCommand(manager);
        for (String name : new String[]{"detu", "ditheo", "baove", "tancong", "hopthe"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmd);
                pc.setTabCompleter(cmd);
            }
        }

        manager.start();
        for (Player p : Bukkit.getOnlinePlayers()) manager.onJoin(p);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }
}
