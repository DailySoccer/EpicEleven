package model;

import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TemplateContest implements JongoId, Initializer {
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

    public String name;

    public int minInstances;        // Minimum desired number of instances that we want running at any given moment
    public int maxEntries;

    public int salaryCap;
    public int entryFee;
    public PrizeType prizeType;
    public List<Integer> prizes;

    public Date startDate;

    public List<ObjectId> templateMatchEventIds;

    public Date activationAt;
    public Date createdAt;

    public TemplateContest() { }

    public void Initialize() {
        state = State.OFF;
    }

    public ObjectId getId() {
        return templateContestId;
    }

    public boolean isOff()      { return (state == State.OFF); }
    public boolean isActive()   { return (state == State.ACTIVE); }
    public boolean isLive()     { return (state == State.LIVE); }
    public boolean isHistory()  { return (state == State.HISTORY); }


    public List<TemplateMatchEvent> getTemplateMatchEvents() {
        return TemplateMatchEvent.findAll(templateMatchEventIds);
    }

    public List<MatchEvent> getMatchEvents() {
        return MatchEvent.findAllFromTemplate(templateMatchEventIds);
    }

    static public TemplateContest findOne(ObjectId templateContestId) {
        return Model.templateContests().findOne("{_id : #}", templateContestId).as(TemplateContest.class);
    }

    static public TemplateContest findOne(String templateContestId) {
        TemplateContest theTemplateContest = null;
        if (ObjectId.isValid(templateContestId)) {
            theTemplateContest = Model.templateContests().findOne("{_id : #}", new ObjectId(templateContestId)).as(TemplateContest.class);
        }
        return theTemplateContest;
    }

    static public List<TemplateContest> findAll() {
        return ListUtils.asList(Model.templateContests().find().as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllActive() {
        return ListUtils.asList(Model.templateContests().find("{state: \"ACTIVE\"}").as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllFromContests(Iterable<Contest> contests) {

        ArrayList<ObjectId> templateContestIds = new ArrayList<>();

        for (Contest contest : contests) {
            templateContestIds.add(contest.templateContestId);
        }

        return ListUtils.asList(Model.findObjectIds(Model.templateContests(), "_id", templateContestIds).as(TemplateContest.class));
    }

    /**
     *  Eliminar un template contest y sus dependencias
     */
    public static boolean remove(TemplateContest templateContest) {
        Logger.info("remove TemplateContest({}): {}", templateContest.templateContestId, templateContest.name);

        // Eliminar el template contest
        try {
            WriteResult result = Model.templateContests().remove("{_id: #, state: \"OFF\"}", templateContest.templateContestId);
            if (result.getN() == 0) {
                throw new RuntimeException(String.format("Template Contest: Error removing %s", templateContest.templateContestId.toString()));
            }
        }
        catch(MongoException e) {
            Logger.error("WTF 6799", e);
        }

        return true;
    }

    public void instantiate() {
        assert(isActive());

        Logger.info("instantiate: {}: activationAt: {}", name, GlobalDate.formatDate(activationAt));

        instantiateMatchEvents();

        // Cuantas instancias tenemos creadas?
        long instances = Model.contests().count("{templateContestId: #}", templateContestId);

        for(long i=instances; i<minInstances; i++) {
            Contest contest = new Contest(this);

            // TODO: <MockData> Cuándo activar o no esta "funcionalidad"
            MockData.addContestEntries(contest, contest.maxEntries-1);

            Model.contests().withWriteConcern(WriteConcern.SAFE).insert(contest);
        }

        // Incluir los premios del torneo (ya no se podrá cambiar la forma de calcularlo)
        prizes = getPrizes(prizeType, maxEntries, getPrizePool());
        Model.templateContests().update(templateContestId).with("{$set: {prizes: #}}", prizes);
    }

    private void instantiateMatchEvents() {
        List<TemplateMatchEvent> templateMatchEvents = getTemplateMatchEvents();

        for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
            MatchEvent matchEvent = MatchEvent.findOneFromTemplate(templateMatchEvent.getId());
            if (matchEvent == null) {
                MatchEvent.createFromTemplate(templateMatchEvent);
            }
        }
    }

    /**
     *  Estado del partido
     */
    public boolean isStarted() {
        boolean started = false;

        // El Contest ha comenzado si cualquiera de sus partidos ha comenzado
        Iterable<TemplateMatchEvent> matchEventResults = TemplateMatchEvent.findAll(templateMatchEventIds);
        for(TemplateMatchEvent templateMatchEvent : matchEventResults) {
            if (templateMatchEvent.isStarted()) {
                started = true;
                break;
            }
        }

        return started;
    }

    public static boolean isStarted(String templateContestId) {
        return findOne(new ObjectId(templateContestId)).isStarted();
    }

    public boolean isFinished() {
        boolean finished = true;

        // El Contest ha terminado si TODOS sus partidos han terminado
        for (TemplateMatchEvent templateMatchEvent : TemplateMatchEvent.findAll(templateMatchEventIds)) {
            if (!templateMatchEvent.isFinished()) {
                finished = false;
                break;
            }
        }

        return finished;
    }

    public static boolean isFinished(String templateContestId) {
        return findOne(new ObjectId(templateContestId)).isFinished();
    }

    public void givePrizes() {
        List<Contest> contests = Contest.findAllFromTemplateContest(templateContestId);

        for (Contest contest : contests) {
            contest.givePrizes();
        }
    }

    public int getPositionPrize(int position) {
        return (position < prizes.size()) ? prizes.get(position) : 0;
    }

    private int getPrizePool() {
        return (int)((maxEntries * entryFee) * 0.90f);
    }

    static private List<Integer> getPrizes(PrizeType prizeType, int maxEntries, int prizePool) {
        List<Integer> prizes = new ArrayList<>();

        if      (prizeType.equals(PrizeType.FREE)) {

        }
        else if (prizeType.equals(PrizeType.WINNER_TAKES_ALL)) {
            prizes.add(prizePool);
        }
        else if (prizeType.equals(PrizeType.TOP_3_GET_PRIZES)) {
            prizes.add((int) (prizePool * 0.5f));
            prizes.add((int) (prizePool * 0.3f));
            prizes.add((int) (prizePool * 0.2));
        }
        else if (prizeType.equals(PrizeType.TOP_THIRD_GET_PRIZES)) {
            // A cuantos repartiremos premios?
            int third = maxEntries / 3;

            // Para hacer el reparto proporcional asignaremos puntos inversamente a la posición
            // Más puntos cuanto más baja su posición. Para repartir a "n" usuarios: 1º = (n) pts / 2º = (n-1) pts / 3º = (n-2) pts / ... / nº = 1 pts

            // Averiguar los puntos totales a repartir para saber cuánto vale el punto: n * (n+1) / 2  (suma el valor de "n" numeros)
            int totalPoints = third * (third + 1) / 2;
            int prizeByPoint = (int) (prizePool / totalPoints);

            // A cada posición le damos el premio (sus puntos se corresponden con su posición "invertida": p.ej. para repartir a 6 usuarios: el 1º tiene 6 puntos, el 2º tiene 5 puntos, etc)
            int totalPrize = prizePool;
            for (int i = third; i > 0; i--) {
                int prize = prizeByPoint * i;
                prizes.add(prize);
                totalPrize -= prize;
            }
            // Si queda algo, ¿se lo damos al primero?
            if (totalPrize > 0) {
                prizes.set(0, prizes.get(0) + totalPrize);
            }
        }
        else if (prizeType.equals(PrizeType.FIFTY_FIFTY)) {
            int mid = maxEntries / 2;
            for (int i = 0; i < mid; i++) {
                prizes.add((int) (prizePool / mid));
            }
        }

        return prizes;
    }
}
