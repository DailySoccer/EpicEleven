package actors;

import model.*;
import play.Logger;

public class VirtualMatchEventActor extends TickableActor {
    public VirtualMatchEventActor() {
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

        // 1 seg. reales = x seg. simulados
        final long TIME_MULTIPLIER = 10;

        // Actualizamos el estado de los partidos simulados para que se actualicen poco a poco ("live")
        TemplateMatchEvent.findAllSimulationsToUpdate().forEach(matchEvent -> matchEvent.updateSimulationState(TIME_MULTIPLIER));

        Logger.debug("Virtual MatchEvent: elapsed: {}", System.currentTimeMillis() - startTime);
    }

    @Override protected void onSimulatorTick() {
        onTick();
    }

}
