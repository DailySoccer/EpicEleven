package model;

import org.bson.types.ObjectId;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class UserInfo {
    public ObjectId userId;
    public String nickName;

    public int wins;
    public int trueSkill = 0;
    public Money earnedMoney = MoneyUtils.zero;

    public UserInfo() {}

    public UserInfo(ObjectId userId, String nickName, int wins, int trueSkill, Money earnedMoney) {
        this.userId = userId;
        this.nickName = nickName;
        this.wins = wins;
        this.trueSkill = trueSkill;
        this.earnedMoney = earnedMoney;
    }

    static public List<UserInfo> findAll() {
        List<User> users = User.findAll();

        List<UserInfo> usersInfo = new ArrayList<>();

        for (User user : users) {
            usersInfo.add(user.info());
        }

        return usersInfo;
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