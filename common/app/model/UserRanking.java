package model;

import org.joda.money.Money;
import utils.MoneyUtils;

import java.util.HashMap;

public class UserRanking extends HashMap<String, Object> {
    private final static String USERID = "userId";
    private final static String NICKNAME = "nickName";
    private final static String TRUESKILL = "trueSkill";
    private final static String EARNEDMONEY = "earnedMoney";

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
    public Integer getTrueSkill() { return (Integer) get(TRUESKILL); }
    public Money getEarnedMoney() { return containsKey(EARNEDMONEY) ? (Money) get(EARNEDMONEY) : MoneyUtils.zero; }
}
