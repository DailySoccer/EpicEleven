package model.jobs;

import com.google.common.collect.ImmutableList;
import model.bonus.AddFundsBonus;
import model.bonus.Bonus;
import model.Order;
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

            AccountingTranOrder.create(orderId, order.paymentId, ImmutableList.of(
                    new AccountOp(order.userId, order.product.price, User.getSeqId(order.userId) + 1)
            ));

            // Existe un bonus por añadir dinero?
            Money bonus = AddFundsBonus.getMoney(order.product.price);
            if (bonus != null) {
                AccountingTranBonus.create(AccountingTran.TransactionType.BONUS, orderId.toString(), bonus, ImmutableList.of(
                        new AccountOp(order.userId, MoneyUtils.zero, User.getSeqId(order.userId) + 1)
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
