package actors;

import akka.actor.UntypedActor;
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

    public enum Personality {
        BERSERKER,
        PRODUCTION
    }

    public BotActor(int botActorId, Personality pers) {
        _botActorId = botActorId;
        _personality = pers;
    }

    private String composeUrl(String suffix) {
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
        return _NICKNAMES.get(_botActorId);
    }

    private String getEmail() {
        return String.format("bototron%04d@test.com", _botActorId);
    }


    @Override public void preStart() throws Exception {
        super.preStart();

        // Lanzando en preStart una excepcion nuestro supervisor no intentara volvernos a encender
        if (_botActorId >= _NICKNAMES.size()) {
            throw new RuntimeException("WTF 2967 Bototron no puede comenzar por falta de _NICKNAMES");
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
                        switch (_personality) {
                            case BERSERKER:
                                onTickBerserker();
                                break;
                            default:
                                onTickProduction();
                                break;
                        }
                    }
                }
                catch (TimeoutException exc) {
                    Logger.info("{} Timeout 1026, probablemente el servidor esta saturado...", getFullName());
                }
                break;

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

    void onTickProduction() throws TimeoutException {
        List<Contest> notEntered = filterContestByNotEntered(getActiveContests(), null);

        // En cada tick el bot solo entrara en 1 concurso
        for (Contest contest : notEntered) {
            if (shouldEnter(contest)) {
                enterContest(contest);
                break;
            }
        }

        // Nos salimos de concursos donde ya hay demasiada gente (u otros bots)
        for (Contest contest : getMyActiveContests()) {
            if (shouldLeave(contest)) {
                leaveContest(contest);
            }
        }
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

        JsonNode jsonNode = post("login", String.format("email=%s&password=uoyeradiputs3991", getEmail()));

        if (jsonNode.findValue("sessionToken") != null) {
            jsonNode = get("get_user_profile");
            _user = fromJSON(jsonNode.toString(), new TypeReference<User>() {});
        }

        return _user != null;
    }


    private void signup() throws TimeoutException {
        JsonNode jsonNode = post("signup", String.format("firstName=%s&lastName=%s&nickName=%s&email=%s&password=uoyeradiputs3991",
                                                         getFirstName(), getLastName(), getNickName(), getEmail()));

        if (jsonNode == null) {
            Logger.error("{} signup returned empty", getFullName());
        }
    }


    private void changeUserProfile() throws TimeoutException {
        JsonNode jsonNode = post("change_user_profile", String.format("firstName=%s&lastName=%s&nickName=%s&email=%s",
                                                                 getFirstName(), getLastName(), getNickName(), getEmail()));

        if (jsonNode == null) {
            Logger.error("{} changeUserProfile returned empty", getFullName());
        }
    }


    private List<Contest> getActiveContests() throws TimeoutException {

        List<Contest> ret = null;
        JsonNode jsonNode = get("get_active_contests").findValue("contests");

        if (jsonNode != null) {
            ret = fromJSON(jsonNode.toString(), new TypeReference<List<Contest>>() { });
        }
        else {
            Logger.error("{} getActiveContests returned empty", getFullName());
        }

        return ret == null? new ArrayList<Contest>() : ret;
    }

    private String enterContest(Contest contest) throws TimeoutException {
        JsonNode jsonNode = get(String.format("get_public_contest/%s", contest.contestId)).findValue("soccer_players");
        String enteredContestId = null;

        if (jsonNode != null) {
            List<TemplateSoccerPlayer> soccerPlayers = fromJSON(jsonNode.toString(), new TypeReference<List<TemplateSoccerPlayer>>() { });
            List<TemplateSoccerPlayer> lineup = generateLineup(soccerPlayers, contest.salaryCap);

            enteredContestId = addContestEntry(lineup, contest.contestId);
        }
        else {
            Logger.error("{} enterContest returned empty", getFullName());
        }

        return enteredContestId;
    }

    private String addContestEntry(List<TemplateSoccerPlayer> lineup, ObjectId contestId) throws TimeoutException {

        String idList = new ObjectMapper().valueToTree(ListUtils.stringListFromObjectIdList(ListUtils.convertToIdList(lineup))).toString();
        JsonNode jsonNode = post("add_contest_entry",
                                 String.format("contestId=%s&soccerTeam=%s", contestId.toString(), idList));

        // Nosotros podemos pedir un contestId, pero el servidor puede elegir meternos en otro
        String enteredContestId = null;

        if (jsonNode != null) {
            JsonNode error = jsonNode.findValue("error");

            if (error == null) {
                enteredContestId = jsonNode.findValue("contestId").toString().replace("\"", "");
            }
            else {
                Logger.error("{} addContestEntry produjo en un error en el servidor {}", getFullName(), error.toString());
            }
        }
        else {
            Logger.error("{} addContestEntry returned empty", getFullName());
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

        // Medios y defensas repartidos por igual
        int averageRemainingSalary = (salaryCap - sumSalary(lineup)) / 8;

        List<TemplateSoccerPlayer> remainingPlayers = new ArrayList<>(middles);
        remainingPlayers.addAll(defenses);
        int averagePlayerSalary = sumSalary(remainingPlayers) / remainingPlayers.size();

        int startingSalary = Math.min(averagePlayerSalary, averageRemainingSalary);
        int diff = -1;

        for (int tryCounter = 1; tryCounter < 10; ++tryCounter) {
            List<TemplateSoccerPlayer> tempLineup = new ArrayList<>(lineup);

            int maxSal = startingSalary;
            int minSal = startingSalary - (tryCounter*startingSalary/10);
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

            diff = salaryCap - sumSalary(tempLineup);

            if (tempLineup.size() == 11 && diff >= 0) {
                lineup = tempLineup;
                break;
            }
        }

        return lineup;
    }

    private int sumSalary(List<TemplateSoccerPlayer> sps) {
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
        WSRequestHolder requestHolder = WS.url(composeUrl(url));

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
        WSRequestHolder requestHolder = WS.url(composeUrl(url));

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
    Personality _personality;

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
