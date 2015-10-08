package model;

import com.fasterxml.jackson.annotation.JsonView;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranBonus;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Minutes;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;
import utils.MoneyUtils;
import utils.TrueSkillHelper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class User {
    static final int MINUTES_TO_RELOAD_ENERGY = 15;
    static final BigDecimal MAX_ENERGY = new BigDecimal(10);
    static final long HOURS_TO_DECAY = 48;
    static final float PERCENT_TO_DECAY = 0.5f;

    static final long[] MANAGER_POINTS = new long[] {
      0, 65, 125, 250, 500, 1000
    };

    public class Rating {
        public double Mean;
        public double StandardDeviation;

        public Rating() {}

        public Rating(double mean, double standardDeviation) {
            this.Mean = mean;
            this.StandardDeviation = standardDeviation;
        };
    }

    @Id
    public ObjectId userId;

	public String firstName;
	public String lastName;
    public String nickName;
	public String email;

    public int wins;

    // True Skill
    public int trueSkill = 0;
    // Rating de True Skill
    public Rating rating = new Rating(TrueSkillHelper.INITIAL_MEAN, TrueSkillHelper.INITIAL_SD);
    // Torneos que se han tenido en cuenta para calcular su rating
    public List<ObjectId> contestsRating = new ArrayList<>();

    public Money earnedMoney = MoneyUtils.zero;

    // TODO: De momento no es realmente un "cache", siempre lo recalculamos
    public Money cachedBalance;
    public Money cachedBonus;

    public Money goldBalance        = Money.zero(MoneyUtils.CURRENCY_GOLD);
    public Money managerBalance     = Money.zero(MoneyUtils.CURRENCY_MANAGER);
    public Money energyBalance      = Money.of(MoneyUtils.CURRENCY_ENERGY, MAX_ENERGY);

    // La última fecha en la que se recalculó la energía
    public Date lastUpdatedEnergy;

    public float managerLevel = 0;

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
        return new UserInfo(userId, nickName, wins, trueSkill, earnedMoney);
    }

    public User getProfile() {
        cachedBalance = calculateBalance();
        cachedBonus = calculateBonus();
        goldBalance = calculateGoldBalance();
        managerBalance = calculateManagerBalance();
        energyBalance = calculateEnergyBalance();
        earnedMoney = calculatePrizes(MoneyUtils.CURRENCY_GOLD);
        managerLevel = managerLevelFromPoints(managerBalance);
        // Logger.debug("gold: {} manager: {} energy: {}", goldBalance, managerBalance, energyBalance);
        return this;
    }

    public void saveEnergy() {
        Model.users().update(userId).with("{$set: {energyBalance: #, lastUpdatedEnergy: #}}", energyBalance.toString(), lastUpdatedEnergy);
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

    static public List<User> findAll() {
        return ListUtils.asList(Model.users().find().as(User.class));
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
        earnedMoney = calculatePrizes(MoneyUtils.CURRENCY_GOLD);

        // Buscamos los contests en los que hayamos participado y ganado (position = 0)
        int contestsGanados = (int) Model.contests().count(
                "{ contestEntries: {" +
                    "$elemMatch: {" +
                        "userId: #, " +
                        "position: 0" +
                    "}" +
                "}}", userId);
        Model.users().update(userId).with("{$set: {wins: #, earnedMoney: #}}", contestsGanados, earnedMoney.toString());
    }

    public void updateTrueSkillByContest(ObjectId contestId) {
        // Garantizamos que un determinado contest no influye varias veces en el cálculo del trueSkill
        Model.users().update("{_id: #, contestsRating: {$nin: [#]} }", userId, contestId).with("{$set: {trueSkill: #, rating: #}, $push: {contestsRating: #}}", trueSkill, rating, contestId);
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
        // La energía no lo controlaremos con las transacciones, sino por medio del valor actual y el tiempo transcurrido desde que se usó la última vez (lastUpdatedEnergy)

        // Si la energía está incompleta, habrá que averiguar si ya la ha recargado...
        if (energyBalance.getAmount().compareTo(MAX_ENERGY) < 0) {
            refreshEnergy();
            saveEnergy();
        }

        return energyBalance;
    }

    public Money calculateBonus() {
        return User.calculateBonus(userId);
    }

    public Money calculatePrizes(CurrencyUnit currenctyUnit) {
        return User.calculatePrizes(userId, currenctyUnit);
    }

    public Money calculateGoldPrizes() {
        return calculatePrizes(MoneyUtils.CURRENCY_GOLD);
    }

    public boolean useEnergy(Money energy) {
        boolean result = false;

        refreshEnergy();
        if (MoneyUtils.compareTo(energyBalance, energy) >= 0) {

            // Si la energía la tenemos al máximo, podemos actualizar la fecha con la de NOW
            if (energyBalance.getAmount().equals(MAX_ENERGY)) {
                lastUpdatedEnergy = GlobalDate.getCurrentDate();
            }
            else {
                // La energía ya se estaba recargando, tiene que estar correctamente actualizada por el refreshEnergy previo
            }
            energyBalance = energyBalance.minus(energy);

            saveEnergy();

            result = true;
        }

        return result;
    }

    private void refreshEnergy() {
        // Si la energía está incompleta, habrá que averiguar si ya la ha recargado...
        if (energyBalance.getAmount().compareTo(MAX_ENERGY) < 0) {

            if (lastUpdatedEnergy != null) {
                DateTime lastUpdated = new DateTime(lastUpdatedEnergy);
                DateTime now = new DateTime(GlobalDate.getCurrentDate());
                Minutes minutes = Minutes.minutesBetween(lastUpdated, now);

                // Cuántos intervalos de incremento de energía se han producido?
                int intervalos = minutes.dividedBy(MINUTES_TO_RELOAD_ENERGY).getMinutes();
                while (intervalos > 0 && energyBalance.getAmount().compareTo(MAX_ENERGY) < 0) {
                    // Aumentar la energía
                    energyBalance = energyBalance.plus(1.0);
                    // Avanzamos el tiempo
                    lastUpdated = lastUpdated.plusMinutes(MINUTES_TO_RELOAD_ENERGY);

                    intervalos--;
                }
                lastUpdatedEnergy = lastUpdated.toDate();
            }
            else {
                // Registrar que en la fecha actual el usuario tiene la energía al máximo (dado que nunca la ha usado)
                lastUpdatedEnergy = GlobalDate.getCurrentDate();
                energyBalance = Money.of(MoneyUtils.CURRENCY_ENERGY, MAX_ENERGY);
            }
        }
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

    // Para registrar los premios ganado
    static public class PrizeOp {
        public Money value;
    }

    static public Money calculatePrizes(ObjectId userId, CurrencyUnit currencyUnit) {
        List<PrizeOp> prizeOps = Model.accountingTransactions()
                .aggregate("{$match: { \"accountOps.accountId\": #, \"accountOps.currencyCode\": #, type: #, state: \"VALID\"}}", userId, currencyUnit.toString(), AccountingTran.TransactionType.PRIZE)
                .and("{$unwind: \"$accountOps\"}")
                .and("{$match: {\"accountOps.accountId\": #}}", userId)
                .and("{$project: {value: \"$accountOps.value\"}}")
                .as(PrizeOp.class);

        Money balance = MoneyUtils.zero(currencyUnit.getCode());
        if (!prizeOps.isEmpty()) {
            for (PrizeOp op : prizeOps) {
                balance = MoneyUtils.plus(balance, op.value);
            }
        }
        return balance;
    }

    // Para registrar las operaciones
    static public class BalanceOp {
        public Money value;
    }

    // Para registrar los premios conseguidos de Manager Points
    static public class ManagerBalanceOp extends BalanceOp {
        public Date createdAt;
    }

    static public Money calculateBalance(ObjectId userId, String currencyUnit) {

        List<BalanceOp> accountingOps = Model.accountingTransactions()
                .aggregate("{$match: { \"accountOps.accountId\": #, \"accountOps.currencyCode\": #, state: \"VALID\"}}", userId, currencyUnit)
                .and("{$unwind: \"$accountOps\"}")
                .and("{$match: {\"accountOps.accountId\": #}}", userId)
                .and("{$project: {value: \"$accountOps.value\"}}")
                .as(BalanceOp.class);

        Money balance = MoneyUtils.zero(currencyUnit);
        if (!accountingOps.isEmpty()) {
            for (BalanceOp op : accountingOps) {
                balance = balance.plus(op.value);
            }
        }
        return balance;
    }

    static public Money calculateGoldBalance(ObjectId userId) {
        return calculateBalance(userId, MoneyUtils.CURRENCY_GOLD.getCode());
    }

    static public Money calculateManagerBalance(ObjectId userId) {
        String currencyUnit = MoneyUtils.CURRENCY_MANAGER.getCode();

        // Queremos las operaciones sobre los Manager Points ordenadas por tiempo, dado que existe un factor de "decay" a lo largo del tiempo
        List<ManagerBalanceOp> managerOps = Model.accountingTransactions()
                .aggregate("{$match: { \"accountOps.accountId\": #, \"accountOps.currencyCode\": #, type: #, state: \"VALID\"}}", userId, currencyUnit, AccountingTran.TransactionType.PRIZE)
                .and("{$sort : { createdAt : 1 }}")
                .and("{$unwind: \"$accountOps\"}")
                .and("{$match: {\"accountOps.accountId\": #}}", userId)
                .and("{$project: {createdAt: 1, value: \"$accountOps.value\"}}")
                .as(ManagerBalanceOp.class);

        Money balance = MoneyUtils.zero(currencyUnit);
        if (!managerOps.isEmpty()) {
            Logger.debug("-------------> Manager Points: UserId: {}", userId);

            // Decay de  los Manager Points entre tiempo (ganancias)
            DateTime lastDate = null;
            for (ManagerBalanceOp op : managerOps) {
                DateTime dateTime = new DateTime(op.createdAt);
                if (lastDate != null) {
                    Duration duration = new Duration(lastDate, dateTime);
                    balance = decayManagerPoints(duration, balance);
                }
                balance = MoneyUtils.plus(balance, op.value);
                lastDate = dateTime;
            }

            // Decay hasta el día de hoy
            Duration duration = new Duration(lastDate, new DateTime(GlobalDate.getCurrentDate()));
            balance = decayManagerPoints(duration, balance);
        }

        return balance;
    }

    static private Money pointsFromManagerLevel(float managerLevel) {
        int level = (int) managerLevel;
        int acc = 0;
        if (level < 5) {
            float pointsToLevelUp = (float) (MANAGER_POINTS[level+1] - MANAGER_POINTS[level]);
            float remainder = managerLevel - level;
            acc = (int) (remainder * pointsToLevelUp);
        }
        return Money.zero(MoneyUtils.CURRENCY_MANAGER).plus(MANAGER_POINTS[level] + acc);
    }

    static private float managerLevelFromPoints(Money managerPoints) {
        long points = managerPoints.getAmount().longValue();
        int level = 0;
        while (level+1 < MANAGER_POINTS.length && points >= MANAGER_POINTS[level+1]) {
            level++;
        }

        float acc = 0;
        if (level < 5) {
            float pointsToLevelUp = (float) (MANAGER_POINTS[level+1] - MANAGER_POINTS[level]);
            float remainder = (float) (points - MANAGER_POINTS[level]);
            acc = remainder / pointsToLevelUp;
        }
        return (float)level + acc;
    }

    static private Money decayManagerPoints(Money managerPoints, float percentToDecay) {
        // Aplicarle la penalización al nivel de Manager
        float level = managerLevelFromPoints(managerPoints) - percentToDecay;
        if (level < 0f) {
            level = 0f;
        }

        // Volver a convertir el nivel a manager Points
        managerPoints = pointsFromManagerLevel(level);

        Logger.debug(">> decay: manager: {} level: {}", managerPoints.getAmount().longValue(), level);
        return managerPoints;
    }

    static private Money decayManagerPoints(Duration duration, Money managerPoints) {
        long horas = duration.getStandardHours();
        if (horas > HOURS_TO_DECAY) {
            // Cuántas veces tenemos que aplicar la penalización
            long penalizations = horas / HOURS_TO_DECAY;

            Logger.debug("horas: {} balance: {} level: {} penalization: {}", horas, managerPoints.getAmount(), managerLevelFromPoints(managerPoints), -PERCENT_TO_DECAY * penalizations);

            managerPoints = decayManagerPoints(managerPoints, PERCENT_TO_DECAY * penalizations);
        }
        else {
            Logger.debug("horas: {} balance: {} level: {}", horas, managerPoints.getAmount(), managerLevelFromPoints(managerPoints));
        }
        return managerPoints;
    }

    static public Money calculateEnergyBalance(ObjectId userId) {
        // La energía no lo controlaremos con las transacciones, sino por medio del valor actual y el tiempo transcurrido desde que se usó la última vez (lastUpdatedEnergy)
        User user = User.findOne(userId);
        return user.calculateEnergyBalance();
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
}
