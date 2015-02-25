package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;
import utils.ListUtils;

import java.util.Date;
import java.util.List;

public class Promo implements JongoId {
    @Id
    public ObjectId promoId;

    public int priority;

    @JsonView(JsonViews.NotForClient.class)
    public Date createdAt;

    public Date activationDate;
    public Date deactivationDate;

    public String codeName;
    public String url;
    public String imageXs;
    public String imageDesktop;
    public String html;


    public Promo() {}

    public ObjectId getId() {
        return promoId;
    }

    public static Promo findOne(ObjectId promoId) {
        return Model.promos().findOne("{_id: #}", promoId).as(Promo.class);
    }

    public static List<Promo> getCurrent(int quantity) {
        return ListUtils.asList(Model.promos()
                        .aggregate("{$match: {activationDate: {$lte: #},  deactivationDate: {$gt: #}}} ", GlobalDate.getCurrentDate(), GlobalDate.getCurrentDate())
                        .and("{$sort: {priority: -1}}")
                        .and("{$limit: #}", quantity)
                        .as(Promo.class));
    }

    /**
     * Creacion de una promo
     */
    public static boolean createPromo(Date activationDate, Date deactivationDate, int priority, String codeName, String url, String html, String imageXs, String imageDesktop) {
        Promo promo = new Promo();
        promo.priority = priority;
        promo.createdAt = GlobalDate.getCurrentDate();
        promo.activationDate = activationDate;
        promo.deactivationDate = deactivationDate;
        promo.codeName = codeName;
        promo.url = url;
        promo.html = html;
        promo.imageDesktop = imageDesktop;
        promo.imageXs = imageXs;

        Model.promos().insert(promo);

        OpsLog.onNew(promo);
        return true;
    }

}
