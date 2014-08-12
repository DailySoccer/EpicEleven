package model;

import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.*;

public class Contest implements JongoId {

    @Id
    public ObjectId contestId;
    public ObjectId templateContestId;

    public Date createdAt;
    public String name;

    public List<ContestEntry> contestEntries = new ArrayList<>();
    public int maxEntries;


    public Contest() {}

    public Contest(TemplateContest template) {
        templateContestId = template.templateContestId;
        name = template.name;
        maxEntries = template.maxEntries;
        createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() { return contestId; }

    static public Contest findOne(ObjectId contestId) {
        return Model.contests().findOne("{_id : #}", contestId).as(Contest.class);
    }

    static public Contest findOne(String contestId) {
        Contest aContest = null;
        if (ObjectId.isValid(contestId)) {
            aContest = Model.contests().findOne(new ObjectId(contestId)).as(Contest.class);
        }
        return aContest;
    }

    public static List<Contest> findAllFromUser(ObjectId userId) {
        return ListUtils.asList(Model.contests().find("{'contestEntries.userId': #}", userId).as(Contest.class));
    }

    static public List<Contest> findAllFromTemplateContest(ObjectId templateContestId) {
        return ListUtils.asList(Model.contests().find("{templateContestId: #}", templateContestId).as(Contest.class));
    }

    static public List<Contest> findAllFromTemplateContests(Iterable<TemplateContest> templateContests) {
        return ListUtils.asList(Model.findObjectIds(Model.contests(), "templateContestId", ListUtils.convertToIdList(templateContests)).as(Contest.class));
    }

    static public void updateRanking(ObjectId templateMatchEventId) {
        // Buscamos los template contests que incluyan ese partido
        List<TemplateContest> templateContests = ListUtils.asList(Model.templateContests().find("{templateMatchEventIds: {$in:[#]}}",
                templateMatchEventId).as(TemplateContest.class));

        for (TemplateContest templateContest : templateContests) {
            // Obtenemos los partidos
            List<MatchEvent> matchEvents = templateContest.getMatchEvents();

            // Actualizamos los rankings de cada contest
            List<Contest> contests = Contest.findAllFromTemplateContest(templateContest.templateContestId);
            for (Contest contest : contests) {
                contest.updateRanking(templateContest, matchEvents);
            }
        }
    }

   private void updateRanking(TemplateContest templateContest, List<MatchEvent> matchEvents) {
        // Actualizamos los fantasy points
        for (ContestEntry contestEntry : contestEntries) {
            contestEntry.fantasyPoints = contestEntry.getFantasyPointsFromMatchEvents(matchEvents);
        }
        // Los ordenamos según los fantasy points
        Collections.sort (contestEntries, new ContestEntryComparable());
        // Actualizamos sus "posiciones"
        int index = 0;
        for (ContestEntry contestEntry : contestEntries) {
            contestEntry.position = index++;
            contestEntry.prize = templateContest.getPositionPrize(contestEntry.position);
            contestEntry.updateRanking();
        }
    }

    class ContestEntryComparable implements Comparator<ContestEntry>{
        @Override
        public int compare(ContestEntry o1, ContestEntry o2) {
            return (o1.fantasyPoints>o2.fantasyPoints ? -1 : (o1.fantasyPoints==o2.fantasyPoints ? 0 : 1));
        }
    }
}
