package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;
import java.util.Date;


public class Session {
    public String sessionToken;
    public ObjectId userId;
    public Date createdAt;

    public Session(String sessionToken, ObjectId userId, Date createdAt) {
        this.sessionToken = sessionToken;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public Session() {}

    @JsonView(JSONViews.Private.class)
    private ObjectId _id;
}
