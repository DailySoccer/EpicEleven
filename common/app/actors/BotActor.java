package actors;

import akka.actor.UntypedActor;
import com.fasterxml.jackson.databind.JsonNode;
import model.Contest;
import model.GlobalDate;
import play.Logger;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import scala.concurrent.duration.Duration;

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

        // Vemos los concursos activos que tenemos

        // Escogemos el primero que este menos del 90% lleno y nos metemos
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
        return jsonPromise.get(1000, TimeUnit.MILLISECONDS);
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
