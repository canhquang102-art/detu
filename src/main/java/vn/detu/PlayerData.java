package vn.detu;

public final class PlayerData {
    public long sm = 0;
    public int hpLevel = 0;
    public int atkLevel = 0;
    public Mode mode = Mode.FOLLOW;
    public boolean summoned = false;
    // chieu da random cho skill 2,3,4 (-1 = chua mo)
    public final int[] moves = {-1, -1, -1};
}
