package controllers.admin;

import model.Promo;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PromoForm {
    @Constraints.Required
    public String codeName;

    @Constraints.Required
    public Integer priority;

    public String id;

    public String url;
    public String html;
    public String imageXs;
    public String imageDesktop;

    @Constraints.Required
    public Date activationDate;
    @Constraints.Required
    public Date deactivationDate;


    public List<ValidationError> validate() {

        List<ValidationError> errors = new ArrayList<>();

        if(errors.size() > 0)
            return errors;

        return null;
    }

    public PromoForm() {}

    public PromoForm(Promo promo) {
        id = promo.promoId.toString();
        codeName = promo.codeName;
        priority = promo.priority;
        url = promo.url;
        html = promo.html;
        imageXs = promo.imageXs;
        imageDesktop = promo.imageDesktop;
        activationDate = promo.activationDate;
        deactivationDate = promo.deactivationDate;
    }

}
