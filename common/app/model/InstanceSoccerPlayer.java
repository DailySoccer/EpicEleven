package model;

import org.bson.types.ObjectId;
import utils.ViewProjection;

public class InstanceSoccerPlayer {
    public ObjectId templateSoccerPlayerId;

    public ObjectId templateSoccerTeamId;

    public FieldPos fieldPos;

    public int salary;

    public InstanceSoccerPlayer() { }

    public InstanceSoccerPlayer(TemplateSoccerPlayer template) {
        templateSoccerPlayerId = template.templateSoccerPlayerId;
        templateSoccerTeamId = template.templateTeamId;
        fieldPos = template.fieldPos;
        salary = template.salary;
    }

    static public InstanceSoccerPlayer findOne(ObjectId contestId, ObjectId templateSoccerPlayerId) {
        Contest contest = Model.contests().findOne("{_id : #}", contestId).projection(ViewProjection.get(JsonViews.InstanceSoccerPlayers.class, Contest.class)).as(Contest.class);
        return contest.getInstanceSoccerPlayer(templateSoccerPlayerId);
    }

}
