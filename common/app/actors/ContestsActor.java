package actors;

import model.Contest;
import model.GlobalDate;
import model.TemplateContest;

public class ContestsActor extends TickableActor {

    @Override public void onReceive(Object msg) {

        switch ((String)msg) {
            default:
                super.onReceive(msg);
                break;
        }
    }

    @Override protected void onTick() {

        for (TemplateContest templateContest : TemplateContest.findAllByActivationAt(GlobalDate.getCurrentDate())) {
            // El TemplateContest instanciara sus Contests y MatchEvents asociados
            templateContest.instantiate();
        }

        for (Contest contest : Contest.findAllHistoryNotClosed()) {
            contest.closeContest();
        }
    }
}
