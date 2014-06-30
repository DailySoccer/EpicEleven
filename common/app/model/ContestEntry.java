package model;

import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import play.Logger;
import utils.ListUtils;

import java.util.ArrayList;
import java.util.List;

public class ContestEntry {
    @Id
    public ObjectId contestEntryId;

    public ObjectId userId;     // Usuario que creo el equipo

    public ObjectId contestId;  // Contest en el que se ha inscrito el equipo

    public List<ObjectId> soccerIds;

    public ContestEntry(ObjectId userId, ObjectId contestId, List<ObjectId> soccerIds) {
        this.userId = userId;
        this.contestId = contestId;
        this.soccerIds = soccerIds;
    }

    // Constructor por defecto (necesario para Jongo: "unmarshall result to class")
    public ContestEntry() {}

    static public ContestEntry find(ObjectId contestEntryId) {
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
     * Creacion de un contest entry (se añade a la base de datos)
     * @param userId        Usuario al que pertenece el equipo
     * @param contestId     Contest al que se apunta
     * @param optaIdsList   Lista de identificadores de los futbolistas de Opta
     * @return Si se ha realizado correctamente su creacion
     */
    public static boolean createFromOptaIds(String userId, String contestId, List<String> optaIdsList) {
        Logger.info("create: userId({}) contestId({}) soccerTeam({})", userId, contestId, optaIdsList);

        // Obtener el userId : ObjectId
        User aUser = User.find(userId);
        if (aUser == null) {
            return false;
        }

        // Obtener el contestId : ObjectId
        Contest aContest = Contest.find(contestId);
        if (aContest == null) {
            return false;
        }

        // Obtener los soccerIds de los futbolistas : List<ObjectId>
        List<ObjectId> soccerIds = new ArrayList<>();

        Iterable<TemplateSoccerPlayer> soccers = Model.findFields(Model.templateSoccerPlayers(), "optaPlayerId", optaIdsList).as(TemplateSoccerPlayer.class);

        String soccerNames = "";    // Requerido para Logger.info
        for (TemplateSoccerPlayer soccer : soccers) {
            soccerNames += soccer.name + " / ";
            soccerIds.add(soccer.templateSoccerPlayerId);
        }

        Logger.info("contestEntry: Contest[{}] / User[{}] = ({}) => {}", aContest.name, aUser.nickName, soccerIds.size(), soccerNames);

        // Crear el equipo en mongoDb.contestEntryCollection
        return create(new ObjectId(userId), new ObjectId(contestId), soccerIds);
    }

    /**
     * Creacion de un contest entry (se añade a la base de datos)
     * @param user      Usuario al que pertenece el equipo
     * @param contestId   Contest al que se apunta
     * @param soccers   Lista de futbolistas con la que se apunta
     * @return Si se ha realizado correctamente su creacion
     */
    public static boolean create(ObjectId user, ObjectId contestId, List<ObjectId> soccers) {
        boolean bRet = true;

        try {
            Contest contest = Model.contests().findOne("{ _id: # }", contestId).as(Contest.class);
            if (contest != null) {
                ContestEntry aContestEntry = new ContestEntry(user, contestId, soccers);
                Model.contestEntries().withWriteConcern(WriteConcern.SAFE).insert(aContestEntry);

                if (!contest.currentUserIds.contains(contest.contestId)) {
                    contest.currentUserIds.add(contest.contestId);
                    Model.contests().update(contest.contestId).with(contest);
                }
            }
            else {
                bRet = false;
            }



        } catch (MongoException exc) {
            Logger.error("create: ", exc);
            bRet = false;
        }

        return bRet;
    }

    public static List<SoccerPlayer> getSoccerPlayers(String contestEntryId) {
        ContestEntry contestEntry = find(new ObjectId(contestEntryId));
        Contest contest = Contest.find(contestEntry.contestId);
        TemplateContest templateContest = TemplateContest.find(contest.templateContestId);
        List<ObjectId> templateMatchEventIds = templateContest.templateMatchEventIds;

        //Iterable<LiveMatchEvent> liveMatchEventsResults = Model.liveMatchEvents().find().as(LiveMatchEvent.class);
        Iterable<LiveMatchEvent> liveMatchEventsResults = Model.findObjectIds(Model.liveMatchEvents(), "templateMatchEventId", templateMatchEventIds).as(LiveMatchEvent.class);
        List<LiveMatchEvent> liveMatchEventList = ListUtils.asList(liveMatchEventsResults);

        List<SoccerPlayer> soccerPlayers = new ArrayList<>();
        for (ObjectId soccerId : contestEntry.soccerIds) {
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
}
