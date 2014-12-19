package model.jobs;

import com.mongodb.WriteResult;
import model.Model;
import org.bson.types.ObjectId;
import org.jongo.FindOne;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;

public class Job {
    public enum JobType {
        ENTER_CONTEST,
        CANCEL_CONTEST_ENTRY
    }

    public enum JobState {
        TODO        (0),
        PROCESSING  (1),
        DONE        (2),
        CANCELED    (3);

        public final int id;

        JobState(int id) {
            this.id = id;
        }
    }

    @Id
    public ObjectId transactionId;

    public JobType type;
    public JobState state;
    public long lastModified;

    public boolean isDone() {
        return state.equals(JobState.DONE);
    }

    public boolean isCanceled() {
        return state.equals(JobState.CANCELED);
    }

    public void apply() {
    }

    public void updateState(JobState fromState, JobState toState) {
        WriteResult result = Model.jobs().update(
                "{ _id: #, state: #}", transactionId, fromState
        ).with(
                "{$set: { state: #, lastModified: #}}", toState, System.currentTimeMillis()
        );

        if (result.getN() > 0) {
            state = toState;
        }
        else {
            Logger.error("WTF 7209: updateState: {} from: {} to: {}", transactionId, fromState, toState);
        }
    }

    public static void insert (JobType type, Job job) {
        job.type = type;
        job.state = JobState.TODO;
        job.lastModified = System.currentTimeMillis();
        Model.jobs().insert(job);
    }

    public static Job findJobByStateAndLastModified(JobState state, long dateThreshold) {
        FindOne find = Model.jobs().findOne(
                "{state: #, lastModified: {$lt: #}}", state, dateThreshold
        );
        Job job = find.as(Job.class);
        switch (job.type) {
            case ENTER_CONTEST: job = find.as(EnterContestJob.class); break;
            case CANCEL_CONTEST_ENTRY: job = find.as(CancelContestEntryJob.class); break;
        }
        return job;
    }
}

