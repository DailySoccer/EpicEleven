package actors;

import model.GlobalDate;
import model.opta.OptaXmlUtils;
import play.Logger;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import utils.StringUtils;

import java.util.Date;
import java.util.concurrent.TimeoutException;

public class RefresherActor extends TickableActor {

    @Override public void onReceive(Object msg) {

        switch ((String)msg) {
            default:
                super.onReceive(msg);
                break;
        }
    }

    @Override protected void onTick() {

        // Cual es el ultimo documento que tenemos?
        long last_date = OptaXmlUtils.getLastDate().getTime();

        // Pedimos a produccion descargar el que va justo despues
        boolean isLastDoc = downloadAndImportXML(last_date);

        // Nos devuelve true si produccion dice que ya no hay ninguno despues. Quitamos el modo inmediato entonces.
        // Cuando todavia quedan mas docs por descargar, pedimos que nos tickeen cuanto antes.
        setImmediateTicking(!isLastDoc);
    }

    private static boolean downloadAndImportXML(long last_timestamp) {

        Logger.info("Importing xml date: {}, in miliseconds {}", GlobalDate.formatDate(new Date(last_timestamp)), last_timestamp);

        boolean isLastDoc = false;
        WSResponse response = null;

        try {
            F.Promise<WSResponse> responsePromise = WS.url("http://dailysoccer.herokuapp.com/return_xml/" + last_timestamp).get();
            response = responsePromise.get(5000);
        }
        catch (Exception exc) {
            Logger.warn("RefresherActor: El servidor no respondio en el tiempo prefijado");
        }

        if (response != null) {
            if (response.getStatus() == 200) {
                String bodyText = response.getBody();

                if (!bodyText.equals("NULL")) {
                    String headers = response.getHeader("headers");
                    String feedType = response.getHeader("feed-type");
                    String gameId = response.getHeader("game-id");
                    String competitionId = response.getHeader("competition-id");
                    String seasonId = response.getHeader("season-id");
                    Date createdAt = GlobalDate.parseDate(response.getHeader("created-at"), null);
                    Date lastUpdated = GlobalDate.parseDate(response.getHeader("last-updated"), null);
                    String name = response.getHeader("name");

                    if (createdAt.after(new Date(last_timestamp))) {
                        Logger.info("About to insert {}, size {}", name, StringUtils.humanReadableByteCount(bodyText.length(), false));

                        OptaXmlUtils.insertXml(bodyText, headers, createdAt, name, feedType, gameId, competitionId, seasonId, lastUpdated);
                    }
                }
                else {
                    isLastDoc = true;
                }
            }
            else {
                Logger.error("WTF 1015 RefresherActor el servidor respondio con status incorrecto {}", response.getStatus());
            }
        }

        return isLastDoc;
    }
}
