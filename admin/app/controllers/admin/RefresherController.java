package controllers.admin;

import model.GlobalDate;
import model.opta.OptaXmlUtils;
import play.Logger;
import play.libs.F;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Result;
import utils.StringUtils;

import java.util.Date;

public class RefresherController extends Controller {

    public static Result index() {
        return ok(views.html.refresher.render());
    }

    public static Result inProgress() {
        return ok(String.valueOf(_inProgress));
    }

    public static Result importFromLast() {

        if (_inProgress)
            return ok("Already refreshing");

        _inProgress = true;

        long last_date = OptaXmlUtils.getLastDate().getTime();

        while (last_date >= 0) {
            last_date = downloadAndImportXML(last_date);
        }

        _inProgress = false;

        return ok("Finished importing");
    }

    public static Result lastDate() {
        return ok(String.valueOf(OptaXmlUtils.getLastDate().getTime()));    // Returns date in millis
    }

    private static long downloadAndImportXML(long last_timestamp) {

        Logger.info("Importing xml date: {}, in miliseconds {}", GlobalDate.formatDate(new Date(last_timestamp)), last_timestamp);

        F.Promise<WS.Response> responsePromise = WS.url("http://dailysoccer.herokuapp.com/return_xml/" + last_timestamp).get();
        WS.Response response = responsePromise.get(100000);

        long ret = -1L;

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
                    ret = createdAt.getTime();
                }
            }
        }
        else {
            Logger.error("Response not OK: " + response.getStatus());
        }

        return ret;
    }


    public static boolean _inProgress = false;
}
