package model;


import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import model.opta.*;
import play.Logger;

import java.util.Date;
import java.util.List;

public class TemplateSoccerPlayer implements JongoId, Initializer {
    @Id
    public ObjectId templateSoccerPlayerId;

    public String optaPlayerId;

    public String name;
    public FieldPos fieldPos;
    public int salary;
    public int fantasyPoints;

    public ObjectId templateTeamId;

    public Date createdAt;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public TemplateSoccerPlayer() {
    }

    public TemplateSoccerPlayer(OptaPlayer optaPlayer, ObjectId aTemplateTeamId) {
        optaPlayerId = optaPlayer.optaPlayerId;
        name = optaPlayer.name;
        fieldPos = getFieldPostFromOpta(optaPlayer.position);
        templateTeamId = aTemplateTeamId;
        createdAt = GlobalDate.getCurrentDate();
    }

    public void Initialize() {
    }

    public ObjectId getId() {
        return templateSoccerPlayerId;
    }

    static FieldPos getFieldPostFromOpta (String optaPosition) {
        FieldPos optaFieldPos;
        if      (optaPosition.startsWith("G"))  optaFieldPos = FieldPos.GOALKEEPER;
        else if (optaPosition.startsWith("D"))  optaFieldPos = FieldPos.DEFENSE;
        else if (optaPosition.startsWith("M"))  optaFieldPos = FieldPos.MIDDLE;
        else                                    optaFieldPos = FieldPos.FORWARD;
        return optaFieldPos;
    }

    static public TemplateSoccerPlayer find(ObjectId templateSoccerPlayerId) {
        return Model.templateSoccerPlayers().findOne("{_id : #}", templateSoccerPlayerId).as(TemplateSoccerPlayer.class);
    }

    public static Iterable<TemplateSoccerPlayer> find(String fieldId, List<ObjectId> idList) {
        return Model.findObjectIds(Model.templateSoccerPlayers(), fieldId, idList).as(TemplateSoccerPlayer.class);
    }

    public boolean hasChanged(OptaPlayer optaPlayer) {
        return !optaPlayerId.equals(optaPlayer.optaPlayerId) ||
               !name.equals(optaPlayer.name) ||
               !fieldPos.equals(getFieldPostFromOpta(optaPlayer.position));
    }

    /**
     * Importar un optaPlayer
     * @param optaPlayer
     * @return
     */
    static public boolean importSoccer(OptaPlayer optaPlayer) {
        TemplateSoccerTeam templateTeam = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaPlayer.teamId).as(TemplateSoccerTeam.class);
        if (templateTeam != null) {
            TemplateSoccerPlayer templateSoccer = new TemplateSoccerPlayer(optaPlayer, templateTeam.templateSoccerTeamId);
            Model.templateSoccerPlayers().withWriteConcern(WriteConcern.SAFE).insert(templateSoccer);

            Model.optaPlayers().update("{id: #}", optaPlayer.optaPlayerId).with("{$set: {dirty: false}}");
        }
        else {
            Logger.error("importSoccer ({}): invalid teamID({})", optaPlayer.optaPlayerId, optaPlayer.teamId);
            return false;
        }
        return true;
    }

    static public boolean isInvalid(OptaPlayer optaPlayer) {
        boolean invalid = (optaPlayer.teamId == null) || optaPlayer.teamId.isEmpty();

        if (!invalid) {
            TemplateSoccerTeam templateTeam = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaPlayer.teamId).as(TemplateSoccerTeam.class);
            invalid = (templateTeam == null);
        }

        return invalid;
    }

}
