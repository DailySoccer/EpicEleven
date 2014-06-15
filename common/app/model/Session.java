package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;
import java.util.Date;


public class Session {
    public String sessionToken;

    @JsonView(JsonViews.NotForClient.class)
    public ObjectId userId;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public Session(String sessionToken, ObjectId userId, Date createdAt) {
        this.sessionToken = sessionToken;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public Session() {}

    @JsonView(JsonViews.NotForClient.class)
    private ObjectId _id;
}
