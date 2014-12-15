package model.transactions;

import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class TransactionChange {
    public List<AccountOp> accounts = new ArrayList<>();

    public TransactionChange() {}
}
