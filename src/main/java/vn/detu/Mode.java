package vn.detu;

public enum Mode {
    ATTACK("Tấn công", "&c"),
    PROTECT("Bảo vệ", "&e"),
    FOLLOW("Đi theo", "&a");

    private final String label;
    private final String color;

    Mode(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public String color() {
        return color;
    }

    public static Mode parse(String s) {
        if (s == null) return null;
        switch (s.toLowerCase()) {
            case "tancong":
            case "attack":
                return ATTACK;
            case "baove":
            case "protect":
                return PROTECT;
            case "ditheo":
            case "follow":
                return FOLLOW;
            default:
                return null;
        }
    }
}
