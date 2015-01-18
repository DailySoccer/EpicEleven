package actors;

import akka.actor.UntypedActor;
import akka.japi.Procedure;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import utils.ListUtils;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;

//       Identificacion universal univoca
//       URL de llamada al server
//       Nums aleatorios
//       http://en.wikipedia.org/wiki/Knapsack_problem
//       Solucionar el arranque/stop bajo demanda en desarrollo y produccion
//
public class BotActor extends UntypedActor {

    public BotActor(int botActorId) {
        _botActorId = botActorId;
    }

    private String getUrl(String suffix) {
        return String.format("http://localhost:9000/%s", suffix);
    }

    private String getLastName() {
        return "v1";
    }

    private String getNickName() {
        return String.format("Bototron%04d", _botActorId);
    }

    private String getEmail() {
        return String.format("bototron%04d@test.com", _botActorId);
    }


    @Override public void preStart() throws Exception {
        super.preStart();

        // Primer tick. Nuestro bot se automantiene vivo
        getSelf().tell("Tick", getSelf());
    }

    @Override
    public void onReceive(Object msg) {
        switch ((String)msg) {

            case "Tick":
                if (_user == null) {
                    tryLogin();
                }
                else {
                    onTick();
                }
                getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), getSelf(),
                                                               "Tick", getContext().dispatcher(), null);
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void tryLogin() {
        // Vemos si tenemos nuestro usuario _user adquirido, si no, lo preparamos
        if (!login()) {
            signup();

            if (!login()) {
                throw new RuntimeException("WTF 5466 Bototron login");
            }
        }
    }

    private void onTick() {
        Logger.info("{} onTick {}", getEmail(), GlobalDate.getCurrentDateString());

        // Vemos los concursos activos en los que no estamos ya metidos, escogemos el primero que este menos del X% lleno y nos metemos
        for (Contest contest : filterContestByNotEntered(getActiveContests())) {
            if (!contest.isFull()) {
                enterContest(contest);
            }
        }
    }

    private boolean login() {
        _user = null;

        JsonNode jsonNode = post(getUrl("login"), String.format("email=%s&password=uoyeradiputs3991", getEmail()));

        if (jsonNode.findValue("sessionToken") != null) {
            jsonNode = get(getUrl("get_user_profile"));
            _user = fromJSON(jsonNode.toString(), new TypeReference<User>() {});
        }

        return _user != null;
    }

    private void signup() {
        JsonNode jsonNode = post(getUrl("signup"), String.format("firstName=Bototron&lastName=%s&nickName=%s&email=%s&password=uoyeradiputs3991",
                                                                 getLastName(), getNickName(), getEmail()));

        if (jsonNode != null) {
            Logger.debug("Bototron Signup returned: {}", jsonNode.toString());
        }
        else {
            Logger.debug("Bototron Signup error");
        }
    }

    private void enterContest(Contest contest) {
        JsonNode jsonNode = get(getUrl(String.format("get_public_contest/%s", contest.contestId)));

        List<TemplateSoccerPlayer> soccerPlayers = fromJSON(jsonNode.findValue("soccer_players").toString(),
                                                            new TypeReference<List<TemplateSoccerPlayer>>() {});

        List<TemplateSoccerPlayer> lineup = generateLineup(soccerPlayers, contest.salaryCap);
        addContestEntry(lineup, contest.contestId);
    }

    private void addContestEntry(List<TemplateSoccerPlayer> lineup, ObjectId contestId) {
        String idList = new ObjectMapper().valueToTree(ListUtils.stringListFromObjectIdList(ListUtils.convertToIdList(lineup))).toString();
        JsonNode jsonNode = post(getUrl(String.format("add_contest_entry")),
                                 String.format("contestId=%s&soccerTeam=%s", contestId.toString(), idList));

        if (jsonNode != null) {
            Logger.debug("Bototron AddContestEntry returned: {}", jsonNode.toString());
        }
        else {
            Logger.debug("Bototron AddContestEntry error");
        }
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
                Logger.error("WTF 7648 Bototron: Menos de 4");
                continue;
            }

            for (int c = 0; c < 4; ++c) {
                int next = _rand.nextInt(Math.min(8, middlesBySalary.size()));
                tempLineup.add(middlesBySalary.remove(next));
                next = _rand.nextInt(Math.min(8, defensesBySalary.size()));
                tempLineup.add(defensesBySalary.remove(next));
            }

            diff = salaryCap - calcSalaryForLineup(tempLineup);
            Logger.debug("Bototron Count {} diff {}", tempLineup.size(), diff);

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

    private List<Contest> getActiveContests() {
        String url = "http://localhost:9000/get_active_contests";

        JsonNode jsonNode = get(url).findValue("contests");

        if (jsonNode != null) {
            return fromJSON(jsonNode.toString(), new TypeReference<List<Contest>>() {
            });
        }
        else {
            return new ArrayList<Contest>();
        }
    }

    private List<Contest> filterContestByNotEntered(List<Contest> contests) {
        List<Contest> ret = new ArrayList<>();
        for (Contest contest : contests) {
            if (!contest.containsContestEntryWithUser(_user.userId)) {
                ret.add(contest);
            }
        }
        return ret;
    }

    private JsonNode post(String url, String params) {
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
                            Logger.debug("El servidor devolvio Json incorrecto: {}", response.getStatusText());
                            return JsonNodeFactory.instance.objectNode();
                        }
                    }
                }
        );

        try {
            return jsonPromise.get(1000, TimeUnit.MILLISECONDS);
        }
        catch (Exception exc) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private JsonNode get(String url) {
        WSRequestHolder requestHolder = WS.url(url);

        F.Promise<WSResponse> response = requestHolder.setHeader("X-Session-Token", getEmail()).get();

        F.Promise<JsonNode> jsonPromise = response.map(
                new F.Function<WSResponse, JsonNode>() {
                    public JsonNode apply(WSResponse response) {
                        try {
                            return response.asJson();
                        }
                        catch (Exception exc) {
                            Logger.debug("El servidor devolvio Json incorrecto: {}", response.getStatusText());
                            return JsonNodeFactory.instance.objectNode();
                        }
                    }
                }
        );

        try {
            return jsonPromise.get(1000, TimeUnit.MILLISECONDS);
        }
        catch (Exception exc) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private static <T> T fromJSON(final String json, final TypeReference<T> type) {
        T ret = null;

        try {
            // TODO: Ximo fixear numEntries / getNumEntries
            ret = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                    .readValue(json, type);
        } catch (Exception exc) {
            Logger.debug("WTF 2229", exc);
        }
        return ret;
    }

    int _botActorId;
    User _user;

    static Random _rand = new Random(System.currentTimeMillis());
}
