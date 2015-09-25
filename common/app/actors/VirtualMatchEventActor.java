package actors;

import akka.actor.UntypedActor;
import model.*;
import model.opta.*;
import org.bson.types.ObjectId;
import play.Logger;

import java.util.*;
import java.util.stream.Collectors;

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

        List<TemplateMatchEvent> matchEvents = TemplateMatchEvent.findAllSimulationsByStartDate();
        for(TemplateMatchEvent matchEvent : matchEvents) {
            matchEvent.updateSimulationState();
        }

        Logger.debug("Virtual MatchEvent: elapsed: {}", System.currentTimeMillis() - startTime);
    }

    @Override protected void onSimulatorTick() {
        onTick();
    }

}
