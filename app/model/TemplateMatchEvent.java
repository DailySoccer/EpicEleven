package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import java.util.Date;


public class TemplateMatchEvent {
    @Id
    public ObjectId templateMatchEventId;

    public ObjectId templateSoccerTeamAId;
    public ObjectId templateSoccerTeamBId;

    public Date startDate;

    public TemplateMatchEvent() {}
}
