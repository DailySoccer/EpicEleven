package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.List;
import java.util.ArrayList;

public class Contest {

    @Id
    public ObjectId contestId;

    public String name;

    public List<ObjectId> currentUserIds = new ArrayList<>();
    public int maxUsers;

    public ObjectId templateContestId;

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public Contest() {
    }

    public Contest(TemplateContest template) {
        templateContestId = template.templateContestId;
        name = template.name;
    }
}
