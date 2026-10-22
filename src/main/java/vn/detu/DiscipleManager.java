package vn.detu;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DiscipleManager {

    static final class Disciple {
        final UUID owner;
        Mannequin body;
        LivingEntity target;
        long lastAttack = 0;
        long buffUntil = 0;
        long pvpUntil = 0;
        double buffFactor = 1.0;
        final long[] skillReady = new long[3]; // skill 2, 3, 4

        Disciple(UUID owner) {
            this.owner = owner;
        }
    }

    private final DeTuPlugin plugin;
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Map<UUID, Disciple> active = new HashMap<>();
    private final Map<UUID, UUID> bodyToOwner = new HashMap<>();
    private File file;
    private BukkitTask task;
    private int tickCount = 0;
    private final NamespacedKey bodyKey;
    private final NamespacedKey fuseHpKey;
    private final Map<UUID, Long> gainUntil = new HashMap<>();
    private final Map<UUID, int[]> autoPending = new HashMap<>();
    private final Map<UUID, Long> autoLast = new HashMap<>();

    public DiscipleManager(DeTuPlugin plugin) {
        this.plugin = plugin;
        this.bodyKey = new NamespacedKey(plugin, "body_owner");
        this.fuseHpKey = new NamespacedKey(plugin, "fusion_hp");
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ------------------------------------------------------------ data

    public PlayerData data(UUID id) {
        return data.computeIfAbsent(id, k -> new PlayerData());
    }

    public void loadData() {
        file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                PlayerData pd = new PlayerData();
                pd.sm = sec.getLong(key + ".sm", 0);
                pd.potential = sec.getLong(key + ".potential", 0);
                pd.hpLevel = sec.getInt(key + ".hp-level", 0);
                pd.atkLevel = sec.getInt(key + ".atk-level", 0);
                Mode m = Mode.parse(sec.getString(key + ".mode", "ditheo"));
                pd.mode = m == null ? Mode.FOLLOW : m;
                pd.summoned = sec.getBoolean(key + ".summoned", false);
                pd.owned = sec.getBoolean(key + ".owned", false);
                pd.fused = sec.getBoolean(key + ".fused", false);
                pd.fuseUntil = sec.getLong(key + ".fuse-until", 0);
                pd.fuseCooldownUntil = sec.getLong(key + ".fuse-cooldown-until", 0);
                pd.costumeWorn = sec.getString(key + ".costume-worn", null);
                pd.autoTarget = sec.getInt(key + ".auto-target", 0);
                pd.nextCost = sec.getLong(key + ".next-cost", 0);
                pd.costumesOwned.addAll(sec.getStringList(key + ".costumes-owned"));
                java.util.List<Integer> mv = sec.getIntegerList(key + ".moves");
                for (int i = 0; i < 3 && i < mv.size(); i++) pd.moves[i] = mv.get(i);
                data.put(id, pd);
            } catch (IllegalArgumentException ignored) {
                // key khong phai UUID
            }
        }
    }

    public void saveData() {
        if (file == null) file = new File(plugin.getDataFolder(), "data.yml");
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> e : data.entrySet()) {
            String k = "players." + e.getKey();
            PlayerData pd = e.getValue();
            y.set(k + ".sm", pd.sm);
            y.set(k + ".potential", pd.potential);
            y.set(k + ".hp-level", pd.hpLevel);
            y.set(k + ".atk-level", pd.atkLevel);
            y.set(k + ".mode", pd.mode.name().toLowerCase());
            y.set(k + ".summoned", pd.summoned);
            y.set(k + ".owned", pd.owned);
            y.set(k + ".fused", pd.fused);
            y.set(k + ".fuse-until", pd.fuseUntil);
            y.set(k + ".fuse-cooldown-until", pd.fuseCooldownUntil);
            y.set(k + ".costume-worn", pd.costumeWorn);
            y.set(k + ".auto-target", pd.autoTarget);
            y.set(k + ".next-cost", pd.nextCost);
            y.set(k + ".costumes-owned", new ArrayList<>(pd.costumesOwned));
            y.set(k + ".moves", Arrays.stream(pd.moves).boxed().toList());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Khong luu duoc data.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ lifecycle

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Disciple d : new ArrayList<>(active.values())) removeBody(d);
        active.clear();
        bodyToOwner.clear();
        for (Player p : Bukkit.getOnlinePlayers()) removeFusionHealth(p);
        saveData();
    }

    public void onJoin(Player p) {
        PlayerData pd = data(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (isFused(p.getUniqueId())) applyFusionHealth(p, false);
            else removeFusionHealth(p);
        }, 10L);
        if (pd.summoned) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) summon(p);
            }, 40L);
        }
    }

    public void onQuit(Player p) {
        Disciple d = active.remove(p.getUniqueId());
        if (d != null) removeBody(d);
        saveData();
    }

    // ------------------------------------------------------------ summon

    public boolean isActive(UUID owner) {
        return active.containsKey(owner);
    }

    public boolean isBody(UUID entityId) {
        return bodyToOwner.containsKey(entityId);
    }

    public UUID ownerOfBody(UUID entityId) {
        return bodyToOwner.get(entityId);
    }

    public boolean summon(Player p) {
        return summon(p, false);
    }

    public boolean summon(Player p, boolean silent) {
        if (active.containsKey(p.getUniqueId())) return false;
        if (!canUse(p)) {
            if (!silent) msg(p, "&c[Đệ tử] Bạn chưa sở hữu đệ tử. Dùng &e/detu shop &cđể mua.");
            return false;
        }
        if (data(p.getUniqueId()).fused) {
            if (!silent) msg(p, "&c[Đệ tử] Đang hợp thể với Sư phụ. Dùng &e/detu hopthe &cđể tách ra trước.");
            return false;
        }
        sweep(p.getUniqueId()); // xoa moi de tu bi bo lai cua nguoi nay
        PlayerData pd = data(p.getUniqueId());
        Disciple d = new Disciple(p.getUniqueId());
        spawnBody(d, p);
        active.put(p.getUniqueId(), d);
        pd.summoned = true;
        if (!silent) msg(p, "&a[Đệ tử] &fSư phụ ơi, con đây nè!");
        return true;
    }


    // ------------------------------------------------------------ shop

    public boolean requirePurchase() {
        return cfg().getBoolean("shop.require-purchase", true);
    }

    public String shopPermission() {
        return cfg().getString("shop.permission", "detu.shop");
    }

    public double shopPrice() {
        return cfg().getDouble("shop.price", 1_000_000);
    }

    public boolean canUse(Player p) {
        if (!requirePurchase()) return true;
        return data(p.getUniqueId()).owned || p.hasPermission("detu.admin");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object economy() {
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider rsp = Bukkit.getServicesManager().getRegistration((Class) ecoClass);
            return rsp == null ? null : rsp.getProvider();
        } catch (Throwable t) {
            return null;
        }
    }

    public void buy(Player p) {
        PlayerData pd = data(p.getUniqueId());
        if (pd.owned) {
            msg(p, "&e[Shop] Bạn đã sở hữu đệ tử rồi. Mỗi người chỉ mua được 1 con.");
            return;
        }
        if (!p.hasPermission(shopPermission())) {
            msg(p, cfg().getString("shop.need-rank-message", "&c[Shop] Bạn cần đạt rank Bạch Kim để mở shop đệ tử."));
            return;
        }
        double price = shopPrice();
        Object eco = economy();
        if (eco == null) {
            msg(p, "&c[Shop] Server chưa có plugin kinh tế (Vault). Báo admin.");
            return;
        }
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            double bal = ((Number) ecoClass.getMethod("getBalance", OfflinePlayer.class).invoke(eco, p)).doubleValue();
            if (bal < price) {
                msg(p, "&c[Shop] Không đủ tiền. Cần &e" + Util.fmt((long) price) + "&c, bạn có &e" + Util.fmt((long) bal) + "&c.");
                return;
            }
            Object resp = ecoClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class).invoke(eco, p, price);
            boolean ok = (Boolean) resp.getClass().getMethod("transactionSuccess").invoke(resp);
            if (!ok) {
                msg(p, "&c[Shop] Giao dịch thất bại, chưa bị trừ tiền.");
                return;
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Loi khi tru tien: " + ex);
            msg(p, "&c[Shop] Lỗi giao dịch, báo admin.");
            return;
        }
        pd.owned = true;
        saveData();
        msg(p, "&a[Shop] Mua đệ tử thành công! Dùng &e/detu goi &ađể gọi đệ tử.");
    }

    public void giveOwnership(Player p) {
        data(p.getUniqueId()).owned = true;
        saveData();
    }


    // ------------------------------------------------------------ hop the: cong mau

    private double fusionHpAmount(PlayerData pd) {
        if (!cfg().getBoolean("fusion.add-hp", true)) return 0;
        double amount = maxHp(pd) * cfg().getDouble("fusion.hp-multiplier", 1.0);
        return Math.min(amount, 1000.0);
    }

    public long fusionHpBonus(PlayerData pd) {
        return (long) fusionHpAmount(pd);
    }

    private void applyFusionHealth(Player p, boolean heal) {
        double amount = fusionHpAmount(data(p.getUniqueId()));
        AttributeInstance ai = p.getAttribute(Attribute.MAX_HEALTH);
        if (ai == null || amount <= 0) return;
        AttributeModifier existing = null;
        for (AttributeModifier mod : ai.getModifiers()) {
            if (mod.getKey().equals(fuseHpKey)) existing = mod;
        }
        if (existing != null && Math.abs(existing.getAmount() - amount) < 0.5) return;
        double before = existing == null ? 0 : existing.getAmount();
        if (existing != null) ai.removeModifier(existing);
        ai.addModifier(new AttributeModifier(fuseHpKey, amount, AttributeModifier.Operation.ADD_NUMBER));
        if (heal) {
            double max = ai.getValue();
            p.setHealth(Math.min(max, p.getHealth() + Math.max(0.0, amount - before)));
        }
    }

    private void removeFusionHealth(Player p) {
        AttributeInstance ai = p.getAttribute(Attribute.MAX_HEALTH);
        if (ai == null) return;
        for (AttributeModifier mod : new ArrayList<>(ai.getModifiers())) {
            if (mod.getKey().equals(fuseHpKey)) ai.removeModifier(mod);
        }
        double max = ai.getValue();
        if (p.getHealth() > max) p.setHealth(max);
    }

    // ------------------------------------------------------------ cai trang

    public static final class Costume {
        public final String id;
        public final String name;
        public final double percent;
        public final double price;
        public final String material;
        public final String skinValue;
        public final String skinSig;

        Costume(String id, String name, double percent, double price, String material, String skinValue, String skinSig) {
            this.id = id;
            this.name = name;
            this.percent = percent;
            this.price = price;
            this.material = material;
            this.skinValue = skinValue;
            this.skinSig = skinSig;
        }
    }

    public List<Costume> costumes() {
        List<Costume> out = new ArrayList<>();
        ConfigurationSection sec = cfg().getConfigurationSection("costumes");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection c = sec.getConfigurationSection(id);
                if (c == null) continue;
                out.add(new Costume(id, c.getString("name", id), c.getDouble("bonus-percent", 0), c.getDouble("price", 0),
                        c.getString("material", "LEATHER_CHESTPLATE"), c.getString("skin-value", ""), c.getString("skin-signature", "")));
            }
        }
        if (out.isEmpty()) {
            out.add(new Costume("trang1", "Cải trang Chiến Binh", 30, 1_000_000, "LEATHER_CHESTPLATE", "", ""));
            out.add(new Costume("trang2", "Cải trang Siêu Chiến Binh", 45, 5_000_000, "DIAMOND_CHESTPLATE", "", ""));
            out.add(new Costume("trang3", "Cải trang Thần Chiến", 60, 20_000_000, "NETHERITE_CHESTPLATE", "", ""));
        }
        return out;
    }

    public Costume costume(String id) {
        if (id == null) return null;
        for (Costume c : costumes()) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    public double costumeBonusPercent(PlayerData pd) {
        Costume c = costume(pd.costumeWorn);
        return c == null ? 0.0 : c.percent;
    }

    private boolean charge(Player p, double price) {
        if (price <= 0) return true;
        Object eco = economy();
        if (eco == null) {
            msg(p, "&c[Shop] Server chưa có plugin kinh tế (Vault). Báo admin.");
            return false;
        }
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            double bal = ((Number) ecoClass.getMethod("getBalance", OfflinePlayer.class).invoke(eco, p)).doubleValue();
            if (bal < price) {
                msg(p, "&c[Shop] Không đủ tiền. Cần &e" + Util.fmt((long) price) + "&c, bạn có &e" + Util.fmt((long) bal) + "&c.");
                return false;
            }
            Object resp = ecoClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class).invoke(eco, p, price);
            boolean ok = (Boolean) resp.getClass().getMethod("transactionSuccess").invoke(resp);
            if (!ok) {
                msg(p, "&c[Shop] Giao dịch thất bại, chưa bị trừ tiền.");
                return false;
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Loi khi tru tien: " + ex);
            msg(p, "&c[Shop] Lỗi giao dịch, báo admin.");
            return false;
        }
    }

    /** Bam vao cai trang: chua co thi mua (va mac luon), da co thi mac / coi. */
    public void costumeAction(Player p, String id) {
        PlayerData pd = data(p.getUniqueId());
        if (!canUse(p)) {
            msg(p, "&c[Cải trang] Bạn chưa sở hữu đệ tử.");
            return;
        }
        Costume c = costume(id);
        if (c == null) return;
        if (!pd.costumesOwned.contains(id)) {
            if (!charge(p, c.price)) return;
            pd.costumesOwned.add(id);
            saveData();
            msg(p, "&a[Cải trang] Đã mua &e" + c.name + "&a.");
        }
        if (id.equals(pd.costumeWorn)) {
            pd.costumeWorn = null;
            msg(p, "&e[Cải trang] Đã cởi &f" + c.name + "&e.");
        } else {
            pd.costumeWorn = id;
            msg(p, "&a[Cải trang] Đang mặc &e" + c.name + "&a: tăng &e" + String.format("%.0f", c.percent) + "% &atiềm năng / SM nhận được.");
        }
        refreshBody(p);
    }

    public void giveCostume(Player p, String id) {
        PlayerData pd = data(p.getUniqueId());
        pd.costumesOwned.add(id);
        saveData();
    }

    private void refreshBody(Player p) {
        Disciple d = active.get(p.getUniqueId());
        if (d == null || d.body == null) return;
        PlayerData pd = data(p.getUniqueId());
        applySkin(d.body, pd);
        refresh(d, p, pd);
    }

    // ------------------------------------------------------------ chet / doi the gioi / don rac

    public void onDeath(Player p) {
        Disciple d = active.remove(p.getUniqueId());
        if (d != null) removeBody(d); // giu nguyen co "summoned" de hoi sinh xong goi lai
    }

    public void onRespawn(Player p) {
        if (!data(p.getUniqueId()).summoned) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !p.isDead() && data(p.getUniqueId()).summoned) summon(p, true);
        }, 20L);
    }

    public void onWorldChange(Player p) {
        Disciple d = active.remove(p.getUniqueId());
        if (d == null) return;
        removeBody(d);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !p.isDead() && data(p.getUniqueId()).summoned) summon(p, true);
        }, 10L);
    }

    /** Xoa cac Mannequin do plugin tao ma khong con duoc theo doi. onlyOwner = null thi don tat ca. */
    public void sweep(UUID onlyOwner) {
        for (World w : Bukkit.getWorlds()) {
            for (Mannequin mq : w.getEntitiesByClass(Mannequin.class)) {
                String tag = mq.getPersistentDataContainer().get(bodyKey, PersistentDataType.STRING);
                if (tag == null) continue;
                if (onlyOwner != null && !tag.equals(onlyOwner.toString())) continue;
                if (bodyToOwner.containsKey(mq.getUniqueId())) continue;
                mq.remove();
            }
        }
    }

    public boolean dismiss(Player p) {
        Disciple d = active.remove(p.getUniqueId());
        data(p.getUniqueId()).summoned = false;
        if (d == null) return false;
        removeBody(d);
        msg(p, "&e[Đệ tử] &fĐã cất đệ tử vào Capsule.");
        return true;
    }

    private Location behind(Player p) {
        Location l = p.getLocation().clone();
        double yaw = Math.toRadians(l.getYaw());
        l.add(Math.sin(yaw) * 1.5, 0, -Math.cos(yaw) * 1.5);
        return l;
    }

    private void spawnBody(Disciple d, Player owner) {
        Location loc = behind(owner);
        World w = loc.getWorld();
        Mannequin m = w.spawn(loc, Mannequin.class);
        m.setPersistent(false);
        m.setInvulnerable(true);
        m.setGravity(false);
        m.setSilent(true);
        m.setImmovable(true);
        m.setCustomNameVisible(true);
        applySkin(m, data(owner.getUniqueId()));
        m.getPersistentDataContainer().set(bodyKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        d.body = m;
        bodyToOwner.put(m.getUniqueId(), owner.getUniqueId());
        refresh(d, owner, data(owner.getUniqueId()));
    }

    private void removeBody(Disciple d) {
        if (d.body != null) {
            bodyToOwner.remove(d.body.getUniqueId());
            d.body.remove();
            d.body = null;
        }
    }

    private void applySkin(Mannequin m, PlayerData pd) {
        String value = cfg().getString("skin.texture-value", "");
        String sig = cfg().getString("skin.texture-signature", "");
        String name = cfg().getString("skin.name", "");
        Costume cs = pd == null ? null : costume(pd.costumeWorn);
        if (cs != null && cs.skinValue != null && !cs.skinValue.isBlank()) {
            value = cs.skinValue;
            sig = cs.skinSig;
            name = "";
        }
        try {
            if (value != null && !value.isBlank()) {
                ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile();
                b.uuid(UUID.nameUUIDFromBytes("detu-skin".getBytes()));
                b.name("Goku");
                if (sig != null && !sig.isBlank()) {
                    b.addProperty(new ProfileProperty("textures", value, sig));
                } else {
                    b.addProperty(new ProfileProperty("textures", value));
                }
                m.setProfile(b.build());
            } else if (name != null && !name.isBlank()) {
                m.setProfile(ResolvableProfile.resolvableProfile().name(name).build());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Khong gan duoc skin: " + t);
        }
    }


    // ------------------------------------------------------------ chieu thuc (random khi mo khoa)

    static final class MoveDef {
        final String name;
        final String type; // beam | aoe | buff
        final double dmg;
        final double radius;
        final boolean xayda;

        MoveDef(String name, String type, double dmg, double radius, boolean xayda) {
            this.name = name;
            this.type = type;
            this.dmg = dmg;
            this.radius = radius;
            this.xayda = xayda;
        }
    }

    static final MoveDef[] MOVES = {
            new MoveDef("Antomic", "beam", 1.5, 0, true),
            new MoveDef("Kaioken", "buff", 2.0, 0, true),
            new MoveDef("Biến Khỉ", "aoe", 1.2, 4, true),
            new MoveDef("Chưởng khí", "beam", 1.0, 0, false),
            new MoveDef("Sóng năng lượng", "aoe", 1.0, 5, false),
            new MoveDef("Bùng nổ", "aoe", 1.3, 7, false)
    };

    private int rollMove(PlayerData pd) {
        double specialty = cfg().getDouble("race.specialty-weight", 3.0);
        double total = 0;
        double[] w = new double[MOVES.length];
        for (int i = 0; i < MOVES.length; i++) {
            boolean taken = false;
            for (int mv : pd.moves) if (mv == i) taken = true;
            if (taken) continue;
            w[i] = MOVES[i].xayda ? specialty : 1.0;
            total += w[i];
        }
        if (total <= 0) return 0;
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < MOVES.length; i++) {
            r -= w[i];
            if (w[i] > 0 && r <= 0) return i;
        }
        return 0;
    }

    private void ensureMoves(PlayerData pd, Player owner) {
        for (int i = 0; i < 3; i++) {
            if (pd.sm >= skillUnlock(i) && pd.moves[i] < 0) {
                pd.moves[i] = rollMove(pd);
                if (owner != null) {
                    msg(owner, "&d[Đệ tử] &fĐã mở khóa &eSkill " + (i + 2) + "&f: &b" + MOVES[pd.moves[i]].name + "&f!");
                }
            }
        }
    }

    public String skillLabel(PlayerData pd, int idx) {
        int mv = pd.moves[idx];
        return mv >= 0 ? MOVES[mv].name : "???";
    }

    private long hitGain(PlayerData pd) {
        double perSd = cfg().getDouble("sm.per-hit-per-sd", 7.5);
        double buff = cfg().getDouble("sm.buff", 2.0);
        return Math.max(1L, (long) (baseAttack(pd) * perSd * buff));
    }

    // ------------------------------------------------------------ stats

    public long skillUnlock(int idx) {
        return cfg().getLong("skills.skill" + (idx + 2) + ".unlock", 1L);
    }

    /** Tiem nang can cho lan nang tiep theo (dung chung cho HP va suc danh). */
    public long upgradeCost(boolean hp, PlayerData pd) {
        if (pd.nextCost > 0) return pd.nextCost;
        return Math.max(1L, cfg().getLong("upgrade.base-cost", 100));
    }

    public double upgradeMultiplier() {
        return Math.max(1.0, cfg().getDouble("upgrade.cost-multiplier", 1.25));
    }

    public long maxHp(PlayerData pd) {
        long per = Math.max(1L, cfg().getLong("sm.sm-per-bonus-hp", 100000));
        return 20L + pd.hpLevel + pd.sm / per;
    }

    public double baseAttack(PlayerData pd) {
        return cfg().getDouble("combat.base-damage", 2.0) + pd.atkLevel * cfg().getDouble("combat.damage-per-level", 1.0);
    }

    public double damage(PlayerData pd) {
        long per = Math.max(1L, cfg().getLong("combat.sm-per-bonus-damage", 1000000));
        return baseAttack(pd) + (double) (pd.sm / per);
    }

    private long killReward(double mobMaxHp) {
        return (long) (cfg().getLong("sm.per-kill-base", 200) + mobMaxHp * cfg().getDouble("sm.per-mob-hp", 20.0));
    }

    public void award(Player owner, long amount, boolean notify) {
        awardCore(owner, amount, notify, true);
    }

    /** Cong SM khong tinh thuong cai trang (dung cho lenh admin). */
    public void awardRaw(Player owner, long amount, boolean notify) {
        awardCore(owner, amount, notify, false);
    }

    private void awardCore(Player owner, long amount, boolean notify, boolean bonus) {
        if (amount <= 0) return;
        PlayerData pd = data(owner.getUniqueId());
        if (bonus && pd.costumeWorn != null) {
            double pct = costumeBonusPercent(pd);
            if (pct > 0) amount = Math.round(amount * (1.0 + pct / 100.0));
        }
        pd.sm += amount;
        pd.potential += amount;
        ensureMoves(pd, owner);
        autoUpgrade(owner, pd);
        if (notify) {
            showGain(owner, "&b+" + Util.fmt(amount) + " SM &7(" + Util.fmt(pd.sm) + ")");
        }
    }

    /** Hien dong +SM tren action bar va giu khoang 3 giay (dong ho hop the nhuong cho trong luc do). */
    private void showGain(Player p, String text) {
        long ms = (long) (cfg().getDouble("display.gain-seconds", 3.0) * 1000L);
        gainUntil.put(p.getUniqueId(), System.currentTimeMillis() + ms);
        p.sendActionBar(Util.color(text));
    }

    /** Dung tiem nang tu nang ngau nhien HP goc hoac suc danh goc cho den khi het tien. */
    private void autoUpgrade(Player owner, PlayerData pd) {
        double hpChance = cfg().getDouble("auto.hp-chance", 0.5);
        int hpUp = 0;
        int atkUp = 0;
        for (int guard = 0; guard < 200; guard++) {
            // boc ngau nhien 1 chi so roi GIU NGUYEN cho den khi du tiem nang de nang no
            if (pd.autoTarget != 1 && pd.autoTarget != 2) {
                pd.autoTarget = ThreadLocalRandom.current().nextDouble() < hpChance ? 1 : 2;
            }
            boolean pickHp = pd.autoTarget == 1;
            long cost = upgradeCost(pickHp, pd);
            if (pd.potential < cost) break; // chua du, doi tiep (khong doi sang chi so re hon)
            pd.potential -= cost;
            pd.nextCost = Math.max(cost + 1, (long) Math.ceil(cost * upgradeMultiplier()));
            if (pickHp) {
                pd.hpLevel++;
                hpUp++;
            } else {
                pd.atkLevel++;
                atkUp++;
            }
            pd.autoTarget = 0;
        }
        if (hpUp + atkUp == 0) return;

        int[] pend = autoPending.computeIfAbsent(owner.getUniqueId(), k -> new int[2]);
        pend[0] += hpUp;
        pend[1] += atkUp;
        long now = System.currentTimeMillis();
        Long last = autoLast.get(owner.getUniqueId());
        if (last == null || now - last >= 5000L) {
            autoLast.put(owner.getUniqueId(), now);
            msg(owner, "&d[Đệ tử] &fĐột phá chỉ số! &cHP gốc +" + pend[0] + " &7(cấp " + pd.hpLevel + ")"
                    + " &f| &6Sức đánh gốc +" + pend[1] + " &7(cấp " + pd.atkLevel + ")");
            pend[0] = 0;
            pend[1] = 0;
        }
    }

    // ------------------------------------------------------------ hop the

    public boolean isFused(UUID id) {
        PlayerData pd = data(id);
        return pd.fused && System.currentTimeMillis() < pd.fuseUntil;
    }

    public long fuseRemaining(PlayerData pd) {
        return pd.fused ? Math.max(0L, pd.fuseUntil - System.currentTimeMillis()) : 0L;
    }

    public long fuseCooldownRemaining(PlayerData pd) {
        return Math.max(0L, pd.fuseCooldownUntil - System.currentTimeMillis());
    }

    private long fuseDurationMs() {
        return (long) (cfg().getDouble("fusion.duration-seconds", 600) * 1000L);
    }

    private long fuseCooldownMs() {
        return (long) (cfg().getDouble("fusion.cooldown-seconds", 600) * 1000L);
    }

    /** Het gio hop the thi tu dong tach va bat dau thoi gian hoi. */
    private void checkFusion() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData pd = data.get(p.getUniqueId());
            if (pd == null || !pd.fused) continue;
            if (now >= pd.fuseUntil) {
                endFusion(p, true);
            } else {
                applyFusionHealth(p, false);
                Long gu = gainUntil.get(p.getUniqueId());
                if (gu == null || now >= gu) {
                    p.sendActionBar(Util.color("&dHợp thể còn &f" + Util.fmtTime(pd.fuseUntil - now)));
                }
            }
        }
    }

    private void endFusion(Player p, boolean timeout) {
        PlayerData pd = data(p.getUniqueId());
        pd.fused = false;
        removeFusionHealth(p);
        pd.fuseUntil = 0;
        pd.fuseCooldownUntil = System.currentTimeMillis() + fuseCooldownMs();
        msg(p, "&d[Hợp thể] &f" + (timeout ? "Đã hết thời gian hợp thể." : "Đã tách hợp thể.")
                + " Phải đợi &e" + Util.fmtTime(fuseCooldownMs()) + " &fmới hợp thể lại được.");
        if (pd.summoned) summon(p, true);
    }

    /** Moi ~1 giay: chop dien + tia sang quanh nguoi dang hop the. */
    private void fusionEffects() {
        boolean lightning = cfg().getBoolean("fusion.lightning-effect", false);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isFused(p.getUniqueId())) continue;
            World w = p.getWorld();
            Location c = p.getLocation().add(0, 1.0, 0);
            w.spawnParticle(Particle.ELECTRIC_SPARK, c, 60, 0.6, 0.9, 0.6, 0.35);
            w.spawnParticle(Particle.END_ROD, c, 25, 0.5, 0.9, 0.5, 0.08);
            if (lightning) w.strikeLightningEffect(p.getLocation());
        }
    }

    public void resetFusionCooldown(Player p) {
        data(p.getUniqueId()).fuseCooldownUntil = 0;
    }

    public boolean fusionAffectsPlayers() {
        return cfg().getBoolean("fusion.affect-players", false);
    }

    public void toggleFuse(Player p) {
        PlayerData pd = data(p.getUniqueId());
        if (!canUse(p)) {
            msg(p, "&c[Đệ tử] Bạn chưa sở hữu đệ tử.");
            return;
        }
        if (pd.fused) {
            endFusion(p, false);
            return;
        }
        long cd = fuseCooldownRemaining(pd);
        if (cd > 0) {
            msg(p, "&c[Hợp thể] Đang hồi chiêu, còn &e" + Util.fmtTime(cd) + " &cnữa mới hợp thể được.");
            return;
        }
        pd.fused = true;
        pd.fuseUntil = System.currentTimeMillis() + fuseDurationMs();
        applyFusionHealth(p, true);
        Disciple d = active.remove(p.getUniqueId());
        if (d != null) removeBody(d);
        msg(p, "&d[Hợp thể] &fĐệ tử hợp thể với Sư phụ trong &e" + Util.fmtTime(fuseDurationMs())
                + "&f! Mỗi đòn đấm cộng thêm &e" + String.format("%.1f", damage(pd)) + " &fsát thương và &c+" + (long) fusionHpAmount(pd) + " &fmáu tối đa (bằng máu của đệ tử).");
    }

    /** Sat thuong cua su phu khi hop the: cong them (add) hoac thay bang (set) suc danh cua de tu. */
    public double fusedDamage(Player p, double base) {
        double bonus = damage(data(p.getUniqueId()));
        String mode = cfg().getString("fusion.damage-mode", "add");
        return mode.equalsIgnoreCase("set") ? Math.max(base, bonus) : base + bonus;
    }

    public void fusedHit(Player p) {
        award(p, hitGain(data(p.getUniqueId())), false);
    }

    public void setMode(Player p, Mode m) {
        PlayerData pd = data(p.getUniqueId());
        pd.mode = m;
        Disciple d = active.get(p.getUniqueId());
        if (d != null && m == Mode.FOLLOW) d.target = null;
        msg(p, "&a[Đệ tử] Trạng thái: " + m.color() + m.label());
    }

    // ------------------------------------------------------------ events from listener

    /** Su phu danh nguoi choi khac thi de tu dua theo danh nguoi do (ATTACK / PROTECT). */
    public void ownerAttacked(Player owner, LivingEntity victim) {
        if (!cfg().getBoolean("pvp.assist", true)) return;
        if (!(victim instanceof Player vp) || vp.equals(owner)) return;
        Disciple d = active.get(owner.getUniqueId());
        if (d == null) return;
        if (data(owner.getUniqueId()).mode == Mode.FOLLOW) return;
        if (vp.getGameMode() == GameMode.CREATIVE || vp.getGameMode() == GameMode.SPECTATOR) return;
        d.target = vp;
        d.pvpUntil = System.currentTimeMillis() + (long) (cfg().getDouble("pvp.assist-seconds", 8.0) * 1000L);
    }

    public void notifyAttacked(Player victim, LivingEntity attacker) {
        Disciple d = active.get(victim.getUniqueId());
        if (d == null) return;
        PlayerData pd = data(victim.getUniqueId());
        if (pd.mode == Mode.FOLLOW) return;
        if (isBody(attacker.getUniqueId())) return;
        if (attacker instanceof Player) return;
        if (attacker.isValid() && !attacker.isDead()) d.target = attacker;
    }

    public void ownerKill(Player owner, LivingEntity mob) {
        double share = cfg().getDouble("sm.owner-kill-share", 0.5);
        long amount = (long) (killReward(mob.getMaxHealth()) * share);
        award(owner, amount, true);
    }

    // ------------------------------------------------------------ tick

    private void tick() {
        tickCount++;
        for (Disciple d : new ArrayList<>(active.values())) {
            Player owner = Bukkit.getPlayer(d.owner);
            if (owner == null || !owner.isOnline()) {
                active.remove(d.owner);
                removeBody(d);
                continue;
            }
            if (owner.isDead()) continue;
            if (d.body == null || !d.body.isValid()) {
                removeBody(d);
                spawnBody(d, owner);
            }
            tickDisciple(d, owner);
        }
        if (tickCount % 20 == 0) checkFusion();
        int flashTicks = Math.max(1, (int) (cfg().getDouble("fusion.flash-seconds", 1.0) * 20));
        if (tickCount % flashTicks == 0) fusionEffects();
        if (tickCount % 200 == 0) sweep(null);
        if (tickCount % 6000 == 0) saveData();
    }

    private void tickDisciple(Disciple d, Player owner) {
        PlayerData pd = data(d.owner);
        long now = System.currentTimeMillis();

        if (d.target != null) {
            LivingEntity t = d.target;
            boolean bad = !t.isValid() || t.isDead() || t.getWorld() != d.body.getWorld()
                    || t.getLocation().distanceSquared(d.body.getLocation()) > 40 * 40;
            if (bad) d.target = null;
        }
        if (d.target instanceof Player && now > d.pvpUntil) d.target = null;
        if (pd.mode == Mode.FOLLOW) d.target = null;
        if (d.target == null && pd.mode != Mode.FOLLOW && tickCount % 10 == 0) {
            d.target = findTarget(d, owner, pd);
        }

        Location goal;
        double stop;
        if (d.target != null) {
            goal = d.target.getLocation();
            stop = 1.6;
        } else {
            goal = owner.getLocation();
            stop = cfg().getDouble("follow.stop-distance", 3.0);
        }
        move(d, owner, goal, stop);

        if (d.target != null) combat(d, owner, pd, now);

        if (tickCount % 20 == 0) refresh(d, owner, pd);
    }

    private LivingEntity findTarget(Disciple d, Player owner, PlayerData pd) {
        double r = pd.mode == Mode.ATTACK
                ? cfg().getDouble("combat.search-radius", 14)
                : cfg().getDouble("combat.protect-radius", 6);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Location bl = d.body.getLocation();
        for (Entity e : owner.getNearbyEntities(r, r, r)) {
            if (!(e instanceof Monster m)) continue;
            if (!m.isValid() || m.isDead()) continue;
            if (m.getWorld() != bl.getWorld()) continue;
            double dist = m.getLocation().distanceSquared(bl);
            if (dist < bestDist) {
                bestDist = dist;
                best = m;
            }
        }
        return best;
    }

    private void move(Disciple d, Player owner, Location goal, double stop) {
        Mannequin b = d.body;
        Location cur = b.getLocation();
        World w = cur.getWorld();
        double tp = cfg().getDouble("follow.teleport-distance", 24);

        if (w != owner.getWorld()) {
            removeBody(d);
            spawnBody(d, owner);
            return;
        }
        if (cur.distanceSquared(owner.getLocation()) > tp * tp) {
            b.teleport(behind(owner));
            return;
        }

        double dx = goal.getX() - cur.getX();
        double dz = goal.getZ() - cur.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = cur.getYaw();
        if (dist > 0.05) yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        double nx = cur.getX();
        double nz = cur.getZ();
        double ny = cur.getY();

        if (dist > stop) {
            double speed = dist > 8 ? cfg().getDouble("follow.run-speed", 0.40) : cfg().getDouble("follow.speed", 0.28);
            double step = Math.min(speed, dist - stop + 0.05);
            double tx = cur.getX() + dx / dist * step;
            double tz = cur.getZ() + dz / dist * step;
            int by = (int) Math.floor(cur.getY() + 0.01);
            Block feet = w.getBlockAt((int) Math.floor(tx), by, (int) Math.floor(tz));
            Block head = feet.getRelative(BlockFace.UP);
            Block above = head.getRelative(BlockFace.UP);
            if (feet.isPassable() && head.isPassable()) {
                nx = tx;
                nz = tz;
            } else if (head.isPassable() && above.isPassable()) {
                nx = tx;
                nz = tz;
                ny = by + 1;
            }
        }

        // trong luc: neu khoi duoi chan trong thi roi, neu dac thi dung tren mat
        int fy = (int) Math.floor(ny);
        Block under = w.getBlockAt((int) Math.floor(nx), fy - 1, (int) Math.floor(nz));
        if (under.isPassable()) {
            ny = Math.max(ny - 0.5, w.getMinHeight());
        } else {
            ny = fy;
        }

        b.teleport(new Location(w, nx, ny, nz, yaw, 0f));
    }

    private void combat(Disciple d, Player owner, PlayerData pd, long now) {
        LivingEntity t = d.target;
        if (t == null || t.getWorld() != d.body.getWorld()) return;
        double dist = d.body.getLocation().distance(t.getLocation());

        if (pd.mode == Mode.ATTACK) {
            for (int i = 2; i >= 0; i--) {
                if (pd.moves[i] < 0 || pd.sm < skillUnlock(i) || now < d.skillReady[i]) continue;
                if (dist > cfg().getDouble("skills.skill" + (i + 2) + ".range", 10)) continue;
                long cd = (long) (cfg().getDouble("skills.skill" + (i + 2) + ".cooldown", 10) * 1000L);
                d.skillReady[i] = now + cd;
                castSkill(d, owner, pd, t, i, now);
                return;
            }
        }

        double range = cfg().getDouble("combat.attack-range", 2.5);
        long cdMs = cfg().getLong("combat.attack-cooldown-ms", 800);
        if (dist <= range && now - d.lastAttack >= cdMs) {
            d.lastAttack = now;
            d.body.swingMainHand();
            hit(d, owner, t, damage(pd) * (now < d.buffUntil ? d.buffFactor : 1.0));
        }
    }

    private void hit(Disciple d, Player owner, LivingEntity t, double dmg) {
        if (t instanceof Player) {
            t.damage(dmg * cfg().getDouble("pvp.damage-multiplier", 1.0), d.body);
            if (t.isDead()) d.target = null;
            return; // danh nguoi choi khong cong SM (tranh farm bang nick phu)
        }
        double maxHp = t.getMaxHealth();
        t.damage(dmg, d.body);
        long gained = hitGain(data(owner.getUniqueId()));
        if (t.isDead()) {
            gained += killReward(maxHp);
            d.target = null;
            award(owner, gained, true);
        } else {
            award(owner, gained, true);
        }
    }

    private void castSkill(Disciple d, Player owner, PlayerData pd, LivingEntity t, int idx, long now) {
        MoveDef mv = MOVES[pd.moves[idx]];
        String base = "skills.skill" + (idx + 2);
        double mult = cfg().getDouble(base + ".multiplier", 3.0);
        double radius = mv.radius > 0 ? mv.radius : cfg().getDouble(base + ".radius", 0);

        d.body.swingMainHand();
        World w = t.getWorld();

        if (mv.type.equals("buff")) {
            d.buffUntil = now + 10_000L;
            d.buffFactor = mv.dmg;
            w.spawnParticle(Particle.FLAME, d.body.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.05);
            msg(owner, "&d[Đệ tử] &fKích hoạt &e" + mv.name + "&f! Sát thương tăng x" + mv.dmg + " trong 10 giây.");
            return;
        }

        double dmg = damage(pd) * mult * mv.dmg;
        Location from = d.body.getLocation().add(0, 1.4, 0);
        Location to = t.getLocation().add(0, t.getHeight() / 2.0, 0);
        Vector dir = to.toVector().subtract(from.toVector());
        double len = dir.length();
        if (len > 0.1) {
            dir.normalize();
            for (double s = 0; s < len; s += 0.5) {
                w.spawnParticle(Particle.FLAME, from.clone().add(dir.clone().multiply(s)), 2, 0.05, 0.05, 0.05, 0.0);
            }
        }
        w.spawnParticle(Particle.CRIT, to, 40, 0.6, 0.6, 0.6, 0.2);

        List<LivingEntity> victims = new ArrayList<>();
        victims.add(t);
        if (mv.type.equals("aoe") && radius > 0) {
            for (Entity e : w.getNearbyEntities(t.getLocation(), radius, radius, radius)) {
                if (e instanceof Monster mo && mo != t && mo.isValid() && !mo.isDead()) victims.add(mo);
            }
        }

        long gained = 0;
        long perHit = hitGain(pd);
        for (LivingEntity v : victims) {
            if (v instanceof Player) {
                v.damage(dmg * cfg().getDouble("pvp.damage-multiplier", 1.0), d.body);
                continue;
            }
            double maxHp = v.getMaxHealth();
            v.damage(dmg, d.body);
            gained += perHit;
            if (v.isDead()) gained += killReward(maxHp);
        }
        award(owner, gained, true);
        if (t.isDead()) d.target = null;
        msg(owner, "&d[Đệ tử] &fSử dụng &e" + mv.name + "&f!");
    }

    private void refresh(Disciple d, Player owner, PlayerData pd) {
        if (d.body == null) return;
        ensureMoves(pd, owner);
        String name = cfg().getString("display-name", "&eĐệ tử của &6%owner%").replace("%owner%", owner.getName());
        d.body.customName(Util.color(name));
        Costume cs = costume(pd.costumeWorn);
        d.body.setDescription(Util.color("&bSM &f" + Util.fmt(pd.sm) + " &8| &c❤ &f" + maxHp(pd)
                + (cs != null ? " &8| &d" + cs.name : "")));
    }

    // ------------------------------------------------------------ misc

    public void msg(Player p, String s) {
        p.sendMessage(Util.color(s));
    }

    public void reloadConfig() {
        plugin.reloadConfig();
    }
}
