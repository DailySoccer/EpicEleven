package actors;

import model.*;
import play.Logger;
import play.Play;

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

        // Activamos los partidos simulados que deberían haber comenzado
        TemplateMatchEvent.findAllSimulationsByStartDate().forEach(TemplateMatchEvent::startSimulation);

        // Actualizamos el estado de los partidos simulados para que se actualicen poco a poco ("live")
        TemplateMatchEvent.findAllSimulationsToUpdate().forEach(matchEvent -> matchEvent.updateSimulationState(_timeMultiplier));

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
