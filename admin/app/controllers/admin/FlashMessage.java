package controllers.admin;

public class FlashMessage {
    public String alertType;
    public String text;

    public FlashMessage(String type, String text) {
        this.alertType = type;
        this.text = text;
    }

    public static FlashMessage info(String text) {
        return new FlashMessage("info", text);
    }

    public static FlashMessage success(String text) {
        return new FlashMessage("success", text);
    }

    public static FlashMessage warning(String text) {
        return new FlashMessage("warning", text);
    }

    public static FlashMessage danger(String text) {
        return new FlashMessage("danger", text);
    }
}
