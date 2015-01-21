package actors;

import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import akka.japi.Procedure;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import model.*;
import org.bson.types.ObjectId;
import play.Logger;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import utils.ListUtils;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

//       Como ejecutar los bots en produccion?
//       Pensar en el problema de que siempre se metan los mismos (de que aparezcan constantemente)
//       http://en.wikipedia.org/wiki/Knapsack_problem
//
public class BotActor extends UntypedActor {

    public BotActor(int botActorId) {
        _botActorId = botActorId;
    }

    private String getUrl(String suffix) {
        return String.format("http://localhost:9000/%s", suffix);
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
        return _NICKNAMES[_botActorId];
    }

    private String getEmail() {
        return String.format("bototron%04d@test.com", _botActorId);
    }


    @Override public void preStart() throws Exception {
        super.preStart();

        // Lanzando en preStart una excepcion nuestro supervisor no intentara volvernos a encender
        if (_botActorId >= _NICKNAMES.length) {
            throw new RuntimeException(String.format("WTF 2967 %s no puede comenzar", getFullName()));
        }
    }

    @Override
    public void onReceive(Object msg) {
        switch ((String)msg) {

            case "Tick":
                try {
                    if (_user == null) {
                        tryLogin();
                    } else {
                        getContext().become(_enterContestBerserker, false);
                    }
                }
                catch (TimeoutException exc) {
                    Logger.info("{} Timeout 1026, probablemente el servidor esta saturado...", getFullName());
                }
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

            if (!login()) {
                throw new RuntimeException(String.format("WTF 5466 %s no pudo hacer signup + login", getFullName()));
            }
        }

        // Vamos a asegurnos de que el profile del bot tiene los datos como queremos
        changeUserProfile();
    }

    Procedure<Object> _enterContestBerserker = new Procedure<Object>() {
        @Override
        public void apply(Object msg) {
            switch ((String)msg) {
                case "Tick":
                    try {
                        // Vemos los concursos activos en los que no estamos ya metidos, escogemos el primero que este menos del X% lleno y nos metemos
                        for (Contest contest : filterContestByNotEntered(getActiveContests(), null)) {
                            if (!contest.isFull()) {
                                enterContest(contest);
                            }
                        }
                    }
                    catch (TimeoutException exc) {
                       Logger.info("{} Timeout 1027, probablemente el servidor esta saturado...", getFullName());
                    }
                    break;

                default:
                    unhandled(msg);
                    break;
            }
        }
    };


    Procedure<Object> _enterContestWithRetries = new Procedure<Object>() {
        @Override
        public void apply(Object msg) {
            switch ((String)msg) {
                /*
                case "Tick":
                    try {
                        // Hacemos caso a los retries y evitamos los USER_ALREADY_INCLUDED

                        List<Contest> activeContests = getActiveContests();
                        for (Contest contest : filterContestByNotEntered(activeContests)) {
                            if (!contest.isFull()) {
                                enterContest(contest);
                            }
                        }
                    }
                    catch (TimeoutException exc) {
                        Logger.info("{} Timeout 1028, probablemente el servidor esta saturado...", getFullName());
                    }
                    reescheduleTick();
                    break;
                    */
                default:
                    unhandled(msg);
                    break;
            }
        }
    };


    Procedure<Object> _production = new Procedure<Object>() {
        @Override
        public void apply(Object msg) {
            switch ((String)msg) {
                case "Tick":
                    try {
                        List<Contest> activeContests = getActiveContests();
                        List<Contest> entered = new ArrayList<>();
                        List<Contest> notEntered = filterContestByNotEntered(activeContests, entered);

                        // En cada tick el bot solo entrara en 1 concurso
                        for (Contest contest : notEntered) {
                            if (shouldEnter(contest)) {
                                enterContest(contest);
                                break;
                            }
                        }

                        // Nos salimos de concursos donde ha entrado ya mas gente
                        for (Contest contest : entered) {
                            if (shouldLeave(contest)) {
                                //leaveContest(contest);
                            }
                        }
                    }
                    catch (TimeoutException exc) {
                        Logger.info("{} Timeout 1028, probablemente el servidor esta saturado...", getFullName());
                    }
                    break;

                default:
                    unhandled(msg);
                    break;
            }
        }
    };

    private boolean shouldLeave(Contest contest) throws TimeoutException {
        boolean bRet = false;
        int excessBots = contest.getNumEntries() - (contest.maxEntries - getGoalFreeSlots(contest));

        if (excessBots > 0) {
            // De todos los bots que hay, yo que prioridad tengo, en que posicion estoy?
            List<String> botsInContest = getBotsNicknamesInContest(contest);

            if (botsInContest.indexOf(getNickName()) < excessBots) {
                bRet = true;
            }
        }

        return bRet;
    }

    private List<String> getBotsNicknamesInContest(Contest contest) throws TimeoutException {
        List<String> ret = new ArrayList<>();

        // Hacemos un request al server para obtener los nicknames de los participantes
        List<UserInfo> usersInfo = getUsersInfoInContest(contest);

        for (ContestEntry contestEntry : contest.contestEntries) {
            UserInfo userInfo = findUserInfoInContestEntries(contestEntry, usersInfo);

            int index = _NICKNAMES.indexOf(userInfo.nickName);
            if (index != -1) {
                ret.add(_NICKNAMES.get(index));
            }
        }

        // La devolvemos siempre ordenada segun aparecen en nuestra lista predefinida
        Collections.sort(ret, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return _NICKNAMES.indexOf(o2) - _NICKNAMES.indexOf(o1);
            }
        });

        return ret;
    }

    private UserInfo findUserInfoInContestEntries(ContestEntry contestEntry, List<UserInfo> usersInfo) {
        UserInfo ret = null;
        for (UserInfo userInfo : usersInfo) {
            if (userInfo.userId == contestEntry.userId) {
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
        // Para head vs head, no nos metemos nunca
        int goalFreeSlots = 2;

        // Queremos que haya una pequeña variacion en el numero de slots libres, pero tambien queremos que no haya
        // oscilaciones, es decir, que para el mismo concurso todos los bots esten de acuerdo en cuantos slots libres
        // hay que dejar
        Random localRandom = new Random(contest.contestId.hashCode());

        if (contest.maxEntries > 2) {
            if (contest.maxEntries <= 5) {
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
        }

        return goalFreeSlots;
    }

    private boolean login() throws TimeoutException {
        _user = null;

        JsonNode jsonNode = post(getUrl("login"), String.format("email=%s&password=uoyeradiputs3991", getEmail()));

        if (jsonNode.findValue("sessionToken") != null) {
            jsonNode = get(getUrl("get_user_profile"));
            _user = fromJSON(jsonNode.toString(), new TypeReference<User>() {});
        }

        return _user != null;
    }


    private void signup() throws TimeoutException {
        JsonNode jsonNode = post(getUrl("signup"), String.format("firstName=%s&lastName=%s&nickName=%s&email=%s&password=uoyeradiputs3991",
                                                                 getFirstName(), getLastName(), getNickName(), getEmail()));

        if (jsonNode == null) {
            Logger.error("{} signup returned empty", getFullName());
        }
    }


    private void changeUserProfile() throws TimeoutException {
        JsonNode jsonNode = post(getUrl("change_user_profile"), String.format("firstName=%s&lastName=%s&nickName=%s&email=%s",
                                                                 getFirstName(), getLastName(), getNickName(), getEmail()));

        if (jsonNode == null) {
            Logger.error("{} changeUserProfile returned empty", getFullName());
        }
    }


    private List<Contest> getActiveContests() throws TimeoutException {

        List<Contest> ret = null;
        JsonNode jsonNode = get(getUrl("get_active_contests")).findValue("contests");

        if (jsonNode != null) {
            ret = fromJSON(jsonNode.toString(), new TypeReference<List<Contest>>() { });
        }
        else {
            Logger.error("{} getActiveContests returned empty", getFullName());
        }

        return ret == null? new ArrayList<Contest>() : ret;
    }

    private void enterContest(Contest contest) throws TimeoutException {
        JsonNode jsonNode = get(getUrl(String.format("get_public_contest/%s", contest.contestId))).findValue("soccer_players");

        if (jsonNode != null) {
            List<TemplateSoccerPlayer> soccerPlayers = fromJSON(jsonNode.toString(), new TypeReference<List<TemplateSoccerPlayer>>() { });
            List<TemplateSoccerPlayer> lineup = generateLineup(soccerPlayers, contest.salaryCap);

            addContestEntry(lineup, contest.contestId);
        }
        else {
            Logger.error("{} enterContest returned empty", getFullName());
        }
    }

    private void addContestEntry(List<TemplateSoccerPlayer> lineup, ObjectId contestId) throws TimeoutException {
        String idList = new ObjectMapper().valueToTree(ListUtils.stringListFromObjectIdList(ListUtils.convertToIdList(lineup))).toString();
        JsonNode jsonNode = post(getUrl(String.format("add_contest_entry")),
                                 String.format("contestId=%s&soccerTeam=%s", contestId.toString(), idList));

        if (jsonNode == null) {
            Logger.error("{} addContestEntry returned empty", getFullName());
        }
    }

    private List<UserInfo> getUsersInfoInContest(Contest contest) throws TimeoutException {
        List<UserInfo> ret;
        JsonNode jsonNode = get(getUrl(String.format("get_contest_info/%s", contest.contestId))).findValue("users_info");

        if (jsonNode != null) {
            ret = fromJSON(jsonNode. toString(), new TypeReference<List<UserInfo>>() { });
        }
        else {
            Logger.error("{} enterContest returned empty", getFullName());
            ret = new ArrayList<>();
        }

        return ret;
    }

    private List<TemplateSoccerPlayer> generateLineup(List<TemplateSoccerPlayer> soccerPlayers, int salaryCap) {
        List<TemplateSoccerPlayer> lineup = new ArrayList<>();

        sortByFantasyPoints(soccerPlayers);

        List<TemplateSoccerPlayer> forwards = filterByPosition(soccerPlayers, FieldPos.FORWARD);
        List<TemplateSoccerPlayer> goalkeepers = filterByPosition(soccerPlayers, FieldPos.GOALKEEPER);
        List<TemplateSoccerPlayer> middles = filterByPosition(soccerPlayers, FieldPos.MIDDLE);
        List<TemplateSoccerPlayer> defenses = filterByPosition(soccerPlayers, FieldPos.DEFENSE);

        // Dos delanteros entre los 8 mejores
        for (int c = 0; c < 2; ++c) {
            int next = _rand.nextInt(Math.min(8, forwards.size()));
            lineup.add(forwards.get(next));
        }

        // Un portero de la mitad para abajo
        lineup.add(goalkeepers.get(_rand.nextInt(goalkeepers.size() / 2) + (goalkeepers.size() / 2)));

        // Medios y defensas repartidos por igual, buscamos varias veces partiendo desde la media y aumentado de 100 en
        // 100 por debajo
        int averageRemainingSalary = (salaryCap - calcSalaryForLineup(lineup)) / 8;
        int diff = -1;

        for (int tryCounter = 0; tryCounter < 10; ++tryCounter) {
            List<TemplateSoccerPlayer> tempLineup = new ArrayList<>(lineup);

            int maxSal = averageRemainingSalary + 1000;
            int minSal = averageRemainingSalary - ((tryCounter+1)*100);
            List<TemplateSoccerPlayer> middlesBySalary = filterBySalary(middles, minSal, maxSal);
            List<TemplateSoccerPlayer> defensesBySalary = filterBySalary(defenses, minSal, maxSal);

            if (middlesBySalary.size() < 4 || defensesBySalary.size() < 4) {
                continue;
            }

            for (int c = 0; c < 4; ++c) {
                int next = _rand.nextInt(Math.min(8, middlesBySalary.size()));
                tempLineup.add(middlesBySalary.remove(next));
                next = _rand.nextInt(Math.min(8, defensesBySalary.size()));
                tempLineup.add(defensesBySalary.remove(next));
            }

            diff = salaryCap - calcSalaryForLineup(tempLineup);

            if (tempLineup.size() == 11 && diff >= 0) {
                lineup = tempLineup;
                break;
            }
        }

        return lineup;
    }

    private int calcSalaryForLineup(List<TemplateSoccerPlayer> sps) {
        int ret = 0;
        for (TemplateSoccerPlayer sp : sps) {
         ret += sp.salary;
        }
        return ret;
    }

    private List<TemplateSoccerPlayer> filterByPosition(List<TemplateSoccerPlayer> sps, final FieldPos fp) {
        return ListUtils.asList(Collections2.filter(sps, new Predicate<TemplateSoccerPlayer>() {
            @Override
            public boolean apply(@Nullable TemplateSoccerPlayer templateSoccerPlayer) {
            return (templateSoccerPlayer != null && templateSoccerPlayer.fieldPos == fp);
            }
        }));
    }

    private List<TemplateSoccerPlayer> filterBySalary(List<TemplateSoccerPlayer> sps, final int salMin, final int salMax) {
        return ListUtils.asList(Collections2.filter(sps, new Predicate<TemplateSoccerPlayer>() {
            @Override
            public boolean apply(@Nullable TemplateSoccerPlayer templateSoccerPlayer) {
            return (templateSoccerPlayer != null && templateSoccerPlayer.salary >= salMin && templateSoccerPlayer.salary <= salMax);
            }
        }));
    }

    private void sortByFantasyPoints(List<TemplateSoccerPlayer> sps) {
        Collections.sort(sps, new Comparator<TemplateSoccerPlayer>() {
            @Override
            public int compare(TemplateSoccerPlayer o1, TemplateSoccerPlayer o2) {
            return o1.fantasyPoints - o2.fantasyPoints;
            }
        });
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

    private JsonNode post(String url, String params) throws TimeoutException {
        WSRequestHolder requestHolder = WS.url(url);

        F.Promise<WSResponse> response = requestHolder.setContentType("application/x-www-form-urlencoded")
                                                      .setHeader("X-Session-Token", getEmail()).post(params);

        F.Promise<JsonNode> jsonPromise = response.map(
                new F.Function<WSResponse, JsonNode>() {
                    public JsonNode apply(WSResponse response) {
                        try {
                            return response.asJson();
                        }
                        catch (Exception exc) {
                            Logger.debug("WTF 1280 {} el servidor devolvio Json incorrecto: {}", getFullName(), response.getStatusText());
                            return JsonNodeFactory.instance.objectNode();
                        }
                    }
                }
        );

        return jsonPromise.get(5000, TimeUnit.MILLISECONDS);
    }

    private JsonNode get(String url) throws TimeoutException {
        WSRequestHolder requestHolder = WS.url(url);

        F.Promise<WSResponse> response = requestHolder.setHeader("X-Session-Token", getEmail()).get();

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


    int _botActorId;
    User _user;

    static Random _rand = new Random(System.currentTimeMillis());

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
