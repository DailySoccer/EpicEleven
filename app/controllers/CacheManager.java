package controllers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.collect.ImmutableMap;
import model.TemplateContest;
import model.TemplateMatchEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CacheManager {
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
    private final static int CACHE_SOCCERPLAYER_BY_COMPETITION = 15 * 60;   // 15 minutos
    private final static int CACHE_TEMPLATESOCCERPLAYERS = 8 * 60 * 60;     // 8 Horas
    private final static int CACHE_TEMPLATESOCCERPLAYER = 8 * 60 * 60;          // 8 Hora
    private final static int CACHE_TEMPLATESOCCERTEAMS = 24 * 60 * 60;

    private CacheManager() {}

    static Cache<String, Map<String, Object>> activeTemplateContests() {
        if (_activeTemplateContests == null) {
            _activeTemplateContests = Caffeine.newBuilder()
                    .maximumSize(1)
                    .expireAfterWrite(CACHE_ACTIVE_TEMPLATE_CONTESTS, TimeUnit.SECONDS)
                    .recordStats()
                    .build();
        }
        return _activeTemplateContests;
    }

    static Cache<String, Long> count() {
        if (_count == null) {
            _count = Caffeine.newBuilder()
                    .maximumSize(1)
                    .expireAfterWrite(CACHE_COUNT_ACTIVE_TEMPLATE_CONTESTS, TimeUnit.SECONDS)
                    .recordStats()
                    .build();
        }
        return _count;
    }

    private static Map<String, Object> findActiveTemplateContests() {
        List<TemplateContest> templateContests = TemplateContest.findAllActiveOrLive();
        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.gatherFromTemplateContests(templateContests);
        return ImmutableMap.of(
                "template_contests", templateContests,
                "match_events", matchEvents
        );
    }

    static String stats() {
        StringBuffer result = new StringBuffer();

        result.append( stats ("ActiveTemplateContests", activeTemplateContests().stats()) );
        result.append( stats ("Count", count().stats()) );

        return result.toString();
    }

    static private String stats(String id, CacheStats stats) {
        StringBuffer result = new StringBuffer();

        result.append("** " + id);
        result.append("\n");
        result.append(stats.toString());
        result.append("\n");
        result.append("  hitRate: ");
        result.append(stats.hitRate());
        result.append("  missRate: ");
        result.append(stats.missRate());
        result.append("\n");

        return result.toString();
    }

    static Cache<String, Map<String, Object>> _activeTemplateContests;
    static Cache<String, Long> _count;
}
