package model;

public enum TemplateSoccerPlayerTag {
    ACTIVE;

    TemplateSoccerPlayerTag() { }

    public static boolean isValid(String c) {
        for (TemplateSoccerPlayerTag val : TemplateSoccerPlayerTag.values()) {
            if (val.toString().equals(c)) {
                return true;
            }
        }
        return false;
    }
}
