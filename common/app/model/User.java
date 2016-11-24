package model;

import com.fasterxml.jackson.annotation.JsonView;
import model.accounting.*;
import model.rewards.DailyRewards;
import model.rewards.GoldReward;
import model.rewards.Reward;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Minutes;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import play.data.validation.Constraints;
import utils.ListUtils;
import utils.MoneyUtils;
import utils.TrueSkillHelper;
import utils.ViewProjection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class User {
    public static final int MINUTES_TO_RELOAD_ENERGY = 60;
    public static final BigDecimal MAX_ENERGY = new BigDecimal(10);
    public static final long HOURS_TO_DECAY = 48;
    public static final float[] PERCENT_TO_DECAY = new float[] {
        0, 0, 0.1f, 0.25f, 0.75f, 1.5f, 3f, 4.4f
    };

    public static final long[] MANAGER_POINTS = new long[] {
      0, 50, 125, 225, 350, 600, 1100, 1850, 2850, 4350, 7350
    };

    public static final long MAX_MANAGER_LEVEL = MANAGER_POINTS.length - 1;

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

    @JsonView(JsonViews.NotForClient.class)
	public String firstName;

    @JsonView(JsonViews.NotForClient.class)
	public String lastName;

    public String nickName;
	public String email;

    public String deviceUUID;
    public String facebookID;

    @JsonView(JsonViews.NotForClient.class)
    public String facebookName;

    @JsonView(JsonViews.NotForClient.class)
    public String facebookEmail;

    public int wins;

    // True Skill
    public int trueSkill = 0;

    // Rating de True Skill
    @JsonView(JsonViews.NotForClient.class)
    public Rating rating = new Rating(TrueSkillHelper.INITIAL_MEAN, TrueSkillHelper.INITIAL_SD);

    // Torneos que se han tenido en cuenta para calcular su rating
    @JsonView(JsonViews.NotForClient.class)
    public List<ObjectId> contestsRating = new ArrayList<>();

    public Money earnedMoney = MoneyUtils.zero;

    // TODO: De momento no es realmente un "cache", siempre lo recalculamos
    @JsonView(JsonViews.Public.class)
    public Money cachedBalance;

    @JsonView(JsonViews.Public.class)
    public Money goldBalance        = Money.zero(MoneyUtils.CURRENCY_GOLD);

    @JsonView(JsonViews.Public.class)
    public Money managerBalance     = Money.zero(MoneyUtils.CURRENCY_MANAGER);

    @JsonView(JsonViews.Public.class)
    public Money energyBalance      = Money.of(MoneyUtils.CURRENCY_ENERGY, MAX_ENERGY);

    // La última fecha en la que se participó en un contest de simulación
    @JsonView(JsonViews.NotForClient.class)
    public Date lastUpdatedManager;

    // La última fecha en la que se recalculó la energía
    @JsonView(JsonViews.Public.class)
    public Date lastUpdatedEnergy;

    @JsonView(JsonViews.Public.class)
    public float managerLevel = 0;

    public List<String> achievements = new ArrayList<>();

    @JsonView(JsonViews.Public.class)
    public List<UserNotification> notifications = new ArrayList<>();

    @JsonView(JsonViews.Public.class)
    public List<ObjectId> favorites = new ArrayList<>();

    @JsonView(JsonViews.Public.class)
    public List<String> flags = new ArrayList<>();

    @JsonView(JsonViews.Public.class)
    public DailyRewards dailyRewards = new DailyRewards();

    @JsonView(JsonViews.Public.class)
    public ObjectId guildId;        // Guild al que pertenece

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
        return new UserInfo(this);
    }

    public UserInfo infoWithAchievements() {
        UserInfo info = new UserInfo(this);
        info.achievements = achievements;
        return info;
    }

    public User getProfile() {
        // TODO En la versión actual no se usa el ManagerLevel
        goldBalance = calculateGoldBalance();
        cachedBalance = goldBalance;
        //managerBalance = calculateManagerBalance();
        //energyBalance = calculateEnergyBalance();
        //earnedMoney = calculatePrizes(MoneyUtils.CURRENCY_GOLD);
        //managerLevel = managerLevelFromPoints(managerBalance);
        // Logger.debug("gold: {} manager: {} energy: {}", goldBalance, managerBalance, energyBalance);

        if (dailyRewards.update()) {
            // Actualizar el dailyRewards en la bdd
            updateDailyRewards();
        }

        return this;
    }

    public void saveManagerBalance() {
        Model.users().update(userId).with("{$set: {managerBalance: #, lastUpdatedManager: #}}", managerBalance.toString(), lastUpdatedManager);
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

    static public List<User> findAll(Class<?> projectionClass) {
        return ListUtils.asList(Model.users().find().projection(ViewProjection.get(projectionClass, User.class)).as(User.class));
    }

    static public User findByName(String username) {
        return Model.users().findOne("{nickName: #}", username).as(User.class);
    }

    static public boolean existsByName(String username) {
        return Model.users().find("{nickName: #}", username).limit(1).projection("{_id: 1}").as(User.class).iterator().hasNext();
    }

    static public List<User> findByGuild(ObjectId guildId) {
        return ListUtils.asList(Model.users().find("{guildId: #}", guildId).as(User.class));
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

    static public List<User> findByFacebook(List<String> facebookIds) {
        return ListUtils.asList(Model.findFields(Model.users(), "facebookID", facebookIds).as(User.class));
    }

    static public User findByEmail(String email) {
        return Model.users().findOne("{email: #}", email).as(User.class);
    }

    static public User findByUUID(String uuid) {
        return Model.users().findOne("{deviceUUID: #}", uuid).as(User.class);
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
            Model.users().update(userId).with("{$set: {managerBalance: #, lastUpdatedManager: #}}", balance.toString(), GlobalDate.getCurrentDate());
        }
        else if (balance.getCurrencyUnit().equals(MoneyUtils.CURRENCY_ENERGY)) {
            // No actualizaremos la ENERGíA con ninguna transacción
            Logger.error("WTF: 4401: updateBalance: {} {}", userId.toString(), balance.toString());
            // Model.users().update(userId).with("{$set: {energyBalance: #}}", balance.toString());
        }
        // Model.users().update(userId).with("{$set: {cachedBalance: #}}", balance.toString());
    }

    public void setFavorites(List<ObjectId> soccerPlayers) {
        if (soccerPlayers == null || (soccerPlayers.isEmpty() && favorites.isEmpty())) {
            Logger.warn("setFavorites: NULL or EMPTY");
            return;
        }
        favorites = soccerPlayers;
        Model.users().update(userId).with("{$set: {favorites: #}}", favorites);
    }

    public void setGuild(ObjectId newGuildId) {
        guildId = newGuildId;

        if (guildId != null) {
            Model.users().update(userId).with("{$set: {guildId: #}}", guildId);
        }
        else {
            Model.users().update(userId).with("{$unset: {guildId: ''}}");
        }
    }


    public void addFlag (String flag) {
        Model.users().update(userId).with("{$addToSet: {flags: #}}", flag);
    }

    public void removeFlag (String flag) {
        Model.users().update(userId).with("{$pull: {flags: #}}", flag);
    }

    public boolean hasFlag (String flag) {
        return Model.users().find("{_id: #, flags: {$in: [#]}}", userId, flag).as(User.class) != null;
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

    public void updateDailyRewards() {
        Model.users().update("{_id: #}", userId).with("{$set: {dailyRewards: #}}", dailyRewards);
    }

    public void claimReward() {
        Reward reward = dailyRewards.lastReward();
        if (!reward.pickedUp && (reward instanceof  GoldReward)) {
            GoldReward goldReward = (GoldReward) reward;

            // Crear un AccountingTranReward para dar la recompensa al usuario
            List<AccountOp> accounts = new ArrayList<>();
            accounts.add(new AccountOp(userId, goldReward.value, User.getSeqId(userId) + 1));
            AccountingTranReward.create(MoneyUtils.CURRENCY_GOLD.getCode(), reward.rewardId, accounts);

            // Actualizamos la fecha al momento concreto en el que se hizo y marcamos la recompensa como recogida
            dailyRewards.lastDate = GlobalDate.getCurrentDate();
            reward.pickedUp = true;

            // Actualizamos la bdd con los cambios
            Model.users()
                    .update("{_id: #, \"dailyRewards.rewards._id\": #}", userId, reward.rewardId)
                    .with("{$set: {\"dailyRewards.lastDate\": #, \"dailyRewards.rewards.$.pickedUp\": true}}", GlobalDate.getCurrentDate());

            Logger.debug("claimReward: user: {} rewardId {}", userId.toString(), reward.rewardId);
        }
        else {
            Logger.error("claimReward: user: {} rewardId: {} invalid", userId.toString(), reward.rewardId.toString());
        }
    }

    public Integer getSeqId() {
        return User.getSeqId(userId);
    }

    public Duration timeOfDecay() {
        Duration result = new Duration(0);
        if (lastUpdatedManager != null) {
            DateTime now = new DateTime(GlobalDate.getCurrentDate());

            // Obtenemos el tiempo transcurrido desde la última actualizacion del managerBalance
            DateTime dateTime = new DateTime(lastUpdatedManager);
            result = new Duration(dateTime, now);
        }
        return result;
    }

    public Money calculateBalance() {
        return User.calculateGoldBalance(userId);
    }
    public Money calculateGoldBalance() { return User.calculateGoldBalance(userId); }
    public Money calculateManagerBalance() {
        // Los managerPoints no lo controlaremos con las transacciones,
        // sino por medio del valor actual y el tiempo transcurrido desde que se participó en un torneo de simulación (lastUpdatedManager)

        // Aplicamos el decay actual al balance
        managerBalance = decayManagerPoints(timeOfDecay(), managerBalance);

        return managerBalance;
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

    public Money calculatePrizes(CurrencyUnit currenctyUnit) {
        return User.calculatePrizes(userId, currenctyUnit);
    }

    public Money calculateGoldPrizes() {
        return calculatePrizes(MoneyUtils.CURRENCY_GOLD);
    }

    public void addManagerBalance(Money managerPoints) {
        // Actualizar el managerBalance con los nuevos puntos obtenidos
        managerBalance = calculateManagerBalance().plus(managerPoints);

        // Registramos el momento actual el que se actualiza el managerLevel
        lastUpdatedManager = GlobalDate.getCurrentDate();

        saveManagerBalance();
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

    public void addEnergy(Money energy) {
        refreshEnergy();

        Money maxEnergy = Money.of(MoneyUtils.CURRENCY_ENERGY, MAX_ENERGY);
        energyBalance = energyBalance.plus(energy);
        if (energyBalance.isGreaterThan(maxEnergy)) {
            energyBalance = maxEnergy;
        }

        // Si la energía la tenemos al máximo, podemos actualizar la fecha con la de NOW
        if (energyBalance.getAmount().equals(MAX_ENERGY)) {
            lastUpdatedEnergy = GlobalDate.getCurrentDate();
        }
        else {
            // La energía ya se estaba recargando, tiene que estar correctamente actualizada por el refreshEnergy previo
        }

        saveEnergy();
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

    public boolean hasAchievement(AchievementType aAchievement) {
        return achievements.stream().anyMatch(achievement -> achievement.equals(aAchievement.toString()));
    }

    public boolean hasContestNotification(UserNotification.Topic topic, ObjectId contestId) {
        return notifications.stream().anyMatch(notification -> (notification.topic == topic) && notification.info.containsValue(contestId.toString()));
    }

    public void achievedAchievement(AchievementType achievementType) {
        if (!hasAchievement(achievementType)) {
            achievements.add(achievementType.toString());

            // Incluimos el achievement en la ficha del usuario y le enviamos una notificación
            Model.users().update("{_id: #}", userId).with("{$addToSet: {achievements: #, notifications: #}}", achievementType.toString(), UserNotification.achievementEarned(achievementType));
        }
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
                .aggregate("{$match: { \"accountOps.accountId\": #, currencyCode: #, type: #, state: \"VALID\"}}", userId, currencyUnit.toString(), AccountingTran.TransactionType.PRIZE)
                .and("{$unwind: \"$accountOps\"}")
                .and("{$match: {\"accountOps.accountId\": #}}", userId)
                .and("{$project: {value: \"$accountOps.value\"}}")
                .as(PrizeOp.class);

        Money balance = MoneyUtils.zero(currencyUnit.getCode());
        if (!prizeOps.isEmpty()) {
            for (PrizeOp op : prizeOps) {
                balance = balance.plus(op.value);
            }
        }
        return balance;
    }

    // Para registrar las operaciones
    static public class BalanceOp {
        public Money value;
    }

    static public Money calculateBalance(ObjectId userId, String currencyUnit) {

        List<BalanceOp> accountingOps = Model.accountingTransactions()
                .aggregate("{$match: { \"accountOps.accountId\": #, currencyCode: #, \"accountOps.value\": {$ne: #}, state: \"VALID\"}}", userId, currencyUnit, currencyUnit.concat(" 0.00"))
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
        return User.findOne(userId).calculateManagerBalance();
    }

    static private Money pointsFromManagerLevel(float managerLevel) {
        int level = (int) managerLevel;
        int acc = 0;
        if (level < MAX_MANAGER_LEVEL) {
            float pointsToLevelUp = (float) (MANAGER_POINTS[level+1] - MANAGER_POINTS[level]);
            float remainder = managerLevel - level;
            acc = (int) (remainder * pointsToLevelUp);
        }
        return Money.zero(MoneyUtils.CURRENCY_MANAGER).plus(MANAGER_POINTS[level] + acc);
    }

    static public float managerLevelFromPoints(Money managerPoints) {
        long points = managerPoints.getAmount().longValue();
        int level = 0;
        while (level+1 < MANAGER_POINTS.length && points >= MANAGER_POINTS[level+1]) {
            level++;
        }

        float acc = 0;
        if (level < MAX_MANAGER_LEVEL) {
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
        return managerPoints;
    }

    static private Money decayManagerPoints(Duration duration, Money managerPoints) {
        long horas = duration.getStandardHours();
        if (horas > HOURS_TO_DECAY) {
            // Cuántas veces tenemos que aplicar la penalización
            long days = horas / 24;
            if (days >= PERCENT_TO_DECAY.length) {
                days = PERCENT_TO_DECAY.length - 1;
            }
            float percentToDecay = PERCENT_TO_DECAY[(int)days];

            Money managerPointsCopy = Money.of(managerPoints);

            managerPoints = decayManagerPoints(managerPoints, percentToDecay);

            /*
            Logger.debug("decay: manager: {} level: {} [horas: {} balance: {} level: {} penalization: {}]",
                    managerPoints.getAmount().longValue(), managerLevelFromPoints(managerPoints),
                    horas, managerPointsCopy.getAmount(), managerLevelFromPoints(managerPointsCopy), -percentToDecay);
            */

        }
        else {
            // Logger.debug("horas: {} balance: {} level: {}", horas, managerPoints.getAmount(), managerLevelFromPoints(managerPoints));
        }
        return managerPoints;
    }

    static public Money calculateEnergyBalance(ObjectId userId) {
        // La energía no lo controlaremos con las transacciones, sino por medio del valor actual y el tiempo transcurrido desde que se usó la última vez (lastUpdatedEnergy)
        User user = User.findOne(userId);
        return user.calculateEnergyBalance();
    }

    static public Money getBalance(ObjectId userId, CurrencyUnit currencyUnit) {
        Money balance;
        if (currencyUnit.equals(MoneyUtils.CURRENCY_GOLD)) {
            balance = calculateGoldBalance(userId);
        }
        else if (currencyUnit.equals(MoneyUtils.CURRENCY_MANAGER)) {
            balance = calculateManagerBalance(userId);
        }
        else if (currencyUnit.equals(MoneyUtils.CURRENCY_ENERGY)) {
            balance = calculateEnergyBalance(userId);
        }
        else {
            // El usuario no tendrá dinero, si la moneda es diferente
            Logger.error("User not has Money: {}", currencyUnit.toString());
            balance = Money.zero(currencyUnit);
        }
        return balance;
    }

    static public boolean hasMoney(ObjectId userId, Money money) {
        Money balance = getBalance(userId, money.getCurrencyUnit());
        return MoneyUtils.compareTo(balance, money) >= 0;
    }

    static public Money moneyToBuy(Contest contest, Money managerBalance, List<InstanceSoccerPlayer> instanceSoccerPlayers) {
        Money price = Money.zero(MoneyUtils.CURRENCY_GOLD);

        float managerLevel = managerLevelFromPoints(managerBalance);

        for (InstanceSoccerPlayer instanceSoccerPlayer : instanceSoccerPlayers) {
            int soccerPlayerLevel = TemplateSoccerPlayer.levelFromSalary(instanceSoccerPlayer.salary);
            price = price.plus(TemplateSoccerPlayer.moneyToBuy(contest, soccerPlayerLevel, (int) managerLevel));
        }

        return price;
    }

    static public List<InstanceSoccerPlayer> playersToBuy(Money managerBalance, List<InstanceSoccerPlayer> instanceSoccerPlayers) {
        float managerLevel = managerLevelFromPoints(managerBalance);

        return instanceSoccerPlayers.stream().filter( instanceSoccerPlayer ->
                TemplateSoccerPlayer.levelFromSalary(instanceSoccerPlayer.salary) > (int) managerLevel
        ).collect(Collectors.toList());
    }
}
