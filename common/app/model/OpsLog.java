package model;

import com.google.common.collect.ImmutableMap;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;

import java.util.Date;

public class OpsLog {
    public static String SUBTYPE_TEAM = "TEAM";
    public static String SUBTYPE_PLAYER = "PLAYER";
    public static String SUBTYPE_MATCHEVENT = "MATCHEVENT";
    public static String OP_NEW = "NEW";
    public static String OP_CHANGE = "CHANGE";
    public static String OP_DELETE = "DELETE";
    public static String OP_INVALIDATE = "INVALIDATE";

    public String type;
    public String subType;
    public String op;
    public Object object;

    public Date createdAt;

    private OpsLog(String type, String subType, String op, Object object) {
        this.type = type;
        this.subType = subType;
        this.op = op;
        this.createdAt = GlobalDate.getCurrentDate();
        this.object = object;
    }

    public static <T> void opNew(String type, T opta) {
        add(type, getSubType(opta), OpsLog.OP_NEW, opta);
    }

    public static <T> void opChange(String type, T opta) {
        add(type, getSubType(opta), OpsLog.OP_CHANGE, opta);
    }

    public static <T> void opInvalidate(String type, T opta) {
        add(type, getSubType(opta), OpsLog.OP_INVALIDATE, opta);
    }

    public static <T> void opDelete(String type, T opta) {
        add(type, getSubType(opta), OpsLog.OP_DELETE, opta);
    }

    public static <T> void opChange(String type, String subType, T opta) {
        add(type, subType, OpsLog.OP_CHANGE, opta);
    }

    public static void add(String theType, String theSubtype, String theOp, Object aObject) {
        Model.opsLog().insert(new OpsLog(theType, theSubtype, theOp, aObject));
    }

    private static <T> String getSubType(T aObject) {
        String subType = "UNKNOWN";
        if (aObject.getClass().equals(OptaTeam.class)) {
            subType = SUBTYPE_TEAM;
        }
        else if (aObject.getClass().equals(OptaPlayer.class)) {
            subType = SUBTYPE_PLAYER;
        }
        else if (aObject.getClass().equals(OptaMatchEvent.class)) {
            subType = SUBTYPE_MATCHEVENT;
        }
        return subType;
    }
}
