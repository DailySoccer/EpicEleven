package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;
import org.jongo.Aggregate;
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
        List<SeqId> seqId = Model.transactions()
                .aggregate("{$match: { \"changes.accounts.accountId\": #}}", userId)
                .and("{$unwind: \"$changes.accounts\"}")
                .and("{$match: {\"changes.accounts.accountId\": #}}", userId)
                .and("{$project: { \"changes.accounts.seqId\": 1 }}")
                .and("{$sort: { \"changes.accounts.seqId\": -1 }}")
                .and("{$limit: 1}")
                .and("{$group: {_id: \"seqId\", seqId: { $first: \"$changes.accounts.seqId\" }}}")
                .as(SeqId.class);
        return (!seqId.isEmpty() && seqId.get(0).seqId != null) ? seqId.get(0).seqId : 0;
    }

    public BigDecimal calculateBalance() {
        List<Balance> balance = Model.transactions()
                .aggregate("{$match: { \"changes.accounts.accountId\": #, state: \"VALID\"}}", userId)
                .and("{$unwind: \"$changes.accounts\"}")
                .and("{$match: {\"changes.accounts.accountId\": #}}", userId)
                .and("{$group: {_id: \"total\", total: { $sum: \"$changes.accounts.value\" }}}")
                .as(Balance.class);
        return (!balance.isEmpty()) ? balance.get(0).total : new BigDecimal(0);
    }
}

class SeqId {
    Integer seqId;
}

class Balance {
    BigDecimal total;
}