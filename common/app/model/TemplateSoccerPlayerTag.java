package model;

public enum TemplateSoccerPlayerTag {
    ACTIVE;


    TemplateSoccerPlayerTag() {

    }

    public static boolean isValid(String c) {
        return TemplateSoccerPlayerTag.getEnum(c) != null;
    }


    public static TemplateSoccerPlayerTag getEnum(String c) {
        return TemplateSoccerPlayerTag.valueOf(c.toUpperCase());
    }
}
