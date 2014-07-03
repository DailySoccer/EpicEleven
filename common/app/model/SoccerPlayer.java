package model;

import model.opta.OptaEvent;
import model.opta.OptaMatchEvent;
import org.bson.types.ObjectId;

import java.util.List;

public class SoccerPlayer {
    public ObjectId templateSoccerPlayerId;
    public String optaPlayerId;
    public String name;
    public FieldPos fieldPos;
    public int salary;
    public int playedMatches;
    public int fantasyPoints;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public SoccerPlayer() {
    }

    public SoccerPlayer(TemplateSoccerPlayer template) {
        templateSoccerPlayerId = template.templateSoccerPlayerId;
        optaPlayerId = template.optaPlayerId;
        name = template.name;
        fieldPos = template.fieldPos;
        salary = template.salary;
        fantasyPoints = template.fantasyPoints;
    }

    /**
     * Obtener el numero de partidos jugados por un futbolista en una competicion de una temporada
     * @param seasonId
     * @param competitionId
     * @return
     */
    public void updatePlayedMatches(String seasonId, String competitionId) {
        String id = Model.getPlayerIdFromOpta(optaPlayerId);

        // Buscar el numero de partidos de un jugador en particular
        //  dado que hay multiples eventos de un mismo jugador en un mismo partido, se busca que sean "distintos partidos"
        List<OptaEvent> partidos = Model.optaEvents()
                .distinct("gameId")
                .query("{optaPlayerId: #, seasonId: #, competitionId: #}", id, seasonId, competitionId)
                .as(OptaEvent.class);

        playedMatches = partidos.size();

        if (playedMatches > 0) {
            play.Logger.info("seasonId: {} - competitionId: {} - optaPlayerId: {} = {} partidos",
                    seasonId, competitionId, optaPlayerId, playedMatches);
        }
    }
}
