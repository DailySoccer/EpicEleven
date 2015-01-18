package actors;

import akka.actor.UntypedActor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import model.*;
import play.Logger;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import utils.ObjectIdMapper;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BotActor extends UntypedActor {

    @Override public void postRestart(Throwable reason) throws Exception {
        Logger.debug("BotActor postRestart, reason:", reason);
        super.postRestart(reason);

        // Hemos muerto por algun motivo, retickeamos
        getSelf().tell("OnTick", getSelf());
    }

    @Override
    public void onReceive(Object msg) {
        switch ((String)msg) {

            case "Tick":
                onTick();
                getContext().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), getSelf(),
                                                               "Tick", getContext().dispatcher(), null);
                break;

            // En el caso del SimulatorTick no tenemos que reeschedulear el mensaje porque es el Simulator el que se
            // encarga de drivearnos.
            case "SimulatorTick":
                onTick();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void onTick() {
        Logger.info("Bot Actor onTick {}", GlobalDate.getCurrentDateString());

        // Vemos si tenemos nuestro usuario, si no, lo preparamos
        if (!login()) {
            signup();

            if (!login()) {
                throw new RuntimeException("WTF 5466");
            }
        }

        // Vemos los concursos activos que tenemos, escogemos el primero que este menos del X% lleno y nos metemos
        for (Contest contest : getActiveContests()) {
            if (!contest.isFull()) {
                enterContest(contest);
            }
        }
    }

    private boolean login() {
        String url = "http://localhost:9000/login";
        JsonNode jsonNode = post(url, String.format("email=%s&password=uoyeradiputs3991", getEmail()));
        return jsonNode.findPath("sessionToken") != null;
    }

    private void signup() {
        String url = "http://localhost:9000/signup";
        JsonNode jsonNode = post(url, String.format("firstName=Bototron&lastName=%s&nickName=%s&email=%s&password=uoyeradiputs3991",
                                                    getLastName(), getNickName(), getEmail()));

        Logger.info("Signup returned: {}", jsonNode.toString());
    }

    private void enterContest(Contest contest) {
        String url = String.format("http://localhost:9000/get_public_contest/%s", contest.contestId);
        JsonNode jsonNode = get(url);
        List<TemplateSoccerPlayer> soccerPlayers = fromJSON(jsonNode.findValue("soccer_players").toString(),
                                                            new TypeReference<List<TemplateSoccerPlayer>>() {});

        List<TemplateMatchEvent> matchEvents = fromJSON(jsonNode.findValue("match_events").toString(),
                                                        new TypeReference<List<TemplateMatchEvent>>() {});

        List<TemplateSoccerTeam> soccerTeams = fromJSON(jsonNode.findValue("soccer_teams").toString(),
                                                        new TypeReference<List<TemplateSoccerTeam>>() {});

        Collections.sort(soccerPlayers, new Comparator<TemplateSoccerPlayer>() {
            @Override
            public int compare(TemplateSoccerPlayer o1, TemplateSoccerPlayer o2) {
                return o1.fantasyPoints - o2.fantasyPoints;
            }
        });

        Collection<TemplateSoccerPlayer> goalkeepers = Collections2.filter(soccerPlayers, new Predicate<TemplateSoccerPlayer>() {
            @Override
            public boolean apply(@Nullable TemplateSoccerPlayer templateSoccerPlayer) {
                return (templateSoccerPlayer != null && templateSoccerPlayer.fieldPos == FieldPos.GOALKEEPER);
            }
        });

        for (TemplateSoccerPlayer sp : goalkeepers) {
            Logger.info("{} {} {}", sp.name, sp.salary, sp.fantasyPoints);
        }
    }

    private List<Contest> getActiveContests() {
        String url = "http://localhost:9000/get_active_contests";
        JsonNode jsonNode = get(url);
        return fromJSON(jsonNode.findValue("contests").toString(), new TypeReference<List<Contest>>() {});
    }

    private JsonNode post(String url, String params) {
        WSRequestHolder requestHolder = WS.url(url);

        F.Promise<WSResponse> response = requestHolder.setContentType("application/x-www-form-urlencoded").post(params);

        F.Promise<JsonNode> jsonPromise = response.map(
                new F.Function<WSResponse, JsonNode>() {
                    public JsonNode apply(WSResponse response) {
                        return response.asJson();
                    }
                }
        );
        return jsonPromise.get(10000, TimeUnit.MILLISECONDS);
    }

    private JsonNode get(String url) {
        WSRequestHolder requestHolder = WS.url(url);

        F.Promise<WSResponse> response = requestHolder.get();

        F.Promise<JsonNode> jsonPromise = response.map(
                new F.Function<WSResponse, JsonNode>() {
                    public JsonNode apply(WSResponse response) {
                        return response.asJson();
                    }
                }
        );
        return jsonPromise.get(10000, TimeUnit.MILLISECONDS);
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

    private String getLastName() {
        return "v1";
    }

    private String getNickName() {
        return "TODO";
    }

    private String getEmail() {
        return "bototron0001@test.com";
    }
}
