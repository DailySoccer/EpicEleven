package actors;

import akka.actor.UntypedActor;
import model.GlobalDate;
import model.Model;
import model.TemplateContest;
import model.transactions.AccountOp;
import model.transactions.PrizeChange;
import model.transactions.Transaction;
import model.transactions.*;
import play.Logger;
import scala.concurrent.duration.Duration;
import utils.ListUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class TransactionsActor extends UntypedActor {
    public void onReceive(Object msg) {

        switch ((String)msg) {

            case "Tick":
                onTick();
                getContext().system().scheduler().scheduleOnce(Duration.create(30, TimeUnit.MINUTES), getSelf(),
                        "Tick", getContext().dispatcher(), null);
                break;

            // En el caso del SimulatorTick no tenemos que reeschedulear el mensaje porque es el Simulator el que se
            // encarga de drivearnos.
            case "SimulatorTick":
                onTick();
                break;

            default:
                unhandled(msg);
                break;
        }
    }

    private void onTick() {
        Logger.info("Transactions: {}", GlobalDate.getCurrentDateString());

        List<Transaction> transactions = ListUtils.asList(Model.transactions().find("{proc: #, state: #}",
                Transaction.TransactionProc.UNCOMMITTED, Transaction.TransactionState.VALID).as(Transaction.class));

        for (Transaction transaction: transactions) {
            boolean valid = true;
            for (AccountOp accountOp: transaction.changes.accounts) {
                if (!accountOp.canCommit()) {
                    valid = false;
                    break;
                }
                accountOp.updateBalance();
            }

            if (valid) {
                transaction.commit();
            }
        }
    }}
