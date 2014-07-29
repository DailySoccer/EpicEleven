package controllers.admin;

import model.Model;
import org.jongo.MongoCollection;
import play.Logger;

import java.util.Date;


class OptaSimulatorState {
    static public MongoCollection collection() { return Model.jongo().getCollection(collectionName); }

    String stateId = "--unique id--";
    boolean useSnapshot;
    boolean paused;
    Date pause;
    Date lastParsedDate;
    int nextDocToParseIndex;

    static public OptaSimulatorState getInstance() {
        return (OptaSimulatorState._state != null) ? OptaSimulatorState._state : OptaSimulatorState.findOrCreateInstance();
    }

    static public void reset() {
        Logger.info("Simulator: reset State...");
        _state = findOrCreateInstance();
        update();
    }

    static public void initialize(OptaSimulator optaSimulator) {
        OptaSimulatorState state = getInstance();

        optaSimulator._pause = state.paused ? state.pause : null;
        optaSimulator._lastParsedDate = state.lastParsedDate;
        optaSimulator._nextDocToParseIndex = state.nextDocToParseIndex;

        if (state.useSnapshot) {
            OptaSimulator.useSnapshot();
        }

        Logger.info("Simulator: loading State: date({}): {} index", state.lastParsedDate, state.nextDocToParseIndex);
    }

    static public void update() {
        OptaSimulatorState state = getInstance();

        OptaSimulator optaSimulator = OptaSimulator._instance;
        if (optaSimulator != null) {
            state.paused = (optaSimulator._pause != null);
            state.pause =  optaSimulator._pause;

            if (optaSimulator._lastParsedDate != null)
                state.lastParsedDate = optaSimulator._lastParsedDate;

            state.nextDocToParseIndex =  optaSimulator._nextDocToParseIndex;
        }
        state.useSnapshot = ( OptaSimulator._snapshot != null);

        collection().update("{stateId: #}", state.stateId).upsert().with(state);
    }

    static private OptaSimulatorState findOrCreateInstance() {
        _state = collection().findOne().as(OptaSimulatorState.class);
        if (_state == null) {
            _state = stateDefault();
        }
        return _state;
    }

    static private OptaSimulatorState stateDefault() {
        OptaSimulatorState state = new OptaSimulatorState();
        state.useSnapshot = (OptaSimulator._snapshot != null);
        state.lastParsedDate = Model.dateFirstFromOptaXML();
        return state;
    }

    static private OptaSimulatorState _state = null;
    static final private String collectionName = "simulator";
}