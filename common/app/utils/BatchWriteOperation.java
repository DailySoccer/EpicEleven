package utils;

import com.mongodb.BulkWriteOperation;

public class BatchWriteOperation {
    public BatchWriteOperation(BulkWriteOperation bulkOperation) {
        this.bulkOperation = bulkOperation;
    }

    public int getNumOperations() {
        return numOperations;
    }

    public void insert(com.mongodb.DBObject document) {
        numOperations++;
        bulkOperation.insert(document);
    }

    public com.mongodb.BulkWriteRequestBuilder find(com.mongodb.DBObject query) {
        numOperations++;
        return bulkOperation.find(query);
    }

    public void execute() {
        if (numOperations > 0) {
            bulkOperation.execute();
        }
    }

    public void execute(com.mongodb.WriteConcern writeConcern) {
        if (numOperations > 0) {
            bulkOperation.execute(writeConcern);
        }
    }

    private int numOperations = 0;
    private BulkWriteOperation bulkOperation;
}
