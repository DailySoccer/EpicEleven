package actors;

import model.*;
import model.opta.OptaCompetition;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.jongo.Find;
import play.Logger;
import utils.ListUtils;
import utils.MoneyUtils;

import java.util.*;

public class ContestsActor extends TickableActor {

    @Override public void onReceive(Object msg) {

        switch ((String)msg) {
            case "StartCreatingTemplateContests":
                Logger.debug("CreatingTemplateContests ON");
                _creatingTemplateContestsEnabled = true;
                break;

            case "StopCreatingTemplateContests":
                Logger.debug("CreatingTemplateContests OFF");
                _creatingTemplateContestsEnabled = false;
                break;

            case "GetCreatingTemplateContestsState":
                sender().tell(_creatingTemplateContestsEnabled, self());
                break;

            default:
                super.onReceive(msg);
                break;
        }
    }

    @Override protected void onTick() {

        // El TemplateContest instanciara sus Contests y MatchEvents asociados
        TemplateContest.findAllByActivationAt(GlobalDate.getCurrentDate()).forEach(TemplateContest::instantiate);

        Contest.findAllHistoryNotClosed().forEach(Contest::closeContest);
    }

    @Override protected void onSimulatorTick() {
        if (_creatingTemplateContestsEnabled && verifyPeriod("creatingTemplateContest", new Period().plusHours(12))) {
            creatingTemplateContests();
        }

        onTick();
    }

    private void creatingTemplateContests() {
        OptaCompetition.findAllActive().forEach(competition ->
                createTemplateContests(competition.competitionId)
        );
    }

    private void createTemplateContests(String competitionId) {
        Find findedMatchEvents = _lastMatchEventByCompetition.containsKey(competitionId)
                ? Model.templateMatchEvents().find("{_id: {$gt: #}, optaCompetitionId: #, startDate: {$gt: #}}", _lastMatchEventByCompetition.get(competitionId), competitionId, GlobalDate.getCurrentDate())
                : Model.templateMatchEvents().find("{optaCompetitionId: #, startDate: {$gt: #}}", competitionId, GlobalDate.getCurrentDate());

        List<TemplateMatchEvent> newMatchEvents = ListUtils.asList(findedMatchEvents.sort("{startDate: 1}").as(TemplateMatchEvent.class));
        if (!newMatchEvents.isEmpty()) {
            // Si los nuevos partidos comienzan antes de 7 días...
            //  No queremos generar los templates con mucha antelación para que tengan la información de "startDate" correcta (actualizada por Opta)
            DateTime currentTime = new DateTime(GlobalDate.getCurrentDate());
            if (currentTime.plusDays(7).isAfter(new DateTime(newMatchEvents.get(0).startDate))) {
                List<TemplateMatchEvent> nextMatchEvents = getNextMatches(newMatchEvents);
                if (!nextMatchEvents.isEmpty()) {
                    createMock(nextMatchEvents);

                    // Registramos el último partido con el que construimos el template
                    _lastMatchEventByCompetition.put(competitionId, getLastId(nextMatchEvents));
                }
            }
        }
    }

    private List<TemplateMatchEvent> getNextMatches(List<TemplateMatchEvent> matchEvents) {
        List<TemplateMatchEvent> nextMatches = new ArrayList<>();

        Set<String> teams = new HashSet<>();

        DateTime lastMatchDate = null;
        for (TemplateMatchEvent matchEvent : matchEvents) {
            DateTime matchDate = new DateTime(matchEvent.startDate);
            if (lastMatchDate == null) {
                lastMatchDate = matchDate;
            }

            // Es un partido de una misma jornada, si...

            // ... el intervalo de tiempo entre partidos es menor a 2 días
            if (lastMatchDate.plusDays(2).isBefore(matchDate)) {
                break;
            }

            // ... el equipo NO ha participado ya en la jornada
            if (teams.contains(matchEvent.optaTeamAId) || teams.contains(matchEvent.optaTeamBId)) {
                break;
            }

            nextMatches.add(matchEvent);

            if (lastMatchDate.isBefore(matchDate)) {
                lastMatchDate = matchDate;
            }
            teams.add(matchEvent.optaTeamAId);
            teams.add(matchEvent.optaTeamBId);
        }

        return nextMatches;
    }

    private void createMock(List<TemplateMatchEvent> templateMatchEvents) {
        createMock(templateMatchEvents, MoneyUtils.zero, 20, PrizeType.FREE, SalaryCap.STANDARD);
        createMock(templateMatchEvents, MoneyUtils.zero, 25, PrizeType.FREE, SalaryCap.DIFFICULT);

        for (int i = 1; i<=6; i++) {
            Money money = MoneyUtils.of(i);

            switch (i) {
                case 1:
                    createMock(templateMatchEvents, money, 2, PrizeType.WINNER_TAKES_ALL, SalaryCap.EASY);
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, SalaryCap.DIFFICULT);
                    break;
                case 2:
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, SalaryCap.DIFFICULT);
                    break;
                case 3:
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_THIRD_GET_PRIZES, SalaryCap.STANDARD);
                    createMock(templateMatchEvents, money, 10, PrizeType.FIFTY_FIFTY, SalaryCap.EASY);
                    break;
                case 4:
                    createMock(templateMatchEvents, money, 10, PrizeType.FIFTY_FIFTY, SalaryCap.DIFFICULT);
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, SalaryCap.STANDARD);
                    break;
                case 5:
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, SalaryCap.EASY);
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, SalaryCap.DIFFICULT);
                    break;
                case 6:
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, SalaryCap.STANDARD);
                    break;
            }
        }
    }

    private void createMock(List<TemplateMatchEvent> templateMatchEvents, Money entryFee, int maxEntries, PrizeType prizeType, SalaryCap salaryCap) {
        if (templateMatchEvents.size() == 0) {
            Logger.error("create: templateMatchEvents is empty");
            return;
        }

        Date startDate = getStartDate(templateMatchEvents);

        TemplateContest templateContest = new TemplateContest();

        _templateCount = (_templateCount + 1) % _contestNameSuffixes.length;

        templateContest.name = "%StartDate - " + _contestNameSuffixes[_templateCount] + " " + TemplateContest.FILL_WITH_MOCK_USERS;
        templateContest.minInstances = 1;
        templateContest.maxEntries = maxEntries;
        templateContest.prizeType = prizeType;
        templateContest.entryFee = entryFee;
        templateContest.salaryCap = salaryCap.money;
        templateContest.startDate = startDate;
        templateContest.templateMatchEventIds = new ArrayList<>();

        // Se activará 2 dias antes a la fecha del partido
        templateContest.activationAt = new DateTime(startDate).minusDays(5).toDate();

        templateContest.createdAt = GlobalDate.getCurrentDate();

        for (TemplateMatchEvent match: templateMatchEvents) {
            templateContest.optaCompetitionId = match.optaCompetitionId;
            templateContest.templateMatchEventIds.add(match.templateMatchEventId);
        }

        Logger.info("Generate: Template Contest: {}: {}", GlobalDate.formatDate(startDate), templateContest.templateMatchEventIds);

        Model.templateContests().insert(templateContest);
    }

    private ObjectId getLastId(List<TemplateMatchEvent> templateMatchEvents) {
        ObjectId lastId = null;
        for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
            if (lastId == null || (templateMatchEvent.templateMatchEventId.compareTo(lastId) > 0)) {
                lastId = templateMatchEvent.templateMatchEventId;
            }
        }
        return lastId;
    }

    private Date getStartDate(List<TemplateMatchEvent> templateMatchEvents) {
        Date startDate = null;
        for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
            if (startDate == null || startDate.after(templateMatchEvent.startDate)) {
                startDate = templateMatchEvent.startDate;
            }
        }
        return startDate;
    }

    private boolean verifyPeriod(String timerKey, Period period) {
        DateTime currentTime = new DateTime(GlobalDate.getCurrentDate());
        if (!_lastDate.containsKey(timerKey) || _lastDate.get(timerKey).plus(period).isBefore(currentTime)) {
            _lastDate.put(timerKey, currentTime);
            return true;
        }
        return false;
    }

    private Map<String, DateTime> _lastDate = new HashMap<>();
    private boolean _creatingTemplateContestsEnabled = false;
    private Map<String, ObjectId> _lastMatchEventByCompetition = new HashMap<>();
    private int _templateCount = 0;
    private final String[] _contestNameSuffixes = {"1", "a", "b", "a", "2", "n", "asfex", "dfggh", "piu", "lorem", "7", "8", "9"};

}
