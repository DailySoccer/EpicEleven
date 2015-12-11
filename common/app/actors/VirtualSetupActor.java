package actors;

import model.TemplateMatchEvent;
import play.Logger;

public class VirtualSetupActor extends TickableActor {
    public VirtualSetupActor() {
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

        // Creamos los resultados de la simulación
        // TemplateMatchEvent.findAllSimulationsToSetup().forEach(TemplateMatchEvent::setupSimulation);

        Logger.debug("Virtual Setup: elapsed: {}", System.currentTimeMillis() - startTime);
    }

    @Override protected void onSimulatorTick() {
        onTick();
    }

}