package model;

import org.bson.types.ObjectId;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class UserInfo {
    public ObjectId userId;
    public String nickName;
    public String facebookID;

    public int wins;
    public int trueSkill = 0;
    public float managerLevel = 0;
    public Money earnedMoney = MoneyUtils.zero;

    public List<String> achievements = new ArrayList<>();

    public UserInfo() {}

    public UserInfo(User theUser) {
        this.userId = theUser.userId;
        this.nickName = theUser.nickName;
        this.wins = theUser.wins;
        this.trueSkill = theUser.trueSkill;
        this.managerLevel = User.managerLevelFromPoints(theUser.managerBalance);
        this.earnedMoney = theUser.earnedMoney;
        this.facebookID = theUser.facebookID;
    }

    public UserInfo(ObjectId userId, String nickName, int wins, int trueSkill, Money earnedMoney) {
        this.userId = userId;
        this.nickName = nickName;
        this.wins = wins;
        this.trueSkill = trueSkill;
        this.earnedMoney = earnedMoney;
    }

    static public List<UserInfo> findAll() {
        List<UserInfo> usersInfo = new ArrayList<>();

        User.findAll().forEach(user -> usersInfo.add(user.info()));

        return usersInfo;
    }

    static public List<UserInfo> findAllWithAchievements() {
        List<UserInfo> usersInfo = new ArrayList<>();

        User.findAll().forEach(user -> usersInfo.add(user.infoWithAchievements()));

        return usersInfo;
    }

    static public List<UserInfo> findGuildWithAchievements(ObjectId guildId) {
        List<UserInfo> usersInfo = new ArrayList<>();

        if (guildId != null) {
            User.findByGuild(guildId).forEach(user -> usersInfo.add(user.infoWithAchievements()));
        }

        return usersInfo;
    }

    static public List<UserInfo> findAllFromContestEntries(List<ContestEntry> contestEntries) {
        List<UserInfo> usersInfo = new ArrayList<>();

        User.find(contestEntries).forEach(user -> usersInfo.add(user.info()));

        return usersInfo;
    }
}