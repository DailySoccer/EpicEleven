package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.List;

public class ContestEntry {
    @Id
    public ObjectId contestEntryId;

    public ObjectId userId;     // Usuario que creo el equipo

    public ObjectId contestId;  // Contest en el que se ha inscrito el equipo

    public List<ObjectId> soccerIds;

    public ContestEntry(ObjectId userId, ObjectId contestId, List<ObjectId> soccerIds) {
        this.userId = userId;
        this.contestId = contestId;
        this.soccerIds = soccerIds;
    }

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public ContestEntry() {}
}
