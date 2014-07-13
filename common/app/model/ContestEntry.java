package model;

import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ContestEntry {
    @Id
    public ObjectId contestEntryId;
    public ObjectId userId;             // Usuario que creo el equipo
    public ObjectId contestId;          // Contest en el que se ha inscrito el usuario
    public List<ObjectId> soccerIds;    // Fantasy team

    public Date createdAt;

    public ContestEntry() {}

    public ContestEntry(ObjectId userId, ObjectId contestId, List<ObjectId> soccerIds) {
        this.userId = userId;
        this.contestId = contestId;
        this.soccerIds = soccerIds;
        this.createdAt = GlobalDate.getCurrentDate();
    }

    static public ContestEntry findOne(ObjectId contestEntryId) {
        return Model.contestEntries().findOne("{_id : #}", contestEntryId).as(ContestEntry.class);
    }

    /**
     *  Eliminar un contest entry y sus dependencias
     */
    public static boolean remove(ContestEntry contestEntry) {
        Logger.info("remove ContestEntry ({})", contestEntry.contestEntryId);
        Model.contestEntries().remove(contestEntry.contestEntryId);

        return true;
    }

    /**
     * Obtener la lista de Soccer Players incluidos en un Contest Entry
     * @return lista de Soccer Players
     */
    public List<SoccerPlayer> getSoccerPlayers() {
        Contest contest = Contest.findOne(contestId);
        TemplateContest templateContest = TemplateContest.findOne(contest.templateContestId);
        List<ObjectId> templateMatchEventIds = templateContest.templateMatchEventIds;

        //Iterable<LiveMatchEvent> liveMatchEventsResults = Model.liveMatchEvents().find().as(LiveMatchEvent.class);
        Iterable<LiveMatchEvent> liveMatchEventsResults = Model.findObjectIds(Model.liveMatchEvents(), "templateMatchEventId", templateMatchEventIds).as(LiveMatchEvent.class);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.asList(liveMatchEventsResults);

        List<SoccerPlayer> soccerPlayers = new ArrayList<>();
        for (ObjectId soccerId : soccerIds) {
            for (LiveMatchEvent liveMatchEvent : liveMatchEventList) {
                SoccerPlayer liveSoccer = liveMatchEvent.findSoccerPlayer(soccerId);
                if (liveSoccer != null) {
                    soccerPlayers.add(liveSoccer);
                    break;
                }
            }
        }
        return soccerPlayers;
    }


    /**
     * Creacion de un contest entry (se añade a la base de datos)
     * @param user      Usuario al que pertenece el equipo
     * @param contestId   Contest al que se apunta
     * @param soccers   Lista de futbolistas con la que se apunta
     * @return Si se ha realizado correctamente su creacion
     */
    public static boolean create(ObjectId user, ObjectId contestId, List<ObjectId> soccers) {

        boolean bRet = false;

        try {
            Contest contest = Model.contests().findOne("{ _id: # }", contestId).as(Contest.class);

            if (contest != null) {
                ContestEntry aContestEntry = new ContestEntry(user, contestId, soccers);
                Model.contestEntries().withWriteConcern(WriteConcern.SAFE).insert(aContestEntry);

                if (!contest.currentUserIds.contains(contest.contestId)) {
                    contest.currentUserIds.add(contest.contestId);
                    Model.contests().update(contest.contestId).with(contest);
                }

                bRet = true;
            }
        }
        catch (MongoException exc) {
            Logger.error("WTF 2032: ", exc);
        }

        return bRet;
    }
}
