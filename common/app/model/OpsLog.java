package model;

import model.opta.OptaCompetition;
import model.opta.OptaMatchEvent;
import model.opta.OptaPlayer;
import model.opta.OptaTeam;

import java.util.Date;

public class OpsLog {
    public static enum Op {
        NEW,
        CHANGE,
        DELETE,
        INVALIDATE
    }

    public static enum ActingOn {
        UNKNOWN,
        TEAM,
        PLAYER,
        MATCHEVENT,
        POINTS_TRANSLATION,
        COMPETITION,
        TEMPLATE_CONTEST
    }

    public Op op;               // Operación (creación, modificación, eliminación, inválido...)
    public String opSubType;    // Opcional: Puede utilizarse para proporcionar información extra (contexto, ...)
    public ActingOn actingOn;   // En qué documento se realiza la Op
    public Object object;       // Estructura con la información que se usó para la Op

    public Date createdAt;

    private OpsLog(Op op, String opSubType, ActingOn actingOn, Object object) {
        this.op = op;
        this.opSubType = opSubType;
        this.actingOn = actingOn;
        this.object = object;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    // ---------------------------
    // OBJECT
    // ---------------------------
    public static void onNew(Object aObject) {
        add(Op.NEW, null, getActingOn(aObject), aObject);
    }

    public static void onChange(Object aObject) {
        add(Op.CHANGE, null, getActingOn(aObject), aObject);
    }

    public static void onDelete(Object aObject) {
        add(Op.DELETE, null, getActingOn(aObject), aObject);
    }

    public static void onInvalidate(Object aObject) {
        add(Op.INVALIDATE, null, getActingOn(aObject), aObject);
    }

    // ---------------------------
    // OP_SUBTYPE + OBJECT
    // ---------------------------
    public static void onNew(String opSubType, Object aObject) {
        add(Op.NEW, opSubType, getActingOn(aObject), aObject);
    }

    public static void onChange(String opSubType, Object aObject) {
        add(Op.CHANGE, opSubType, getActingOn(aObject), aObject);
    }

    public static void onDelete(String opSubType, Object aObject) {
        add(Op.DELETE, opSubType, getActingOn(aObject), aObject);
    }

    public static void onInvalidate(String opSubType, Object aObject) {
        add(Op.INVALIDATE, opSubType, getActingOn(aObject), aObject);
    }

    // ---------------------------
    // OP_SUBTYPE + OBJECT
    // ---------------------------
    public static void onChange(ActingOn actingOn, Object aObject) {
        add(Op.CHANGE, null, actingOn, aObject);
    }

    // ---------------------------
    // OP_SUBTYPE + ACTING_ON + OBJECT
    // ---------------------------
    public static void onChange(String opSubType, ActingOn actingOn, Object aObject) {
        add(Op.CHANGE, opSubType, actingOn, aObject);
    }

    // ---------------------------
    // GENERIC
    // ---------------------------
    private static void add(Op theOp, String theOpSubtype, ActingOn actingOn, Object aObject) {
        Model.opsLog().insert(new OpsLog(theOp, theOpSubtype, actingOn, aObject));
    }

    private static ActingOn getActingOn(Object aObject) {
        ActingOn aType = ActingOn.UNKNOWN;
        if (aObject.getClass().equals(OptaTeam.class)) {
            aType = ActingOn.TEAM;
        }
        else if (aObject.getClass().equals(OptaPlayer.class)) {
            aType = ActingOn.PLAYER;
        }
        else if (aObject.getClass().equals(OptaMatchEvent.class)) {
            aType = ActingOn.MATCHEVENT;
        }
        else if (aObject.getClass().equals(PointsTranslation.class)) {
            aType = ActingOn.POINTS_TRANSLATION;
        }
        else if (aObject.getClass().equals(OptaCompetition.class)) {
            aType = ActingOn.COMPETITION;
        }
        else if (aObject.getClass().equals(TemplateContest.class)) {
            aType = ActingOn.TEMPLATE_CONTEST;
        }
        return aType;
    }
}
