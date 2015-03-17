package model.jobs;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mongodb.WriteResult;
import model.GlobalDate;
import model.Model;
import org.bson.types.ObjectId;
import org.jongo.FindOne;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.Date;
import java.util.List;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS,property="_class")
public class Job {
    public enum JobType {
        ENTER_CONTEST,
        CANCEL_CONTEST_ENTRY,
        CANCEL_CONTEST,
        COMPLETE_ORDER
    }

    public enum JobState {
        TODO        (0),
        PROCESSING  (1),
        DONE        (2),
        CANCELING   (3),
        CANCELED    (4);

        public final int id;

        JobState(int id) {
            this.id = id;
        }
    }

    @Id
    public ObjectId jobId;

    public JobType type;
    public JobState state;
    public Date lastModified;

    public boolean isDone() {
        return state.equals(JobState.DONE);
    }

    public boolean isCanceled() {
        return state.equals(JobState.CANCELED);
    }

    public boolean isFinished() { return isDone() || isCanceled(); }

    public void apply() {}

    public void continueProcessing() {}

    public void updateState(JobState fromState, JobState toState) {
        WriteResult result = Model.jobs().update(
                "{ _id: #, state: #}", jobId, fromState
        ).with(
                "{$set: { state: #, lastModified: #}}", toState, GlobalDate.getCurrentDate()
        );

        if (result.getN() > 0) {
            state = toState;
        }
        else {
            // Es posible que el estado ya haya sido cambiado por otro thread (p.ej. TransactionActor)
            // mientras el job.apply se estaba realizando (p.ej. ContestEntryController.addContestEntry)
            Logger.warn("updateState: {} from: {} to: {}", jobId, fromState, toState);
        }
    }

    public static void insert (JobType type, Job job) {
        job.type = type;
        job.state = JobState.TODO;
        job.lastModified = GlobalDate.getCurrentDate();
        Model.jobs().insert(job);
    }

    public static List<Job> findByStateAndLastModified(JobState state, Date dateThreshold) {
        return ListUtils.asList(Model.jobs().find(
                "{state: #, lastModified: {$lt: #}}", state, dateThreshold
        ).as(Job.class));
    }

    public static Job findOneByStateAndLastModified(JobState state, Date dateThreshold) {
        FindOne find = Model.jobs().findOne(
                "{state: #, lastModified: {$lt: #}}", state, dateThreshold
        );
        Job job = find.as(Job.class);
        if (job != null) {
            switch (job.type) {
                case ENTER_CONTEST:
                    job = find.as(EnterContestJob.class);
                    break;
                case CANCEL_CONTEST_ENTRY:
                    job = find.as(CancelContestEntryJob.class);
                    break;
                case CANCEL_CONTEST:
                    job = find.as(CancelContestJob.class);
                    break;
                case COMPLETE_ORDER:
                    job = find.as(CompleteOrderJob.class);
                    break;
            }
        }
        return job;
    }
}

