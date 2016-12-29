package model;

import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.HashMap;

public class UserRanking extends HashMap<String, Object> {
    private final static String USERID = "userId";
    private final static String NICKNAME = "nickName";
    private final static String TRUESKILL = "trueSkill";
    private final static String EARNEDMONEY = "earnedMoney";

    private final static String SKILLRANK = "skillRank";
    private final static String GOLDRANK = "goldRank";

    public UserRanking(User user) {
        put(USERID, user.userId.toString());
        put(NICKNAME, user.nickName);

        if (user.trueSkill != 0) {
            put(TRUESKILL, user.trueSkill);
        }

        if (user.earnedMoney.isPositive()) {
            put(EARNEDMONEY, user.earnedMoney);
        }
    }

    public String getUserId() { return (String) get(USERID); }
    public String getNickName() { return (String) get(NICKNAME); }
    public Integer getTrueSkill() { return containsKey(TRUESKILL) ? (Integer) get(TRUESKILL) : 0; }
    public Money getEarnedMoney() { return containsKey(EARNEDMONEY) ? (Money) get(EARNEDMONEY) : MoneyUtils.zero; }

    public int getSkillRank() { return containsKey(SKILLRANK) ? (Integer) get(SKILLRANK) : -1; }
    public int getGoldRank() { return containsKey(GOLDRANK) ? (Integer) get(GOLDRANK) : -1; }
}
