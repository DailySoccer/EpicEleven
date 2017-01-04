package controllers;

import akka.actor.ActorRef;
import akka.actor.Props;
import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CacheManager {
    private final static String CACHE_DISPATCHER = "cache-dispatcher";
    private final static int CHECK_ACTIVE_TEMPLATE_CONTESTS = 15 * 60;      // 15 minutos

    private CacheManager() {}

    static ActorRef getActiveTemplateContests() {
        if (!_actors.containsKey("ActiveTemplateContests")) {
            ActorRef actor = Akka.system().actorOf(Props.create(CacheActor.class).withDispatcher(CACHE_DISPATCHER), "activetemplatecontests");
            _actors.put("ActiveTemplateContests", actor);

            Akka.system().scheduler().schedule(
                    Duration.create(CHECK_ACTIVE_TEMPLATE_CONTESTS, TimeUnit.SECONDS),     //Initial delay
                    Duration.create(CHECK_ACTIVE_TEMPLATE_CONTESTS, TimeUnit.SECONDS),     //Frequency
                    actor,
                    "checkActiveTemplateContests",
                    Akka.system().dispatcher(),
                    null
            );
        }
        return _actors.get("ActiveTemplateContests");
    }

    private static ConcurrentHashMap<String, ActorRef> _actors = new ConcurrentHashMap<>();
}
