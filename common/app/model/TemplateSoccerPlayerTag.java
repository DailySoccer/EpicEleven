package model;

import java.util.Arrays;

public enum TemplateSoccerPlayerTag {
    ACTIVO  ("activo");

    public final String text;

    TemplateSoccerPlayerTag(String c) {
        text = c;
    }

    public static boolean isValid(String c) {
        return Arrays.asList(TemplateSoccerPlayerTag.values()).contains(c);
    }

    public String toString() {
        return text;
    }

    public static TemplateSoccerPlayerTag getEnum(String c) {
        for (TemplateSoccerPlayerTag templateSoccerPlayerTag : TemplateSoccerPlayerTag.values()){
            if (templateSoccerPlayerTag.text.equals(c)) {
                return templateSoccerPlayerTag;
            }
        }
        return null;
    }
}
