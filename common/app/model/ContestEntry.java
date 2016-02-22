package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.ImmutableList;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;
import model.accounting.AccountOp;
import model.accounting.AccountingTranOrder;
import model.shop.Order;
import model.shop.Product;
import model.shop.ProductSoccerPlayer;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;
import utils.MoneyUtils;

import java.util.*;
import java.util.stream.Collectors;

public class ContestEntry implements JongoId {
    public static final List<String> FORMATIONS = ImmutableList.of( "442", "352", "433", "343", "451" );
    public static final String FORMATION_DEFAULT = FORMATIONS.get(0);

    static public List<Money> SUBSTITUTIONS_PRICES = ImmutableList.<Money>builder()
            .add(Money.of(MoneyUtils.CURRENCY_GOLD, 0))
            .add(Money.of(MoneyUtils.CURRENCY_GOLD, 3))
            .add(Money.of(MoneyUtils.CURRENCY_GOLD, 6))
            .build();

    @Id
    public ObjectId contestEntryId;

    @JsonView(value={JsonViews.Public.class, JsonViews.AllContests.class})
    public ObjectId userId;             // Usuario que creo el equipo

    @JsonView(value={JsonViews.FullContest.class, JsonViews.MyLiveContests.class})
    public String formation;    // Formación del fantasyTeam creado

    @JsonView(value={JsonViews.FullContest.class, JsonViews.MyLiveContests.class})
    public List<ObjectId> soccerIds;    // Fantasy team

    @JsonView(value={JsonViews.FullContest.class})
    public List<ObjectId> playersPurchased; // Futbolistas que han necesitado ser comprados por tener un nivel superior

    @JsonView(value={JsonViews.FullContest.class})
    public List<Substitution> substitutions; // Cambio de la alineacion de futbolistas en "Live"

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyHistoryContests.class})
    public int position = -1;

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyHistoryContests.class})
    public Money prize = MoneyUtils.zero;

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyHistoryContests.class})
    public int fantasyPoints;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public ContestEntry() {}

    public ContestEntry(ObjectId userId, String formation, List<ObjectId> soccerIds) {
        this.contestEntryId = new ObjectId();
        this.userId = userId;
        this.formation = formation;
        this.soccerIds = soccerIds;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() { return contestEntryId; }

    public boolean playerPurchased(InstanceSoccerPlayer instanceSoccerPlayer) {
        return playersPurchased.stream().anyMatch(t -> t.equals(instanceSoccerPlayer.templateSoccerPlayerId));
    }

    public List<InstanceSoccerPlayer> playersNotPurchased(List<InstanceSoccerPlayer> instanceSoccerPlayers) {
        return instanceSoccerPlayers.stream().filter(instanceSoccerPlayer ->
                        playersPurchased.stream().noneMatch(t -> t.equals(instanceSoccerPlayer.templateSoccerPlayerId))
        ).collect(Collectors.toList());
    }

    public Money changePrice() {
        int pricesIndex = (substitutions != null ? substitutions.size() : 0);
        if (pricesIndex >= SUBSTITUTIONS_PRICES.size() || pricesIndex < 0) {
            Logger.error("WTF - 3537: SoccerPlayer changes is requesting unknown change-price: index={}, MAX_CHANGES={}", pricesIndex, SUBSTITUTIONS_PRICES.size());
            return Money.zero(MoneyUtils.CURRENCY_GOLD);
        }
        return SUBSTITUTIONS_PRICES.get(pricesIndex);
    }

    public void updateRanking() {
        // Logger.info("ContestEntry: {} | UserId: {} | Position: {} | FantasyPoints: {}", contestEntryId, userId, position, fantasyPoints);

        Model.contests()
            .update("{'contestEntries._id': #}", getId())
            .with("{$set: {'contestEntries.$.position': #, 'contestEntries.$.fantasyPoints': #, 'contestEntries.$.prize': #}}",
                    position, fantasyPoints, prize.toString());
    }

    public boolean containsSoccerPlayer(ObjectId soccerPlayerId) {
        return soccerIds.indexOf(soccerPlayerId) != -1;
    }

    public Money changeSoccerPlayer(ObjectId oldSoccerPlayerId, ObjectId newSoccerPlayerId) {
        Money moneyNeeded = changePrice();

        int index = soccerIds.indexOf(oldSoccerPlayerId);
        if (index != -1) {
            if (substitutions == null) {
                substitutions = new ArrayList<>();
            }

            substitutions.add(new Substitution(oldSoccerPlayerId, newSoccerPlayerId));
            soccerIds.set(index, newSoccerPlayerId);
        }

        return moneyNeeded;
    }

    static public ContestEntry findOne(String contestEntryId) {
        ContestEntry aContestEntry = null;
        if (ObjectId.isValid(contestEntryId)) {
            aContestEntry = findOne(new ObjectId(contestEntryId));
        }
        return aContestEntry;
    }

    static public ContestEntry findOne(ObjectId contestEntryId) {
        ContestEntry contestEntry = null;

        Contest contest = Contest.findOneFromContestEntry(contestEntryId);
        if (contest != null) {
            for (ContestEntry entry : contest.contestEntries) {
                if (entry.contestEntryId.equals(contestEntryId)) {
                    contestEntry = entry;
                    break;
                }
            }
        }

        return contestEntry;
    }

    static public List<ContestEntry> findAllFromContests() {
        List<ContestEntry> contestEntries = new ArrayList<>();

        List<Contest> contests = ListUtils.asList(Model.contests().find().as(Contest.class));
        for (Contest contest : contests) {
            contestEntries.addAll(contest.contestEntries);
        }

        return contestEntries;
    }

    public static boolean update(User user, Contest contest, ContestEntry contestEntry, String formation, List<ObjectId> playerIds) {

        boolean bRet = false;

        List<Product> productSoccerPlayers = new ArrayList<>();
        List<ObjectId> playersPurchasedList = contestEntry.playersPurchased;

        // Si el contest es de pago con GOLD, entonces los futbolistas de nivel superior se compran y hay que crear una transacción
        if (contest.entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
            Money managerBalance = User.calculateManagerBalance(user.userId);

            List<InstanceSoccerPlayer> soccerPlayers = contest.getInstanceSoccerPlayers(playerIds);
            List<InstanceSoccerPlayer> playersToBuy = contestEntry.playersNotPurchased(User.playersToBuy(managerBalance, soccerPlayers));
            if (!playersToBuy.isEmpty()) {

                // Actualizar la lista de futbolistas comprados
                if (playersPurchasedList == null) {
                    playersPurchasedList = new ArrayList<>();
                }

                playersPurchasedList.addAll(playersToBuy.stream().map(instanceSoccerPlayer ->
                                instanceSoccerPlayer.templateSoccerPlayerId
                ).collect(Collectors.toList()));

                float managerLevel = User.managerLevelFromPoints(managerBalance);

                for (InstanceSoccerPlayer instanceSoccerPlayer : playersToBuy) {
                    Money price = TemplateSoccerPlayer.moneyToBuy(contest, TemplateSoccerPlayer.levelFromSalary(instanceSoccerPlayer.salary), (int) managerLevel);
                    ProductSoccerPlayer product = new ProductSoccerPlayer(price, contest.contestId, instanceSoccerPlayer.templateSoccerPlayerId);
                    productSoccerPlayers.add(product);
                }
            }
        }

        try {
            if (!productSoccerPlayers.isEmpty()) {
                // Crear el identificador del nuevo pedido
                ObjectId orderId = new ObjectId();

                Order order = Order.create(
                        orderId,
                        user.userId,
                        Order.TransactionType.IN_GAME,
                        "payment_ID_generic",
                        productSoccerPlayers,
                        "epiceleven.com");

                // Crear la transacción
                AccountingTranOrder.create(MoneyUtils.CURRENCY_GOLD.toString(), orderId, "payment_ID_generic", ImmutableList.of(
                        new AccountOp(user.userId, order.price().negated(), User.getSeqId(user.userId) + 1)
                ));

                order.setCompleted();
            }

            WriteResult result = Model.contests()
                    .update("{_id: #, state: #, contestEntries._id: #, contestEntries.userId: #}", contest.contestId, "ACTIVE", contestEntry.contestEntryId, user.userId)
                    .with("{$set: {contestEntries.$.soccerIds: #, contestEntries.$.playersPurchased: #}}", playerIds, playersPurchasedList);

            // Comprobamos el número de documentos afectados (error == 0)
            bRet = (result.getN() > 0);
        }
        catch (MongoException exc) {
            Logger.error("WTF 3032: ", exc);
        }

        return bRet;
    }

    public static boolean change(User user, Contest contest, ObjectId contestEntryId, ObjectId oldSoccerPlayerId, ObjectId newSoccerPlayerId) {

        boolean bRet = false;

        ContestEntry contestEntry = ContestEntry.findOne(contestEntryId);
        if (contestEntry == null) {
            Logger.error("WTF 3039: ContestEntryId: {}", contestEntryId.toString());
            return bRet;
        }

        List<Product> productSoccerPlayers = new ArrayList<>();
        List<ObjectId> playersPurchasedList = contestEntry.playersPurchased;

        // Si el contest es de pago con GOLD, entonces los futbolistas de nivel superior se compran y hay que crear una transacción
        if (contest.entryFee.getCurrencyUnit().equals(MoneyUtils.CURRENCY_GOLD)) {
            InstanceSoccerPlayer instanceSoccerPlayer = contest.getInstanceSoccerPlayer(newSoccerPlayerId);

            Money managerBalance = User.calculateManagerBalance(user.userId);
            float managerLevel = User.managerLevelFromPoints(managerBalance);

            Money price = TemplateSoccerPlayer.moneyToBuy(contest, TemplateSoccerPlayer.levelFromSalary(instanceSoccerPlayer.salary), (int) managerLevel);
            if (price.isPositive()) {
                // Actualizar la lista de futbolistas comprados
                if (playersPurchasedList == null) {
                    playersPurchasedList = new ArrayList<>();
                }

                playersPurchasedList.add(instanceSoccerPlayer.templateSoccerPlayerId);
            }

            if (contestEntry.containsSoccerPlayer(oldSoccerPlayerId)) {
                price = price.plus(contestEntry.changeSoccerPlayer(oldSoccerPlayerId, newSoccerPlayerId));
            }

            if (price.isPositive()) {
                ProductSoccerPlayer product = new ProductSoccerPlayer(price, contest.contestId, instanceSoccerPlayer.templateSoccerPlayerId);
                productSoccerPlayers.add(product);
            }
        }

        try {
            if (!productSoccerPlayers.isEmpty()) {
                // Crear el identificador del nuevo pedido
                ObjectId orderId = new ObjectId();

                Order order = Order.create(
                        orderId,
                        user.userId,
                        Order.TransactionType.IN_GAME,
                        "payment_ID_generic",
                        productSoccerPlayers,
                        "epiceleven.com");

                // Crear la transacción
                AccountingTranOrder.create(MoneyUtils.CURRENCY_GOLD.toString(), orderId, "payment_ID_generic", ImmutableList.of(
                        new AccountOp(user.userId, order.price().negated(), User.getSeqId(user.userId) + 1)
                ));

                order.setCompleted();
            }

            WriteResult result = Model.contests()
                    .update("{_id: #, state: #, contestEntries._id: #, contestEntries.userId: #}", contest.contestId, "LIVE", contestEntry.contestEntryId, user.userId)
                    .with("{$set: {contestEntries.$.soccerIds: #, contestEntries.$.playersPurchased: #, contestEntries.$.substitutions: #}}", contestEntry.soccerIds, playersPurchasedList, contestEntry.substitutions);

            // Comprobamos el número de documentos afectados (error == 0)
            bRet = (result.getN() > 0);
        }
        catch (MongoException exc) {
            Logger.error("WTF 3032: ", exc);
        }

        return bRet;
    }

    public int getFantasyPointsFromMatchEvents(List<TemplateMatchEvent> templateMatchEvents) {
        int fantasyPoints = 0;
        for (ObjectId templateSoccerPlayerId : soccerIds) {
            for (TemplateMatchEvent templateMatchEvent : templateMatchEvents) {
                if (templateMatchEvent.containsTemplateSoccerPlayer(templateSoccerPlayerId)) {
                    fantasyPoints += templateMatchEvent.getSoccerPlayerFantasyPoints(templateSoccerPlayerId);
                    break;
                }
            }
        }
        return fantasyPoints;
    }
}
