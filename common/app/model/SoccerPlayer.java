package model;

import model.opta.OptaEvent;
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

    public SoccerPlayer() { }

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
     */
    public void updatePlayedMatches(String seasonId, String competitionId) {
        // Buscar el numero de partidos de un jugador en particular
        // dado que hay multiples eventos de un mismo jugador en un mismo partido, se busca que sean "distintos partidos"
        List<OptaEvent> partidos = Model.optaEvents()
                .distinct("gameId")
                .query("{optaPlayerId: #, seasonId: #, competitionId: #}", optaPlayerId, seasonId, competitionId)
                .as(OptaEvent.class);

        playedMatches = partidos.size();

        if (playedMatches > 0) {
            play.Logger.info("seasonId: {} - competitionId: {} - optaPlayerId: {} = {} partidos",
                             seasonId, competitionId, optaPlayerId, playedMatches);
        }
    }
}
