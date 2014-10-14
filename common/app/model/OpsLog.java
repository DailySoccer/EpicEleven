package model;

import com.google.common.collect.ImmutableMap;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;

import java.util.Date;

public class OpsLog {
    public static String TYPE_TEAM = "TEAM";
    public static String TYPE_PLAYER = "PLAYER";
    public static String TYPE_MATCHEVENT = "MATCHEVENT";
    public static String TYPE_POINTS_TRANSLATION = "POINTS_TRANSLATION";
    public static String OP_NEW = "NEW";
    public static String OP_CHANGE = "CHANGE";
    public static String OP_DELETE = "DELETE";
    public static String OP_INVALIDATE = "INVALIDATE";

    public String type;         // En qué documento se realiza la Op
    public String subType;      // Opcional: Puede utilizarse para proporcionar información extra (contexto, ...)
    public String op;           // Operación (creación, modificación, eliminación, inválido...)
    public Object object;       // Estructura con la información que se usó para la Op

    public Date createdAt;

    private OpsLog(String type, String subType, String op, Object object) {
        this.type = type;
        this.subType = subType;
        this.op = op;
        this.createdAt = GlobalDate.getCurrentDate();
        this.object = object;
    }

    public static <T> void opNew(T aObject) {
        opNew(null, aObject);
    }

    public static <T> void opChange(T aObject) {
        opChange(null, aObject);
    }

    public static <T> void opInvalidate(T aObject) {
        opInvalidate(null, aObject);
    }

    public static <T> void opDelete(T aObject) {
        opDelete(null, aObject);
    }

    public static <T> void opNew(String subType, T aObject) {
        add(getType(aObject), subType, OpsLog.OP_NEW, aObject);
    }

    public static <T> void opChange(String subType, T aObject) {
        add(getType(aObject), subType, OpsLog.OP_CHANGE, aObject);
    }

    public static <T> void opInvalidate(String subType, T aObject) {
        add(getType(aObject), subType, OpsLog.OP_INVALIDATE, aObject);
    }

    public static <T> void opDelete(String subType, T aObject) {
        add(getType(aObject), subType, OpsLog.OP_DELETE, aObject);
    }

    public static <T> void opChange(String type, String subType, T aObject) {
        add(type, subType, OpsLog.OP_CHANGE, aObject);
    }

    public static void add(String theType, String theSubtype, String theOp, Object aObject) {
        Model.opsLog().insert(new OpsLog(theType, theSubtype, theOp, aObject));
    }

    private static <T> String getType(T aObject) {
        String aType = null;
        if (aObject.getClass().equals(OptaTeam.class)) {
            aType = TYPE_TEAM;
        }
        else if (aObject.getClass().equals(OptaPlayer.class)) {
            aType = TYPE_PLAYER;
        }
        else if (aObject.getClass().equals(OptaMatchEvent.class)) {
            aType = TYPE_MATCHEVENT;
        }
        else if (aObject.getClass().equals(PointsTranslation.class)) {
            aType = TYPE_POINTS_TRANSLATION;
        }
        return aType;
    }
}
