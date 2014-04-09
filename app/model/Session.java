package model;

import org.bson.types.ObjectId;
import java.util.Date;


public class Session {
    private ObjectId _id;

    public String sessionToken;
    public ObjectId userId;
    public Date createdAt;

    public Session(String sessionToken, ObjectId userId, Date createdAt) {
        this.sessionToken = sessionToken;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public Session() {}
}
