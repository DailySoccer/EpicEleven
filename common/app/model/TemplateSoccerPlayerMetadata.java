package model;

/**
 * Created by gnufede on 28/07/14.
 */
public class TemplateSoccerPlayerMetadata {
    public String optaPlayerId;
    public String name;
    public int salary;

    public TemplateSoccerPlayerMetadata(){}


    public static TemplateSoccerPlayerMetadata findOne(String optaPlayerId) {
        // Ojo al parche: optaPlayer.optaPlayerId se pasa aqui como int porque el importador de CSV es demasiado automagico
        // y no encontre manera de decirle que coja un int como string
        return Model.templateSoccerPlayersMetadata().findOne("{ optaPlayerId: #}", Integer.parseInt(optaPlayerId)).as(TemplateSoccerPlayerMetadata.class);
    }
}
