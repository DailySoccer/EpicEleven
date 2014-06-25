package model.opta;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.ArrayList;

/**
 * Created by gnufede on 03/06/14.
 */
public class OptaTeam {
    @Id
    public ObjectId optaTeamId;
    public String id;
    public String name;
    public String shortName;
    //public ArrayList<Integer> competitionIds;
    public long updatedTime;
}
