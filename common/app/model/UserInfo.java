package model;

import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

    static public List<UserInfo> findAllFromContestEntries(List<ContestEntry> contestEntries) {

        Iterable<User> users = User.find(contestEntries);
        List<UserInfo> usersInfo = new ArrayList<>();

        for (User user : users) {
            usersInfo.add(user.info());
        }

        return usersInfo;
    }
}