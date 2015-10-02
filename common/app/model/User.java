package model;

import com.fasterxml.jackson.annotation.JsonView;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranBonus;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
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

    public Money goldBalance;
    public Money managerBalance;
    public Money energyBalance;

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
        cachedBonus = calculateBonus();
        goldBalance = calculateGoldBalance();
        managerBalance = calculateManagerBalance();
        energyBalance = Money.zero(MoneyUtils.CURRENCY_ENERGY);
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
        if (balance.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
            Model.users().update(userId).with("{$set: {goldBalance: #}}", balance.toString());
        }
        else if (balance.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
            Model.users().update(userId).with("{$set: {managerBalance: #}}", balance.toString());
        }
        else {
            Model.users().update(userId).with("{$set: {energyBalance: #}}", balance.toString());
        }
        // Model.users().update(userId).with("{$set: {cachedBalance: #}}", balance.toString());
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
        return User.calculateGoldBalance(userId);
    }
    public Money calculateGoldBalance() { return User.calculateGoldBalance(userId); }
    public Money calculateManagerBalance() {
        return User.calculateManagerBalance(userId);
    }
    public Money calculateEnergyBalance() {
        return User.calculateEnergyBalance(userId);
    }

    public Money calculateBonus() {
        return User.calculateBonus(userId);
    }

    public boolean hasMoney(Money money) {
        return hasMoney(userId, money);
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

    static public Money calculatePrizes(ObjectId userId) {
        List<AccountingTran> accounting = Model.accountingTransactions()
                .aggregate("{$match: { \"accountOps.accountId\": #, type: #, state: \"VALID\"}}", userId, AccountingTran.TransactionType.PRIZE)
                .and("{$unwind: \"$accountOps\"}")
                .and("{$match: {\"accountOps.accountId\": #, type: #}}", userId, AccountingTran.TransactionType.PRIZE)
                .and("{$group: {_id: #, _class: { $first: \"$_class\" }, accountOps: { $push: { accountId: \"$accountOps.accountId\", value: \"$accountOps.value\" }}}}", new ObjectId())
                .as(AccountingTran.class);

        Money balance = MoneyUtils.zero;
        if (!accounting.isEmpty()) {
            for (AccountOp op : accounting.get(0).accountOps) {
                balance = MoneyUtils.plus(balance, op.asMoney());
            }
        }
        return balance;
    }

    static public Money calculateBalance(ObjectId userId, String currencyUnit) {
        List<AccountingTran> accounting = Model.accountingTransactions()
                .aggregate("{$match: { \"accountOps.accountId\": #, state: \"VALID\"}}", userId)
                .and("{$unwind: \"$accountOps\"}")
                .and("{$match: {\"accountOps.accountId\": #, \"accountOps.currencyCode\": #}}", userId, currencyUnit)
                .and("{$group: {_id: #, _class: { $first: \"$_class\" }, accountOps: { $push: { accountId: \"$accountOps.accountId\", value: \"$accountOps.value\" }}}}", new ObjectId())
                .as(AccountingTran.class);

        Money balance = MoneyUtils.zero(currencyUnit);
        if (!accounting.isEmpty()) {
            for (AccountOp op : accounting.get(0).accountOps) {
                balance = balance.plus(op.asMoney());
            }
        }
        return balance;
    }

    static public boolean hasMoney(ObjectId userId, Money money) {
        Money balance;
        if (money.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
            balance = calculateGoldBalance(userId);
        }
        else if (money.getCurrencyUnit().equals(MoneyUtils.CURRENCY_MANAGER)) {
            balance = calculateManagerBalance(userId);
        }
        else if (money.getCurrencyUnit().equals(MoneyUtils.CURRENCY_ENERGY)) {
            balance = calculateEnergyBalance(userId);
        }
        else {
            // El usuario no tendrá dinero, si la moneda es diferente
            Logger.error("User not has Money: {}", money.toString());
            return false;
        }
        return MoneyUtils.compareTo(balance, money) >= 0;
    }

    static public Money calculateGoldBalance(ObjectId userId) {
        return calculateBalance(userId, MoneyUtils.CURRENCY_GOLD.getCode());
    }

    static public Money calculateManagerBalance(ObjectId userId) {
        return calculateBalance(userId, MoneyUtils.CURRENCY_MANAGER.getCode());
    }

    static public Money calculateEnergyBalance(ObjectId userId) {
        return calculateBalance(userId, MoneyUtils.CURRENCY_ENERGY.getCode());
    }

    static public Money calculateBonus(ObjectId userId, Date toDate) {
        List<AccountingTranBonus> transactions = ListUtils.asList(Model.accountingTransactions()
                .find("{ \"accountOps.accountId\": #, state: \"VALID\", type: { $in: [\"BONUS\", \"BONUS_TO_CASH\"] }, createdAt: {$lte: #} }", userId, toDate)
                .as(AccountingTranBonus.class));

        Money balance = MoneyUtils.zero;
        if (!transactions.isEmpty()) {
            for (AccountingTranBonus transaction : transactions) {
                balance = MoneyUtils.plus(balance, transaction.bonus);
            }
        }
        return balance;
    }

    static public Money calculateBonus(ObjectId userId) {
        return calculateBonus(userId, GlobalDate.getCurrentDate());
    }
}
