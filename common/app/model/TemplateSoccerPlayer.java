package model;


import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import model.opta.*;
import play.Logger;
import utils.ListUtils;

import java.util.ArrayList;
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

    public List<SoccerPlayerStats> stats = new ArrayList<>();

    public TemplateSoccerPlayer() { }

    public TemplateSoccerPlayer(OptaPlayer optaPlayer, ObjectId aTemplateTeamId) {
        optaPlayerId = optaPlayer.optaPlayerId;
        name = optaPlayer.name;
        fieldPos = transformToFieldPosFromOptaPos(optaPlayer.position);
        templateTeamId = aTemplateTeamId;
        createdAt = GlobalDate.getCurrentDate();
    }

    public void Initialize() { }

    public ObjectId getId() {
        return templateSoccerPlayerId;
    }

    static FieldPos transformToFieldPosFromOptaPos(String optaPosition) {
        FieldPos optaFieldPos = FieldPos.FORWARD;

        if      (optaPosition.startsWith("G"))  optaFieldPos = FieldPos.GOALKEEPER;
        else if (optaPosition.startsWith("D"))  optaFieldPos = FieldPos.DEFENSE;
        else if (optaPosition.startsWith("M"))  optaFieldPos = FieldPos.MIDDLE;
        else if (optaPosition.startsWith("F"))  optaFieldPos = FieldPos.FORWARD;
        else {
            Logger.error("Opta Position not registered yet: {}", optaPosition);
        }
        return optaFieldPos;
    }

    static public TemplateSoccerPlayer findOne(ObjectId templateSoccerPlayerId) {
        return Model.templateSoccerPlayers().findOne("{_id : #}", templateSoccerPlayerId).as(TemplateSoccerPlayer.class);
    }

    static public TemplateSoccerPlayer findOneFromOptaId(String optaPlayerId) {
        return Model.templateSoccerPlayers().findOne("{optaPlayerId: #}", optaPlayerId).as(TemplateSoccerPlayer.class);
    }

    public static List<TemplateSoccerPlayer> findAll() {
        return ListUtils.asList(Model.templateSoccerPlayers().find().as(TemplateSoccerPlayer.class));
    }

    public static List<TemplateSoccerPlayer> findAll(List<ObjectId> idList) {
        return ListUtils.asList(Model.findObjectIds(Model.templateSoccerPlayers(), "_id", idList).as(TemplateSoccerPlayer.class));
    }

    static public List<TemplateSoccerPlayer> findAllFromTemplateTeam(ObjectId templateSoccerTeamId) {
        return ListUtils.asList(Model.templateSoccerPlayers().find("{ templateTeamId: # }", templateSoccerTeamId).as(TemplateSoccerPlayer.class));
    }


    public boolean hasChanged(OptaPlayer optaPlayer) {
        return !optaPlayerId.equals(optaPlayer.optaPlayerId) ||
               !name.equals(optaPlayer.name) ||
               !fieldPos.equals(transformToFieldPosFromOptaPos(optaPlayer.position));
    }

    /**
     * Importar un optaPlayer
     */
    static public boolean importSoccer(OptaPlayer optaPlayer) {
        TemplateSoccerTeam templateTeam = Model.templateSoccerTeams().findOne("{optaTeamId: #}", optaPlayer.teamId).as(TemplateSoccerTeam.class);
        if (templateTeam != null) {
            TemplateSoccerPlayer templateSoccer = new TemplateSoccerPlayer(optaPlayer, templateTeam.templateSoccerTeamId);

            TemplateSoccerPlayerMetadata templateSoccerPlayerMetadata = TemplateSoccerPlayerMetadata.findOne(optaPlayer.optaPlayerId);
            templateSoccer.salary = templateSoccerPlayerMetadata!=null? templateSoccerPlayerMetadata.salary: 79797;

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
