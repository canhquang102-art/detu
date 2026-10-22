package vn.detu;

public final class PlayerData {
    public long sm = 0;
    public long potential = 0; // tiem nang: dung de nang HP / suc danh
    public int hpLevel = 0;
    public int atkLevel = 0;
    public Mode mode = Mode.FOLLOW;
    public boolean summoned = false;
    public boolean owned = false; // da mua de tu
    public long nextCost = 0; // tiem nang can cho lan nang tiep theo (nhan 1.25 sau moi lan nang)
    public int autoTarget = 0; // chi so da boc de tu nang tiep: 0 = chua boc, 1 = HP, 2 = suc danh
    public String costumeWorn = null; // id cai trang dang mac
    public final java.util.Set<String> costumesOwned = new java.util.HashSet<>();
    public boolean fused = false; // dang hop the voi su phu
    public long fuseUntil = 0;         // thoi diem (ms) het hop the
    public long fuseCooldownUntil = 0; // thoi diem (ms) duoc hop the lai
    // chieu da random cho skill 2,3,4 (-1 = chua mo)
    public final int[] moves = {-1, -1, -1};
}
