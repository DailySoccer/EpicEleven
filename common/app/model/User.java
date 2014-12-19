package model;

import com.fasterxml.jackson.annotation.JsonView;
import model.accounting.AccountOp;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.math.BigDecimal;
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
    public BigDecimal cachedBalance;

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
        return new UserInfo(userId, firstName, lastName, nickName, wins);
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
        //User aUser = null;
        return Model.users().findOne("{nickName: #}", username).as(User.class);
        //return aUser;
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

    static public void updateBalance(ObjectId userId, BigDecimal balance) {
        Model.users().update(userId).with("{$set: {cachedBalance: #}}", balance.doubleValue());
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

    public BigDecimal calculateBalance() {
        return User.calculateBalance(userId);
    }

    static public Integer getSeqId(ObjectId userId) {
        List<AccountOp> account = Model.accountingTransactions()
                .aggregate("{$match: { \"changes.accounts.accountId\": #}}", userId)
                .and("{$unwind: \"$changes.accounts\"}")
                .and("{$match: {\"changes.accounts.accountId\": #}}", userId)
                .and("{$project: { \"changes.accounts.seqId\": 1 }}")
                .and("{$sort: { \"changes.accounts.seqId\": -1 }}")
                .and("{$limit: 1}")
                .and("{$group: {_id: \"seqId\", accountId: { $first: \"$changes.accounts.accountId\" }, seqId: { $first: \"$changes.accounts.seqId\" }}}")
                .as(AccountOp.class);
        return (!account.isEmpty() && account.get(0).seqId != null) ? account.get(0).seqId : 0;
    }

    static public BigDecimal calculateBalance(ObjectId userId) {
        List<AccountOp> account = Model.accountingTransactions()
                .aggregate("{$match: { \"changes.accounts.accountId\": #, state: \"VALID\"}}", userId)
                .and("{$unwind: \"$changes.accounts\"}")
                .and("{$match: {\"changes.accounts.accountId\": #}}", userId)
                .and("{$group: {_id: \"value\", accountId: { $first: \"$changes.accounts.accountId\" }, value: { $sum: \"$changes.accounts.value\" }}}")
                .as(AccountOp.class);
        return (!account.isEmpty()) ? account.get(0).value : new BigDecimal(0);
    }
}
