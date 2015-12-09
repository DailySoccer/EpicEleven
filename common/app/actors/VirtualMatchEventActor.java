package actors;

import model.*;
import play.Logger;
import play.Play;
import utils.ListUtils;

import java.util.List;

public class VirtualMatchEventActor extends TickableActor {
    public VirtualMatchEventActor() {
        readConfig();
    }

    @Override
    public void onReceive(Object msg) {
        try {
            switch ((String)msg) {
                default:
                    super.onReceive(msg);
                    break;
            }
        }
        catch (Exception exc) {
            // Logger.info("Timeout 1026, probablemente el servidor esta saturado...");
        }
    }

    @Override protected void onTick() {
        long startTime = System.currentTimeMillis();

        // Buscar los templateSoccerPlayers sin estadísticas de eventsCount actualizadas
        List<TemplateSoccerPlayer> templateSoccerPlayerList = ListUtils.asList(
                Model.templateSoccerPlayers().find("{stats: { $elemMatch: { playedMinutes: {$gt: 0}, eventsCount: {$exists: false} }}}")
                        .as(TemplateSoccerPlayer.class));
        Logger.debug("TemplateSoccerPlayer: EventsCount = NULL: {} players", templateSoccerPlayerList.size());
        templateSoccerPlayerList.forEach(TemplateSoccerPlayer::updateEventStats);

        // Activamos los partidos simulados que deberían haber comenzado
        TemplateMatchEvent.findAllSimulationsByStartDate().forEach(TemplateMatchEvent::startSimulation);

        // Actualizamos el estado de los partidos simulados para que se actualicen poco a poco ("live")
        // TemplateMatchEvent.findAllSimulationsToUpdate().forEach(matchEvent -> matchEvent.updateSimulationState(_timeMultiplier));

        Logger.debug("Virtual MatchEvent: elapsed: {}", System.currentTimeMillis() - startTime);
    }

    @Override protected void onSimulatorTick() {
        onTick();
    }

    private void readConfig() {
        _timeMultiplier = Play.application().configuration().getLong("virtualMatchEventActor.timeMultiplier");
        Logger.debug("VirtualMatchEventActor: Time Multiplier: {}", _timeMultiplier);
    }

    // 1 seg. reales = x seg. simulados
    private long _timeMultiplier = 1;
}
