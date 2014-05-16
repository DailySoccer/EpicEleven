package model;


import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import java.util.Date;


public class PrefabMatchEvent {
    @Id
    public ObjectId prefabMatchEventId;

    public ObjectId prefabSoccerTeamAId;
    public ObjectId prefabSoccerTeamBId;

    public Date startDate;

    public PrefabMatchEvent() {}
}
