package controllers;

import akka.actor.UntypedActor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.ImmutableMap;
import model.*;
import org.bson.types.ObjectId;
import play.Logger;
import play.Play;
import play.cache.Cache;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import utils.JsonUtils;
import utils.ListUtils;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class CacheActor extends UntypedActor {

    private final static int CACHE_ACTIVE_TEMPLATE_CONTESTS = 60 * 60 * 8;      // 8 horas
    private final static int CACHE_COUNT_ACTIVE_TEMPLATE_CONTESTS = 60 * 30;    // 30 minutos
    private final static int CACHE_ACTIVE_CONTESTS = 60;
    private final static int CACHE_ACTIVE_CONTEST = 60;
    private final static int CACHE_VIEW_LIVE_CONTESTS = 60 * 30 * 1;        // 30 minutos
    private final static int CACHE_VIEW_HISTORY_CONTEST = 60 * 60 * 8;      // 8 horas
    private final static int CACHE_LIVE_MATCHEVENTS = 30;
    private final static int CACHE_LIVE_CONTESTENTRIES = 30;
    private final static int CACHE_CONTEST_INFO = 60;
    private final static int CACHE_EXISTS_LIVE = 60 * 5;         // 5 minutos

    public static class CacheMsg {
        public String msg;
        public String userId;
        public Object param;

        public CacheMsg(String m, String u, Object p) { msg = m; userId = u; param = p; }
    }


    public CacheActor() {}

    @Override public void preStart() throws Exception {
        super.preStart();
    }

    @Override
    public void onReceive(Object msg) throws Exception {

        try {
            if (msg instanceof CacheMsg) {
                onReceive((CacheMsg) msg);
            } else {
                onReceive((String) msg);
            }
        }
        catch (TimeoutException exc) {
        }
    }


    private void onReceive(CacheMsg msg) throws Exception {

        switch (msg.msg) {
            default:
                unhandled(msg);
                break;
        }
    }

    private void onReceive(String msg) throws Exception {

        switch (msg) {
            case "getActiveTemplateContests":
                sender().tell(msgGetActiveTemplateContests(), getSelf());
                break;

            case "checkActiveTemplateContests":
                msgCheckActiveTemplateContests();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private static Map<String, Object> activeTemplateContestsCache() throws Exception {
        return Cache.getOrElse("ActiveTemplateContests", new Callable<Map<String, Object>>() {
            @Override
            public Map<String, Object> call() throws Exception {
                return findActiveTemplateContests();
            }
        }, CACHE_ACTIVE_TEMPLATE_CONTESTS);
    }

    private static Map<String, Object> msgGetActiveTemplateContests() throws Exception {
        return Cache.getOrElse("ActiveTemplateContests", new Callable<Map<String, Object>>() {
            @Override
            public Map<String, Object> call() throws Exception {
                return findActiveTemplateContests();
            }
        }, CACHE_ACTIVE_TEMPLATE_CONTESTS);
    }

    private static void msgCheckActiveTemplateContests() throws Exception {
        Map<String, Object> result = activeTemplateContestsCache();

        // Necesitamos actualizar la caché?
        long countTemplateContest = TemplateContest.countAllActiveOrLive();
        if (result.containsKey("template_contests") && result.get("template_contests") instanceof ArrayList) {
            List<?> list = (List<?>) result.get("template_contests");
            if (list != null && list.size() != countTemplateContest) {

                Cache.set("ActiveTemplateContests", findActiveTemplateContests());
                Logger.debug("getActiveTemplateContests: cache INVALID");
            }
        }
    }

    private static Map<String, Object> findActiveTemplateContests() {
        List<TemplateContest> templateContests = TemplateContest.findAllActiveOrLive();
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.gatherFromTemplateContests(templateContests);
        return ImmutableMap.of(
                "template_contests", templateContests,
                "match_events", matchEvents
        );
    }
}