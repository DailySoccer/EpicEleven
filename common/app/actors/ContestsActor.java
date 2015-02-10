package actors;

import model.Contest;
import model.GlobalDate;
import model.TemplateContest;
import model.*;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.jongo.Find;
import play.Logger;
import play.Play;
import utils.ListUtils;

import java.util.*;

public class ContestsActor extends TickableActor {

    @Override public void onReceive(Object msg) {

        switch ((String)msg) {
            default:
                super.onReceive(msg);
                break;
        }
    }

    @Override protected void onTick() {

        if (Play.isDev()) {
            creatingTemplateContests();
        }

        for (TemplateContest templateContest : TemplateContest.findAllByActivationAt(GlobalDate.getCurrentDate())) {
            // El TemplateContest instanciara sus Contests y MatchEvents asociados
            templateContest.instantiate();
        }

        for (Contest contest : Contest.findAllHistoryNotClosed()) {
            contest.closeContest();
        }
    }

    private void creatingTemplateContests() {
        Find findedMatchEvents = _lastMathEventId != null
                ? Model.templateMatchEvents().find("{_id: {$gt: #}}", _lastMathEventId)
                : Model.templateMatchEvents().find();

        List<TemplateMatchEvent> newMatchEvents = ListUtils.asList(findedMatchEvents.sort("{_id: 1}").as(TemplateMatchEvent.class));

        // Registraremos los partidos de una determinada competición
        Map<String, List<TemplateMatchEvent>> matchEventsByCompetition = new HashMap<>();

        for (TemplateMatchEvent match: newMatchEvents) {
            DateTime lastMatchDateTime = null;

            List<TemplateMatchEvent> matchEventsInCompetition = new ArrayList<>();
            TemplateMatchEvent lastMatchEvent = match;

            if (matchEventsByCompetition.containsKey(match.optaCompetitionId)) {
                matchEventsInCompetition = matchEventsByCompetition.get(match.optaCompetitionId);
                lastMatchEvent = matchEventsInCompetition.get( matchEventsInCompetition.size() - 1 );
                lastMatchDateTime = new DateTime(lastMatchEvent.startDate, DateTimeZone.UTC);
            }
            else {
                lastMatchDateTime = new DateTime(match.startDate, DateTimeZone.UTC);
            }

            DateTime matchDateTime = new DateTime(match.startDate, DateTimeZone.UTC);

            // El partido es de un dia distinto?
            if (lastMatchDateTime.dayOfYear().get() != matchDateTime.dayOfYear().get()) {
                // Logger.info("{} != {}", dateTime.dayOfYear().get(), matchDateTime.dayOfYear().get());

                // El dia anterior tenia un numero suficiente de partidos? (minimo 2)
                if (matchEventsInCompetition.size() >= 2) {

                    // crear el contest
                    createMock(matchEventsInCompetition);

                    // Registramos el último partido con el que construimos un contest
                    _lastMathEventId =lastMatchEvent.templateMatchEventId;

                    // empezar a registrar los partidos del nuevo contest
                    matchEventsInCompetition.clear();
                }
            }

            matchEventsInCompetition.add(match);
            matchEventsByCompetition.put(match.optaCompetitionId, matchEventsInCompetition);
        }

        // Creamos los partidos que nos han quedado
        for (List<TemplateMatchEvent> matchEvents : matchEventsByCompetition.values()) {
            createMock(matchEvents);
            _lastMathEventId = matchEvents.get(matchEvents.size()-1).templateMatchEventId;
        }

    }

    private void createMock(List<TemplateMatchEvent> templateMatchEvents) {
        createMock(templateMatchEvents, Money.zero(CurrencyUnit.EUR), 5, PrizeType.FREE, 70000);

        /*
        createMock(templateMatchEvents, Money.zero(CurrencyUnit.EUR), 25, PrizeType.FREE, 65000);

        for (int i = 1; i<=6; i++) {
            Money money = Money.of(CurrencyUnit.EUR, i);

            switch (i) {
                case 1:
                    createMock(templateMatchEvents, money, 2, PrizeType.WINNER_TAKES_ALL, 60000);
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, 65000);
                    break;
                case 2:
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, 65000);
                    break;
                case 3:
                    createMock(templateMatchEvents, money, 5, PrizeType.TOP_THIRD_GET_PRIZES, 70000);
                    createMock(templateMatchEvents, money, 10, PrizeType.FIFTY_FIFTY, 60000);
                    break;
                case 4:
                    createMock(templateMatchEvents, money, 10, PrizeType.FIFTY_FIFTY, 65000);
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, 70000);
                    break;
                case 5:
                    createMock(templateMatchEvents, money, 10, PrizeType.TOP_3_GET_PRIZES, 60000);
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, 65000);
                    break;
                case 6:
                    createMock(templateMatchEvents, money, 25, PrizeType.WINNER_TAKES_ALL, 70000);
                    break;
            }
        }
        */
    }

    private void createMock(List<TemplateMatchEvent> templateMatchEvents, Money entryFee, int maxEntries, PrizeType prizeType, int salaryCap) {
        if (templateMatchEvents.size() == 0) {
            Logger.error("create: templateMatchEvents is empty");
            return;
        }

        Date startDate = templateMatchEvents.get(0).startDate;

        TemplateContest templateContest = new TemplateContest();

        _templateCount = (_templateCount + 1) % _contestNameSuffixes.length;

        templateContest.name = "%StartDate - " + _contestNameSuffixes[_templateCount] + " " + TemplateContest.FILL_WITH_MOCK_USERS;
        templateContest.minInstances = 1;
        templateContest.maxEntries = maxEntries;
        templateContest.prizeType = prizeType;
        templateContest.entryFee = entryFee;
        templateContest.salaryCap = salaryCap;
        templateContest.startDate = startDate;
        templateContest.templateMatchEventIds = new ArrayList<>();

        // Se activará 2 dias antes a la fecha del partido
        templateContest.activationAt = new DateTime(startDate).minusDays(2).toDate();

        templateContest.createdAt = GlobalDate.getCurrentDate();

        for (TemplateMatchEvent match: templateMatchEvents) {
            templateContest.optaCompetitionId = match.optaCompetitionId;
            templateContest.templateMatchEventIds.add(match.templateMatchEventId);
        }

        Logger.info("Generate: Template Contest: {}: {}", GlobalDate.formatDate(startDate), templateContest.templateMatchEventIds);

        Model.templateContests().insert(templateContest);
    }

    private ObjectId _lastMathEventId;
    private int _templateCount = 0;
    private final String[] _contestNameSuffixes = {"1", "a", "b", "a", "2", "n", "asfex", "dfggh", "piu", "lorem", "7", "8", "9"};

}
