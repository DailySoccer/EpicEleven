package model;

import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class UserInfo {
    public ObjectId userId;
    public String nickName;

    public int wins;

    public UserInfo() {}

    public UserInfo(ObjectId userId, String nickName, int wins) {
        this.userId = userId;
        this.nickName = nickName;
        this.wins = wins;
    }

    static public List<UserInfo> findAllFromContestEntries(List<ContestEntry> contestEntries) {

        List<User> users = User.find(contestEntries);
        List<UserInfo> usersInfo = new ArrayList<>();

        for (User user : users) {
            usersInfo.add(user.info());
        }

        return usersInfo;
    }
}