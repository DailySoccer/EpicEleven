package model;

import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.jongo.Find;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.*;

public class TemplateContest implements JongoId {
    public enum State {
        OFF(0),
        ACTIVE(1),
        LIVE(2),
        HISTORY(3);

        public final int id;

        State(int id) {
            this.id = id;
        }
    }

    @Id
    public ObjectId templateContestId;

    public State state = State.OFF;

    public String name;             // Auto-gen if blank
    public String postName;         // This goes in parenthesis

    public int minInstances;        // Minimum desired number of instances that we want running at any given moment
    public int maxEntries;

    public int salaryCap;
    public int entryFee;
    public PrizeType prizeType;

    public Date startDate;

    public List<ObjectId> templateMatchEventIds;  // We rather have it here that normalize it in a N:N table

    public Date activationAt;
    public Date createdAt;

    public TemplateContest() {
    }

    public ObjectId getId() {
        return templateContestId;
    }

    public boolean isActive()   { return (state == State.ACTIVE); }
    public boolean isLive()     { return (state == State.LIVE); }
    public boolean isHistory()  { return (state == State.HISTORY); }

    public List<TemplateMatchEvent> templateMatchEvents() {
        Iterable<TemplateMatchEvent> templateMatchEventResults = TemplateMatchEvent.find("_id", templateMatchEventIds);
        return ListUtils.asList(templateMatchEventResults);
    }

    static public TemplateContest find(ObjectId templateContestId) {
        return Model.templateContests().findOne("{_id : #}", templateContestId).as(TemplateContest.class);
    }

    /**
     *  Query de la lista de Template Contests correspondientes a una lista de contests
     */
    static public Find find(List<Contest> contests) {
        List<ObjectId> contestObjectIds = new ArrayList<>(contests.size());
        for (Contest contest: contests) {
            contestObjectIds.add(contest.templateContestId);
        }
        return Model.findObjectIds(Model.templateContests(), "_id", contestObjectIds);
    }

    public static Iterable<TemplateContest> find(String fieldId, List<ObjectId> idList) {
        return Model.findObjectIds(Model.templateContests(), fieldId, idList).as(TemplateContest.class);
    }

    /**
     *  Eliminar un template contest y sus dependencias
     */
    public static boolean remove(TemplateContest templateContest) {
        Logger.info("remove TemplateContest({}): {}", templateContest.templateContestId, templateContest.name);

        // Buscar los Contests que instancian el template contest
        Iterable<Contest> contestResults = Model.contests().find("{templateContestId : #}", templateContest.templateContestId).as(Contest.class);
        List<Contest> contestList = ListUtils.asList(contestResults);

        for (Contest contest : contestList) {
            Contest.remove(contest);
        }

        // Eliminar el template contest
        Model.templateContests().remove(templateContest.templateContestId);

        return true;
    }

    public void instantiate() {
        // No instanciamos template contests que no esten activos
        if (!isActive())
            return;

        Logger.info("instantiate: {}: activationAt: {}", name, activationAt );

        // Cuantas instancias tenemos creadas?
        long instances = Model.contests().count("{templateContestId: #}", templateContestId);

        for(long i=instances; i<minInstances; i++) {
            Contest contest = new Contest(this);
            Model.contests().withWriteConcern(WriteConcern.SAFE).insert(contest);
        }
    }

    /**
     *  Estado del partido
     */
    public boolean isStarted() {
        boolean started = false;

        // El Contest ha comenzado si cualquiera de sus partidos ha comenzado
        Iterable<TemplateMatchEvent> templateContestResults = TemplateMatchEvent.find("_id", templateMatchEventIds);
        for(TemplateMatchEvent templateMatchEvent : templateContestResults) {
            if (templateMatchEvent.isStarted()) {
                started = true;
                break;
            }
        }

        return started;
    }

    public static boolean isStarted(String templateContestId) {
        TemplateContest templateContest = find(new ObjectId(templateContestId));
        return templateContest.isStarted();
    }

    public boolean isFinished() {
        boolean finished = true;

        // El Contest ha terminado si TODOS sus partidos han terminado
        Iterable<TemplateMatchEvent> templateContestResults = TemplateMatchEvent.find("_id", templateMatchEventIds);
        for(TemplateMatchEvent templateMatchEvent : templateContestResults) {
            if (!templateMatchEvent.isFinished()) {
                finished = false;
                break;
            }
        }

        return finished;
    }

    public static boolean isFinished(String templateContestId) {
        TemplateContest templateContest = find(new ObjectId(templateContestId));
        return templateContest.isFinished();
    }
}
