package model;


import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.WriteConcern;
import model.opta.OptaEvent;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;


public class LiveMatchEvent {
    @Id
    public ObjectId liveMatchEventId;
}
