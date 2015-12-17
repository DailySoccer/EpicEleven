package model.jobs;

import com.google.common.collect.ImmutableList;
import model.bonus.AddFundsBonus;
import model.shop.Order;
import model.User;
import model.accounting.AccountOp;
import model.accounting.AccountingTran;
import model.accounting.AccountingTranBonus;
import model.accounting.AccountingTranOrder;
import org.bson.types.ObjectId;
import org.joda.money.Money;
import utils.MoneyUtils;

public class CompleteOrderJob extends Job {
    ObjectId orderId;

    public CompleteOrderJob() {}

    private CompleteOrderJob(ObjectId orderId) {
        this.orderId = orderId;
    }

    @Override
    public void apply() {
        if (state.equals(Job.JobState.TODO)) {
            updateState(Job.JobState.TODO, Job.JobState.PROCESSING);
        }

        if (state.equals(Job.JobState.PROCESSING)) {

            Order order = Order.findOne(orderId.toString());
            Money orderPrice = order.price();

            int seqId = User.getSeqId(order.userId);

            // Registrar el GASTO
            AccountingTranOrder.create(orderPrice.getCurrencyUnit().getCode(), orderId, order.paymentId, ImmutableList.of(
                    new AccountOp(order.userId, orderPrice, seqId + 1)
            ));

            // Registrar la GANANCIA
            Money gained = order.gained();
            if (!gained.isZero() && (MoneyUtils.isGold(gained) || MoneyUtils.isManager(gained))) {
                AccountingTranOrder.create(gained.getCurrencyUnit().getCode(), orderId, order.paymentId, ImmutableList.of(
                        new AccountOp(order.userId, gained, seqId + 2)
                ));
            }

            order.setCompleted();

            updateState(Job.JobState.PROCESSING, Job.JobState.DONE);
        }
    }

    @Override
    public void continueProcessing() {
        apply();
    }

    public static CompleteOrderJob create(ObjectId orderId) {
        CompleteOrderJob job = new CompleteOrderJob(orderId);
        insert(JobType.COMPLETE_ORDER, job);
        job.apply();
        return job;
    }
}
