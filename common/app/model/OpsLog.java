package model;

import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;

import java.util.Date;

public class OpsLog {
    public static String TYPE_IMPORT = "IMPORT";
    public static String SUBTYPE_TEAM = "TEAM";
    public static String SUBTYPE_PLAYER = "PLAYER";
    public static String SUBTYPE_MATCHEVENT = "MATCHEVENT";
    public static String OP_NEW = "NEW";
    public static String OP_CHANGE = "CHANGE";
    public static String OP_INVALIDATE = "INVALIDATE";

    public String type;
    public String subType;
    public String op;
    public Date createdAt;
    public Object object;

    OpsLog(String type, String subType, String op, Object object) {
        this.type = type;
        this.subType = subType;
        this.op = op;
        this.createdAt = GlobalDate.getCurrentDate();
        this.object = object;
    }

    public static void importOpta(String op, OptaTeam optaTeam) {
        add(TYPE_IMPORT, SUBTYPE_TEAM, op, optaTeam);
    }

    public static void importOpta(String op, OptaPlayer optaPlayer) {
        add(TYPE_IMPORT, SUBTYPE_PLAYER, op, optaPlayer);
    }

    public static void importOpta(String op, OptaMatchEvent optaMatchEvent) {
        add(TYPE_IMPORT, SUBTYPE_MATCHEVENT, op, optaMatchEvent);
    }

    public static void add(String theType, String theSubtype, String theOp, Object aObject) {
        Model.opsLog().insert(new OpsLog(theType, theSubtype, theOp, aObject));
    }

}
