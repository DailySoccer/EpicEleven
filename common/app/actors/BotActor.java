package actors;

import akka.actor.UntypedActor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import model.*;
import org.bson.types.ObjectId;
import play.Logger;
import play.Play;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import utils.ListUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

//       Sacar todos los bots cuando queda menos de X tiempo para empezar (poco a poco)
//       Si queremos que puedan realmente jugar en concursos: http://en.wikipedia.org/wiki/Knapsack_problem
//
public class BotActor extends UntypedActor {

    public enum Personality {
        BERSERKER,
        PRODUCTION
    }


    public static class BotMsg {
        public String msg;
        public String userId;
        public Object param;

        public BotMsg(String m, String u, Object p) { msg = m; userId = u; param = p; }
    }


    public BotActor(int botActorId, Personality pers) {
        _botActorId = botActorId;
        _personality = pers;
        _targetUrl = Play.application().configuration().getString("botActor.targetUrl");
    }

    private String composeUrl(String suffix) {
        return String.format("%s/%s", _targetUrl, suffix);
    }

    private String getFirstName() {
        return "Bototron";
    }

    private String getLastName() {
        return String.format("%04d v1", _botActorId);
    }

    private String getFullName() {
        return getFirstName() + " " + getLastName();
    }

    private String getNickName() {
        return _NICKNAMES.get(_botActorId);
    }

    private String getEmail() {
        return String.format("bototron%04d@bototron.com", _botActorId);
    }


    @Override public void preStart() throws Exception {
        super.preStart();

        // Lanzando en preStart una excepcion nuestro supervisor no intentara volvernos a encender
        if (_botActorId >= _NICKNAMES.size()) {
            throw new RuntimeException("WTF 2967 Bototron no puede comenzar por falta de _NICKNAMES");
        }

        _numTicks = 0;
    }

    @Override
    public void onReceive(Object msg) {

        if (msg instanceof BotMsg) {
            onReceive((BotMsg) msg);
        } else {
            onReceive((String) msg);
        }
    }

    private void onReceive(String msg) {

        switch (msg) {
            case "NextPersonality":
                List<Personality> personalities = Arrays.asList(Personality.values());
                int nextIndex = personalities.indexOf(_personality) + 1;
                if (nextIndex >= personalities.size()) {
                    nextIndex = 0;
                }
                _personality = personalities.get(nextIndex);
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void onReceive(BotMsg msg) {

        switch (msg.msg) {
            case "Tick":
                try {
                    if (_user == null) {
                        tryLogin();
                    } else {
                        switch (_personality) {
                            case BERSERKER:
                                onTickBerserker();
                                break;
                            default:
                                onTickProduction((float)msg.param);
                                break;
                        }
                    }
                }
                catch (TimeoutException exc) {
                    Logger.info("{} Timeout 1026, probablemente el servidor esta saturado...", getFullName());
                }

                _numTicks++;

                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void tryLogin() throws TimeoutException {
        // Vemos si tenemos nuestro usuario _user adquirido, si no, lo preparamos
        if (!login()) {
            signup();

            if (login()) {
                // Vamos a asegurnos de que el profile del bot tiene los datos como queremos
                changeUserProfile();
            }
            else {
                Logger.error("WTF 5466 {} no pudo hacer signup + login", getFullName());
            }
        }
    }

    void onTickBerserker() throws TimeoutException {
        // Vemos los concursos activos en los que no estamos ya metidos y nos metemos en todos ellos en el mismo tick, a lo berserker
        List<Contest> notEntered = filterContestByNotEntered(getActiveContests(), null);

        // El servidor nos puede cambiar de concurso. Vamos guardando aqui los cambios para no volver a intentar entrar
        // durante el bucle
        List<String> enteredContestIds = new ArrayList<>();

        for (Contest contest : notEntered) {
            if (!enteredContestIds.contains(contest.contestId.toString())) {
                String enteredContestId = enterContest(contest);

                if (enteredContestId != null && !enteredContestId.equals(contest.contestId.toString())) {
                    enteredContestIds.add(enteredContestId);
                }
            }
        }
    }

    void onTickProduction(float averageEnteredContests) throws TimeoutException {
        List<Contest> notEntered = filterContestByNotEntered(getActiveContests(), null);
        List<Contest> myActiveContests = getMyActiveContests();
        int diffContests = 0;

        // En cada tick el bot solo entrara en 1 concurso, siempre que no superemos ya la media de concursos entrados
        // por nuestros hermanos. Con esto conseguimos que todos los bots entren en el mismo num de concursos (+-1).
        // Ademas, esperamos al menos 2 ticks para dar tiempo al average a estar bien calculado.
        if (myActiveContests.size() <= averageEnteredContests && _numTicks > 2) {
            for (Contest contest : notEntered) {
                if (shouldEnter(contest)) {
                    if (enterContest(contest) != null) {    // Si conseguimos entrar...
                        diffContests++;
                    }
                    break;
                }
            }
        }

        // Nos salimos de concursos donde ya hay demasiada gente (u otros bots)
        for (Contest contest : myActiveContests) {
            if (shouldLeave(contest)) {
                leaveContest(contest);
                diffContests--;
            }
        }

        // Comunicamos a nuestro padre en cuantos concursos estamos ahora mismo
        getSender().tell(new BotMsg("CurrentEnteredContests", _user.userId.toString(), myActiveContests.size() + diffContests), getSelf());
    }

    List<Contest> getMyActiveContests() throws TimeoutException {
        List<Contest> ret;
        JsonNode jsonNode = get("get_my_contests").findValue("contests_0");

        if (jsonNode != null) {
            ret = fromJSON(jsonNode.toString(), new TypeReference<List<Contest>>() {});
        }
        else {
            Logger.error("{} get_my_contests returned empty", getFullName());
            ret = new ArrayList<>();
        }

        return ret;
    }

    private boolean shouldLeave(Contest contest) throws TimeoutException {
        boolean bRet = false;
        int excessBots = contest.getNumEntries() - (contest.maxEntries - getGoalFreeSlots(contest));

        if (excessBots > 0) {
            // Tenemos que conseguir que todos los bots esten de acuerdo en quien debe salir. Asi que usamos simplemente
            // el orden en nuestra lista. Yo que prioridad tengo -> en que posicion estoy en la lista
            List<String> botsInContest = getBotsNicknamesInContest(contest);

            // Aseguramos que la lista de los bots en el concurso esta en el mismo orden que nuestros nicknames
            Collections.sort(botsInContest, new Comparator<String>() {
                @Override public int compare(String o1, String o2) {
                    return _NICKNAMES.indexOf(o1) - _NICKNAMES.indexOf(o2);
                }
            });

            int myPosition = botsInContest.indexOf(getNickName());

            if (myPosition < 0) {
                throw new RuntimeException(String.format("WTF 7999 %s no esta en el concurso %s", getFullName(), contest.contestId.toString()));
            }

            if (myPosition < excessBots) {
                bRet = true;
            }
        }

        return bRet;
    }

    private void leaveContest(Contest contest) throws TimeoutException {
        Logger.debug("{} leaveContest {}", getFullName(), contest.contestId);

        for (ContestEntry contestEntry : contest.contestEntries) {
            if (contestEntry.userId.equals(_user.userId)) {
                post("cancel_contest_entry", String.format("contestEntryId=%s", contestEntry.contestEntryId));
                break;
            }
        }
    }

    private List<String> getBotsNicknamesInContest(Contest contest) throws TimeoutException {
        List<String> ret = new ArrayList<>();

        // Hacemos un request al server para obtener los nicknames de los participantes
        List<UserInfo> usersInfo = getUsersInfoInContest(contest);

        for (ContestEntry contestEntry : contest.contestEntries) {
            UserInfo userInfo = findUserInfoInContestEntries(contestEntry, usersInfo);

            // Es posible que entre que pedimos get_my_contests y get_contest_info la lista haya cambiado, asi que
            // podemos no encontrar el UserInfo de una ContestEntry. Por eso, tenemos que checkear con null
            if (userInfo != null) {
                int index = _NICKNAMES.indexOf(userInfo.nickName);
                if (index != -1) {
                    ret.add(_NICKNAMES.get(index));
                }
            }
        }

        return ret;
    }

    private UserInfo findUserInfoInContestEntries(ContestEntry contestEntry, List<UserInfo> usersInfo) {
        UserInfo ret = null;
        for (UserInfo userInfo : usersInfo) {
            if (userInfo.userId.equals(contestEntry.userId)) {
                ret = userInfo;
                break;
            }
        }
        return ret;
    }

    private int getNumFreeSlots(Contest contest) {
        return contest.maxEntries - contest.getNumEntries();
    }

    private boolean shouldEnter(Contest contest) {
        return getNumFreeSlots(contest) > getGoalFreeSlots(contest);
    }

    private int getGoalFreeSlots(Contest contest) {
        int goalFreeSlots = -1;

        // Queremos que haya una pequeña variacion en el numero de slots libres, pero tambien queremos que no haya
        // oscilaciones, es decir, que para el mismo concurso todos los bots esten de acuerdo en cuantos slots libres
        // hay que dejar
        Random localRandom = new Random(contest.contestId.hashCode());

        if (contest.maxEntries <= 2) {
            // Vamos a entrar solo en un 33% de los 1vs1 a ver que tal queda
            if (localRandom.nextInt(3) == 0) {
                goalFreeSlots = 1;
            }
            else {
                goalFreeSlots = 2;
            }
        }
        else if (contest.maxEntries <= 5) {
            goalFreeSlots = 2 + localRandom.nextInt(2);    // 2 o 3
        }
        else if (contest.maxEntries <= 10) {
            goalFreeSlots = 2 + localRandom.nextInt(3);    // 2, 3 o 4
        }
        else if (contest.maxEntries <= 20) {
            goalFreeSlots = 3 + localRandom.nextInt(4);    // 3, 4, 5 o 6
        }
        else {
            goalFreeSlots = 5 + localRandom.nextInt(5);    // entre 5 y 9
        }

        if (goalFreeSlots == -1) { // Sanity check
            throw new RuntimeException("WTF 4491");
        }

        return goalFreeSlots;
    }

    private boolean login() throws TimeoutException {

        _user = null;

        JsonNode jsonNode = post("login", String.format("email=%s&password=uoyeradiputs3991", getEmail()));

        _sessionToken = extractStringValue(jsonNode, "sessionToken");

        if (_sessionToken != null) {
            jsonNode = get("get_user_profile");
            _user = fromJSON(jsonNode.toString(), new TypeReference<User>() {});
        }

        return _user != null;
    }

    private void signup() throws TimeoutException {
        JsonNode jsonNode = post("signup", String.format("firstName=%s&lastName=%s&nickName=%s&email=%s&password=uoyeradiputs3991",
                                                         getFirstName(), getLastName(), getNickName(), getEmail()));

        if (jsonNode == null) {
            Logger.error("WTF 4005 {} signup returned empty", getFullName());
        }
    }


    private void changeUserProfile() throws TimeoutException {
        JsonNode jsonNode = post("change_user_profile", String.format("firstName=%s&lastName=%s&nickName=%s&email=%s",
                                                                 getFirstName(), getLastName(), getNickName(), getEmail()));

        if (jsonNode == null) {
            Logger.error("WTF 4002 {} changeUserProfile returned empty", getFullName());
        }
    }

    private List<Contest> getActiveContests() throws TimeoutException {

        List<Contest> ret = null;
        JsonNode jsonNode = get("get_active_contests").findValue("contests");

        if (jsonNode != null) {
            ret = fromJSON(jsonNode.toString(), new TypeReference<List<Contest>>() { });
        }
        else {
            Logger.error("WTF 4001 {} getActiveContests returned empty", getFullName());
        }

        return ret == null? new ArrayList<Contest>() : ret;
    }

    private String enterContest(Contest contest) throws TimeoutException {
        JsonNode jsonNode = get(String.format("get_public_contest/%s", contest.contestId)).findValue("soccer_players");
        String enteredContestId = null;

        if (jsonNode != null) {
            // Debemos hacer to.do nuestro proceso con los datos de salario, equipo, etc que vienen en los Instances y no en los Templates.
            List<InstanceSoccerPlayer> instanceSoccerPlayers = contest.instanceSoccerPlayers;
            List<TemplateSoccerPlayer> soccerPlayers = fromJSON(jsonNode.toString(), new TypeReference<List<TemplateSoccerPlayer>>() { });

            // Simplemente "parcheamos" los templates con los datos de los instances
            copyInstancesToTemplates(instanceSoccerPlayers, soccerPlayers);

            List<TemplateSoccerPlayer> lineup = GenerateLineup.quickAndDirty(soccerPlayers, contest.salaryCap);

            // Verificar que se haya generado un lineup correcto
            if (lineup.size() == 11) {
                enteredContestId = addContestEntry(lineup, contest.contestId);
            }
            else {
                Logger.error("WTF 3561: {} enterContest: lineup invalid: {} players", getFullName(), lineup.size());
            }
        }
        else {
            Logger.error("WTF 4000 {} enterContest returned empty", getFullName());
        }

        return enteredContestId;
    }

    private String addContestEntry(List<TemplateSoccerPlayer> lineup, ObjectId contestId) throws TimeoutException {

        String idList = new ObjectMapper().valueToTree(ListUtils.stringListFromObjectIdList(ListUtils.convertToIdList(lineup))).toString();
        JsonNode jsonNode = post("add_contest_entry", String.format("contestId=%s&soccerTeam=%s", contestId.toString(), idList));

        // Nosotros podemos pedir un contestId, pero el servidor puede elegir meternos en otro
        String enteredContestId = null;

        if (jsonNode != null) {
            JsonNode error = jsonNode.findValue("error");

            if (error == null) {
                enteredContestId = extractStringValue(jsonNode, "contestId");
            }
            else {
                Logger.error("WTF 4006 {} addContestEntry produjo en un error en el servidor {}", getFullName(), error.toString());
            }
        }
        else {
            Logger.error("WTF 4007 {} addContestEntry returned empty", getFullName());
        }

        return enteredContestId;
    }

    private List<UserInfo> getUsersInfoInContest(Contest contest) throws TimeoutException {
        List<UserInfo> ret;
        JsonNode jsonNode = get(String.format("get_contest_info/%s", contest.contestId)).findValue("users_info");

        if (jsonNode != null) {
            ret = fromJSON(jsonNode.toString(), new TypeReference<List<UserInfo>>() { });
        }
        else {
            Logger.error("WTF 4008 {} enterContest returned empty", getFullName());
            ret = new ArrayList<>();
        }

        return ret;
    }

    void copyInstancesToTemplates(List<InstanceSoccerPlayer> instanceSoccerPlayers, List<TemplateSoccerPlayer> soccerPlayers) {
        for (InstanceSoccerPlayer ins : instanceSoccerPlayers) {
            boolean bFound = false;
            for (TemplateSoccerPlayer sp : soccerPlayers) {
                if (sp.templateSoccerPlayerId.equals(ins.templateSoccerPlayerId)) {
                    sp.salary = ins.salary;
                    sp.fieldPos = ins.fieldPos;
                    sp.templateTeamId = ins.templateSoccerTeamId;

                    bFound = true;
                    break;
                }
            }
            if (!bFound) {
                throw new RuntimeException("WTF 6556 Hemos encontrado un InstanceSoccerPlayer sin TemplateSoccerPlayer");
            }
        }
    }

    private List<Contest> filterContestByNotEntered(List<Contest> contests, List<Contest> entered) {
        List<Contest> ret = new ArrayList<>();

        for (Contest contest : contests) {
            if (!contest.containsContestEntryWithUser(_user.userId)) {
                ret.add(contest);
            }
            else if (entered != null) {
                entered.add(contest);
            }
        }

        return ret;
    }

    private JsonNode post(final String url, String params) throws TimeoutException {
        WSRequestHolder requestHolder = WS.url(composeUrl(url));

        F.Promise<WSResponse> response = requestHolder.setContentType("application/x-www-form-urlencoded")
                                                      .setHeader("X-Session-Token", _sessionToken).post(params);

        F.Promise<JsonNode> jsonPromise = response.map(
                new F.Function<WSResponse, JsonNode>() {
                    public JsonNode apply(WSResponse response) {
                        try {
                            return response.asJson();
                        }
                        catch (Exception exc) {
                            Logger.debug("WTF 1280 {} el servidor devolvio Json incorrecto: {}, {}", getFullName(), url, response.getStatusText());
                            return JsonNodeFactory.instance.objectNode();
                        }
                    }
                }
        );

        return jsonPromise.get(5000, TimeUnit.MILLISECONDS);
    }

    private JsonNode get(String url) throws TimeoutException {
        WSRequestHolder requestHolder = WS.url(composeUrl(url));

        F.Promise<WSResponse> response = requestHolder.setHeader("X-Session-Token", _sessionToken).get();

        F.Promise<JsonNode> jsonPromise = response.map(
                new F.Function<WSResponse, JsonNode>() {
                    public JsonNode apply(WSResponse response) {
                        try {
                            return response.asJson();
                        }
                        catch (Exception exc) {
                            Logger.debug("WTF 1279 {} el servidor devolvio Json incorrecto: {}", getFullName(), response.getStatusText());
                            return JsonNodeFactory.instance.objectNode();
                        }
                    }
                }
        );

        return jsonPromise.get(5000, TimeUnit.MILLISECONDS);
    }

    private static <T> T fromJSON(final String json, final TypeReference<T> type) {
        T ret = null;

        try {
            ret = new ObjectMapper().readValue(json, type);
        } catch (Exception exc) {
            Logger.debug("WTF 2229", exc);
        }
        return ret;
    }

    // Cuando el servidor nos devuelve una String, lo hace envuelta en comillas, tenemos que quitarlas
    private String extractStringValue(JsonNode jsonNode, String key) {

        String ret = null;
        JsonNode val = jsonNode.findValue(key);

        if (val != null) {
            ret = val.toString().replace("\"", "");
        }

        return ret;
    }


    int _botActorId;
    User _user;
    Personality _personality;
    int _numTicks;
    String _sessionToken;

    String _targetUrl;

    static final private List<String> _NICKNAMES = Arrays.asList(
            "Alic1a",
            "TheOneSeeker",
            "golpeador",
            "Carlos Alberto",
            "GKclarice",
            "SaRiTah",
            "MigSix",
            "VD_García",
            "Stafilo",
            "D10S",
            "UkraMaster",
            "Coachella_098",
            "dkv_43",
            "SilvioFuertes",
            "LiberalMan",
            "Chouanigma",
            "BlackPipe",
            "Vicator",
            "carralero",
            "Firecrafter",
            "Maldini_87",
            "Riba",
            "pererazor6",
            "Tsubasa10",
            "florín",
            "franciskañer",
            "MyVelvetSack",
            "VanGoghito",
            "Minuto93",
            "Cleren8tor",
            "sum234",
            "Armadillo",
            "EstalacShoot",
            "Pierre10",
            "unahbombah",
            "ASIAnK05",
            "GoyGoy",
            "Xanastur94",
            "SerrA_LoveR",
            "pitu",
            "CaliFaith",
            "Jirou_Nakata",
            "BigBenBrad",
            "terioulous_gr",
            "Alucard6",
            "clavoporclavo",
            "separ54",
            "srt_68",
            "Peri_SE_Ray",
            "Ix_La",
            "genarogijón",
            "DarkRester",
            "EpicSalvador",
            "777CaitSith",
            "Milan_4ever",
            "senpai_Nemo",
            "AbdulMaulavi",
            "MiguelSFW",
            "Tat&Veb",
            "mireia_valera",
            "Hun_Maker",
            "Swan_seaWIN",
            "bichuito",
            "shurmanKOH",
            "SummerDevil",
            "Trashmaker",
            "1_Morituri_1",
            "HienaSup",
            "69_AriA_69",
            "Gildrean",
            "SirKuwait",
            "Sessuria",
            "NexusUnlimited",
            "ZeeZee",
            "BlackAndBlank",
            "Oshkendor",
            "Techmole",
            "MrGalleta",
            "Limit_Breaker",
            "Shallow_leaf",
            "StouterCoach",
            "NppaGuip",
            "Xenofly",
            "Rivery",
            "MeineKaiseR",
            "Crak_O_karC",
            "Jigeki",
            "Ellatrixy",
            "cocofua",
            "MXM",
            "Sekepo85",
            "DaimonJack",
            "DanteNostromo",
            "NegaDOSX",
            "SnuggleBehemot",
            "Magnum",
            "Stylus-zen",
            "seb_lover",
            "atleti_fan",
            "CavalleroSebolla"
    );
}