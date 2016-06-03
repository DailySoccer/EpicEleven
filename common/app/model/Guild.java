package model;

import com.fasterxml.jackson.annotation.JsonView;
import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.util.*;

public class Guild {

    public enum UserRol {
        ADMIN,
        MEMBER
    }

    @Id
    public ObjectId guildId;

    public String name;

    public ObjectId authorId;           // Creador del Guild

    public Map<String, UserRol> roles = new HashMap<>();

    @JsonView(JsonViews.NotForClient.class)
    public List<ObjectId> requests = new ArrayList<>();

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public ObjectId getId() { return guildId; }

    private Guild() {}

    public Guild(ObjectId authorId, String name) {
        this.authorId = authorId;
        this.name = name;
        this.createdAt = GlobalDate.getCurrentDate();

        this.roles.put(authorId.toString(), UserRol.ADMIN);
    }

    static public List<Guild> findAll() {
        return ListUtils.asList(Model.guilds().find().as(Guild.class));
    }

    static public Guild findOne(ObjectId guildId) {
        return Model.guilds().findOne("{_id : #}", guildId).as(Guild.class);
    }

    static public Guild findOne(String guildId) {
        return findOne(new ObjectId(guildId));
    }

    public void insert() {
        Model.guilds().withWriteConcern(WriteConcern.SAFE).insert(this);
    }

    public void request(ObjectId newMemberId) {
        requests.add(newMemberId);
        Model.guilds().update(guildId).with("{$addToSet: {requests: #}}", newMemberId);
    }

    public void acceptRequest(User newMember) {
        // Cambiamos el Guild
        newMember.setGuild(guildId);

        // Quitamos la solicitud
        removeRequest(newMember.userId);
    }

    public void rejectRequest(User newMember) {
        // Quitamos la solicitud
        removeRequest(newMember.userId);
    }

    private void removeRequest(ObjectId userId) {
        Model.guilds().update(guildId).with("{$pull: {requests: #}}", userId);
    }

    public void removeMember(User member) {
        // TODO: ¿si es el único member del guild? ¿el administrador?
        member.setGuild(null);
    }

    public boolean hasRequested (ObjectId memberId) {
        return requests.contains(memberId);
    }

    public boolean hasRol (ObjectId memberId, UserRol rol) {
        return roles.containsKey(memberId.toString()) && roles.get(memberId.toString()).equals(rol);
    }
}
