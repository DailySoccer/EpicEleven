package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.BulkWriteOperation;
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
    @Id
    public ObjectId templateContestId;

    @JsonView(JsonViews.Extended.class)
    public ContestState state = ContestState.OFF;

    public String name;

    @JsonView(JsonViews.NotForClient.class)
    public int minInstances;        // Minimum desired number of instances that we want running at any given moment

    public int maxEntries;

    public int salaryCap;
    public int entryFee;
    public PrizeType prizeType;

    @JsonView(JsonViews.Extended.class)
    public List<Integer> prizes;

    public Date startDate;

    @JsonView(JsonViews.Extended.class)
    public List<ObjectId> templateMatchEventIds;

    @JsonView(JsonViews.NotForClient.class)
    public Date activationAt;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public TemplateContest() { }

    public TemplateContest(String name, int minInstances, int maxEntries, int salaryCap,
                            int entryFee, PrizeType prizeType, Date activationAt,
                            List<String> templateMatchEvents) {

        this.name = name;
        this.minInstances = minInstances;
        this.maxEntries = maxEntries;
        this.salaryCap = salaryCap;
        this.entryFee = entryFee;
        this.prizeType = prizeType;
        this.activationAt = activationAt;

        Date startDate = null;
        this.templateMatchEventIds = new ArrayList<ObjectId>();
        for (String templateMatchEventId : templateMatchEvents) {
            TemplateMatchEvent templateMatchEvent = TemplateMatchEvent.findOneFromOptaId(templateMatchEventId);
            this.templateMatchEventIds.add(templateMatchEvent.templateMatchEventId);

            if (startDate == null || templateMatchEvent.startDate.before(startDate)) {
                startDate = templateMatchEvent.startDate;
            }
        }
        this.startDate = startDate;
    }

    public void Initialize() {
        state = ContestState.OFF;
    }

    public ObjectId getId() {
        return templateContestId;
    }

    public boolean isOff()      { return (state == ContestState.OFF); }
    public boolean isActive()   { return (state == ContestState.ACTIVE); }
    public boolean isLive()     { return (state == ContestState.LIVE); }
    public boolean isHistory()  { return (state == ContestState.HISTORY); }


    public List<TemplateMatchEvent> getTemplateMatchEvents() {
        return TemplateMatchEvent.findAll(templateMatchEventIds);
    }

    public List<MatchEvent> getMatchEvents() {
        return MatchEvent.findAllFromTemplates(templateMatchEventIds);
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

    static public List<TemplateContest> findAllFromContests(List<Contest> contests) {

        ArrayList<ObjectId> templateContestIds = new ArrayList<>(contests.size());

        for (Contest contest : contests) {
            templateContestIds.add(contest.templateContestId);
        }

        return ListUtils.asList(Model.findObjectIds(Model.templateContests(), "_id", templateContestIds).as(TemplateContest.class));
    }

    static public List<TemplateContest> findAllByActivationAt(Date activationAt) {
        return ListUtils.asList(Model.templateContests()
                                     .find("{state: \"OFF\", activationAt: {$lte: #}}", activationAt)
                                     .as(TemplateContest.class));
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

    public Contest instantiateContest(boolean addMockDataUsers) {
        Contest contest = new Contest(this);
        contest.prizes = getPrizes(prizeType, maxEntries, getPrizePool());
        contest.state = ContestState.ACTIVE;

        // TODO: <MockData> Cuándo activar o no esta "funcionalidad"
        if (addMockDataUsers) {
            MockData.addContestEntries(contest, contest.maxEntries - 1);
        }

        Model.contests().withWriteConcern(WriteConcern.SAFE).insert(contest);

        return contest;
    }

    public void instantiate() {

        Logger.info("TemplateContest.instantiate: {}: activationAt: {}", name, GlobalDate.formatDate(activationAt));

        // TODO: Evaluar si este cambio de estado tiene que estar al principio o al final de esta funcion.
        state = ContestState.ACTIVE;

        instantiateMatchEvents();

        // Cuantas instancias tenemos creadas?
        long instances = Model.contests().count("{templateContestId: #}", templateContestId);

        for (long i=instances; i < minInstances; i++) {
            instantiateContest(true);
        }

        // Incluir los premios del torneo (ya no se podrá cambiar la forma de calcularlo)
        prizes = getPrizes(prizeType, maxEntries, getPrizePool());
        Model.templateContests().update(templateContestId).with("{$set: {prizes: #}}", prizes);

        // Cuando hemos acabado de instanciar nuestras dependencias, nos ponemos en activo
        Model.templateContests().update("{_id: #, state: \"OFF\"}", templateContestId).with("{$set: {state: \"ACTIVE\"}}");
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
        for(TemplateMatchEvent templateMatchEvent : TemplateMatchEvent.findAll(templateMatchEventIds)) {
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
        List<MatchEvent> matchEvents = getMatchEvents();

        // Actualizamos los rankings de cada contest
        BulkWriteOperation bulkOperation = Model.contests().getDBCollection().initializeOrderedBulkOperation();
         for (Contest contest : contests) {
            contest.updateRanking(bulkOperation, this, matchEvents);
        }
        bulkOperation.execute();

        // Damos los premios según la posición en el Ranking
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
/*

TABLAS DE PREMIOS DE FANDUEL

{
 "winner": {
    "3":[1],
    "4":[1],
    "5":[1],
    "6":[1],
    "7":[1],
    "8":[1],
    "9":[1],
    "10":[1],
    "11":[1],
    "12":[1],
    "13":[1],
    "14":[1],
    "15":[1],
    "16":[1],
    "17":[1],
    "18":[1],
    "19":[1],
    "20":[1]
 },
 "top3": {
    "3":[0.62963,0.37037],
    "4":[0.38889,0.33333,0.27778],
    "5":[0.44444,0.33333,0.22222],
    "6":[0.48148,0.31481,0.2037],
    "7":[0.50794,0.31746,0.1746],
    "8":[0.5,0.30556,0.19444],
    "9":[0.49383,0.30864,0.19753],
    "10":[0.5,0.3,0.2],
    "11":[0.50505,0.30303,0.19192],
    "12":[0.5,0.2963,0.2037],
    "13":[0.50427,0.2906,0.20513],
    "14":[0.50794,0.28571,0.20635],
    "15":[0.5037,0.28889,0.20741],
    "16":[0.50694,0.28472,0.20833],
    "17":[0.5098,0.28758,0.20261],
    "18":[0.50617,0.2963,0.19753],
    "19":[0.50292,0.30409,0.19298],
    "20":[0.5,0.31111,0.18889]
 },
 "3rd":{
    "3":[1],
    "4":[0.72222,0.27778],
    "5":[0.68889,0.31111],
    "6":[0.66667,0.33333],
    "7":[0.65079,0.34921],
    "8":[0.5,0.30556,0.19444],
    "9":[0.49383,0.30864,0.19753],
    "10":[0.5,0.3,0.2],
    "11":[0.45455,0.28283,0.16162,0.10101],
    "12":[0.44444,0.27778,0.16667,0.11111],
    "13":[0.4359,0.2735,0.17094,0.11966],
    "14":[0.37302,0.22222,0.18254,0.14286,0.07937],
    "15":[0.37037,0.22222,0.18519,0.14074,0.08148],
    "16":[0.36806,0.22222,0.1875,0.13889,0.08333],
    "17":[0.3268,0.21569,0.1634,0.13072,0.09804,0.06536],
    "18":[0.33333,0.22222,0.16049,0.12963,0.09259,0.06173],
    "19":[0.33333,0.22222,0.15789,0.12865,0.09357,0.06433],
    "20":[0.33333,0.22222,0.15556,0.12222,0.1,0.06667]
 }
}

Algoritmo "codificado":

commissionFactor:10
n=this.get("selectedPrivateLeagueSizeId");var j=parseInt(n,10)
p=this.get("selectedPrivateLeagueEntryFee");var g=parseFloat(p)
var h=(1-(this.commissionFactor*0.01))*g;var d=j*h;var m=d
for(k=0;k<l.length;k++)
    o=l[k]*d;o=Math.round(o*2)*0.5;if(o>m){o=m}if(k+1==l.length){if(m>o){o=m}}m-=o

Algoritmo "interpretado":

commissionFactor:10
max_entries (j) = selectedPrivateLeagueSizeId
entry_fee (g) = selectedPrivateLeagueEntryFee

entry_fee_con_comision (h) = (1-(commissionFactor*0.01))*entry_fee;

dinero (d) = max_entries * entry_fee_con_comision
resto (m) = dinero

para cada multiplicador (l[k])
    premio (o) = multiplicador * dinero
    premio = Math.round(premio * 2) * 0.5
    if (premio > resto) {
      premio = resto;
    }
    if (es_ultimo_premio) {
      if (resto > premio) {
        premio = resto;
      }
    }
    resto -= premio

*/