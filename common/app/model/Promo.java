package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.util.Date;
import java.util.List;

public class Promo implements JongoId {
    @Id
    @JsonView(JsonViews.NotForClient.class)
    public ObjectId promoId;

    public int priority;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    @JsonView(JsonViews.NotForClient.class)
    public Date activationDate;

    @JsonView(JsonViews.NotForClient.class)
    public Date deactivationDate;

    public String codeName;
    public String url;
    public String imageXs;
    public String imageDesktop;
    public String html;


    public Promo() {}

    public Promo(Date activationDate, Date deactivationDate, int priority, String codeName, String url, String html, String imageXs, String imageDesktop) {
        this.priority = priority;
        this.createdAt = GlobalDate.getCurrentDate();
        this.activationDate = activationDate;
        this.deactivationDate = deactivationDate;
        this.codeName = codeName;
        this.url = url;
        this.html = html;
        this.imageDesktop = imageDesktop;
        this.imageXs = imageXs;
    }

    public ObjectId getId() {
        return promoId;
    }

    public static Promo findOne(ObjectId promoId) {
        return Model.promos().findOne("{_id: #}", promoId).as(Promo.class);
    }

    public static List<Promo> findByCodeName(String codeName) {
        return ListUtils.asList(Model.promos().find("{codeName: #}", codeName).as(Promo.class));
    }

    public static List<Promo> getCurrent() {
        return ListUtils.asList(Model.promos()
                .aggregate("{$match: {activationDate: {$lte: #},  deactivationDate: {$gt: #}}} ", GlobalDate.getCurrentDate(), GlobalDate.getCurrentDate())
                .and("{$sort: {priority: -1}}")
                .as(Promo.class));
    }



    public static boolean edit(ObjectId promoId, Promo promo) {
        Model.promos().update("{_id: #}", promoId).with(promo);
        return true;
    }

    public static boolean delete(ObjectId promoId) {
        Model.promos().remove("{_id: #}", promoId);
        return true;
    }

    /**
     * Creacion de una promo
     */
    public static boolean createPromo(Promo promo) {
        Model.promos().insert(promo);

        OpsLog.onNew(promo);
        return true;
    }

}
