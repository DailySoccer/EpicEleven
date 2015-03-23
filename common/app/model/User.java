package model;

import com.fasterxml.jackson.annotation.JsonView;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranBonus;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class User {
    @Id
    public ObjectId userId;

	public String firstName;
	public String lastName;
    public String nickName;
	public String email;

    public int wins;

    // TODO: De momento no es realmente un "cache", siempre lo recalculamos
    public Money cachedBalance;
    public Money cachedBonus;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public User() {
    }

	public User(String firstName, String lastName, String nickName, String email) {
		this.firstName = firstName;
		this.lastName = lastName;
        this.nickName = nickName;
		this.email = email;
        createdAt = GlobalDate.getCurrentDate();
	}

    public UserInfo info() {
        return new UserInfo(userId, nickName, wins);
    }

    public User getProfile() {
        cachedBalance = calculateBalance();
        return this;
    }

    /**
     * Query de un usuario por su identificador en mongoDB (verifica la validez del mismo)
     */
    static public User findOne(String userId) {
        User aUser = null;
        Boolean userValid = ObjectId.isValid(userId);
        if (userValid) {
            aUser = findOne(new ObjectId(userId));
        }
        return aUser;
    }

    static public User findByName(String username) {
        return Model.users().findOne("{nickName: #}", username).as(User.class);
    }

    static public User findOne(ObjectId userId) {
        return Model.users().findOne(userId).as(User.class);
    }

    static public List<User> find(List<ContestEntry> contestEntries) {
        List<ObjectId> userObjectIds = new ArrayList<>(contestEntries.size());

        for (ContestEntry entry: contestEntries) {
            userObjectIds.add(entry.userId);
        }

        return ListUtils.asList(Model.findObjectIds(Model.users(), "_id", userObjectIds).as(User.class));
    }

    static public User findByEmail(String email) {
        return Model.users().findOne("{email: #}", email).as(User.class);
    }

    static public List<User> findTests() {
        return ListUtils.asList(Model.users().find("{email: {$regex: \"@test.com\"}, nickName: {$ne: 'Test'}}").as(User.class));
    }

    static public List<User> findBots() {
        return ListUtils.asList(Model.users().find("{email: {$regex: \"@bototron.com\"}, firstName: \"Bototron\"}").as(User.class));
    }

    static public void updateBalance(ObjectId userId, Money balance) {
        Model.users().update(userId).with("{$set: {cachedBalance: #}}", balance.toString());
    }

    public void updateStats() {
        // Buscamos los contests en los que hayamos participado y ganado (position = 0)
        int contestsGanados = (int) Model.contests().count(
                "{ contestEntries: {" +
                    "$elemMatch: {" +
                        "userId: #, " +
                        "position: 0" +
                    "}" +
                "}}", userId);
        Model.users().update(userId).with("{$set: {wins: #}}", contestsGanados);
    }

    public Integer getSeqId() {
        return User.getSeqId(userId);
    }

    public Money calculateBalance() {
        return User.calculateBalance(userId);
    }

    public Money calculateBonus() {
        return User.calculateBonus(userId);
    }

    static public Integer getSeqId(ObjectId userId) {
        List<AccountOp> account = Model.accountingTransactions()
                .aggregate("{$match: { \"accountOps.accountId\": #}}", userId)
                .and("{$unwind: \"$accountOps\"}")
                .and("{$match: {\"accountOps.accountId\": #}}", userId)
                .and("{$project: { \"accountOps.seqId\": 1 }}")
                .and("{$sort: { \"accountOps.seqId\": -1 }}")
                .and("{$limit: 1}")
                .and("{$group: {_id: \"seqId\", accountId: { $first: \"$accountOps.accountId\" }, seqId: { $first: \"$accountOps.seqId\" }}}")
                .as(AccountOp.class);
        return (!account.isEmpty() && account.get(0).seqId != null) ? account.get(0).seqId : 0;
    }

    static public Money calculateBalance(ObjectId userId) {
        List<AccountingTran> accounting = Model.accountingTransactions()
                .aggregate("{$match: { \"accountOps.accountId\": #, state: \"VALID\"}}", userId)
                .and("{$unwind: \"$accountOps\"}")
                .and("{$match: {\"accountOps.accountId\": #}}", userId)
                .and("{$group: {_id: #, _class: { $first: \"$_class\" }, accountOps: { $push: { accountId: \"$accountOps.accountId\", value: \"$accountOps.value\" }}}}", new ObjectId())
                .as(AccountingTran.class);

        Money balance = MoneyUtils.zero;
        if (!accounting.isEmpty()) {
            for (AccountOp op : accounting.get(0).accountOps) {
                balance = MoneyUtils.plus(balance, op.value);
            }
        }
        return balance;
    }

    static public Money calculateBonus(ObjectId userId, Date toDate) {
        List<AccountingTranBonus> transactions = ListUtils.asList(Model.accountingTransactions()
                .find("{ \"accountOps.accountId\": #, state: \"VALID\", type: { $in: [\"BONUS\", \"BONUS_TO_CASH\"] }, createdAt: {$lte: #} }", userId, toDate)
                .as(AccountingTranBonus.class));

        Money balance = MoneyUtils.zero;
        if (!transactions.isEmpty()) {
            for (AccountingTranBonus transacition : transactions) {
                balance = MoneyUtils.plus(balance, transacition.bonus);
            }
        }
        return balance;
    }

    static public Money calculateBonus(ObjectId userId) {
        return calculateBonus(userId, GlobalDate.getCurrentDate());
    }
}
