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
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ContestEntry implements JongoId {
    @Id
    public ObjectId contestEntryId;

    @JsonView(value={JsonViews.Public.class, JsonViews.AllContests.class})
    public ObjectId userId;             // Usuario que creo el equipo

    @JsonView(value={JsonViews.FullContest.class, JsonViews.MyLiveContests.class})
    public List<ObjectId> soccerIds;    // Fantasy team

    @JsonView(value={JsonViews.FullContest.class})
    public List<ObjectId> playersPurchased; // Futbolistas que han necesitado ser comprados por tener un nivel superior

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyHistoryContests.class})
    public int position = -1;

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyHistoryContests.class})
    public Money prize = MoneyUtils.zero;

    @JsonView(value={JsonViews.Extended.class, JsonViews.MyHistoryContests.class})
    public int fantasyPoints;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public ContestEntry() {}

    public ContestEntry(ObjectId userId, List<ObjectId> soccerIds) {
        this.contestEntryId = new ObjectId();
        this.userId = userId;
        this.soccerIds = soccerIds;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    public ObjectId getId() { return contestEntryId; }

    public List<InstanceSoccerPlayer> playersNotPurchased(List<InstanceSoccerPlayer> instanceSoccerPlayers) {
        return instanceSoccerPlayers.stream().filter(instanceSoccerPlayer ->
                        playersPurchased.stream().noneMatch(t -> t.equals(instanceSoccerPlayer.templateSoccerPlayerId))
        ).collect(Collectors.toList());
    }

    public void updateRanking() {
        // Logger.info("ContestEntry: {} | UserId: {} | Position: {} | FantasyPoints: {}", contestEntryId, userId, position, fantasyPoints);

        Model.contests()
            .update("{'contestEntries._id': #}", getId())
            .with("{$set: {'contestEntries.$.position': #, 'contestEntries.$.fantasyPoints': #, 'contestEntries.$.prize': #}}",
                    position, fantasyPoints, prize.toString());
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

    public static boolean update(User user, Contest contest, ContestEntry contestEntry, List<ObjectId> playerIds) {

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
                    Money price = TemplateSoccerPlayer.moneyToBuy(TemplateSoccerPlayer.levelFromSalary(instanceSoccerPlayer.salary), (int) managerLevel);
                    ProductSoccerPlayer product = new ProductSoccerPlayer(price, contest.templateContestId, instanceSoccerPlayer.templateSoccerPlayerId);
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
                    .update("{_id: #, state: \"ACTIVE\", contestEntries._id: #, contestEntries.userId: #}", contest.contestId, contestEntry.contestEntryId, user.userId)
                    .with("{$set: {contestEntries.$.soccerIds: #, contestEntries.$.playersPurchased: #}}", playerIds, playersPurchasedList);

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
