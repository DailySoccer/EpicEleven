package controllers.admin;

import java.util.ArrayList;
import java.util.List;

public class FlashMessage {
    public String alertType;
    public String text;

    public static List<FlashMessage> list = new ArrayList<>();

    public FlashMessage(String type, String text) {
        this.alertType = type;
        this.text = text;
    }

    public static void clear () { list.clear(); }

    public static void info(String text) {
        list.add( new FlashMessage("info", text) );
    }

    public static void success(String text) {
        list.add( new FlashMessage("success", text) );
    }

    public static void warning(String text) {
        list.add( new FlashMessage("warning", text) );
    }

    public static void danger(String text) {
        list.add( new FlashMessage("danger", text) );
    }
}
