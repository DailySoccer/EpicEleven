package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.Date;

public class UserInfo {
    public ObjectId userId;
    public String firstName;
    public String lastName;
    public String nickName;

    public UserInfo(ObjectId userId, String firstName, String lastName, String nickName) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.nickName = nickName;
    }
}