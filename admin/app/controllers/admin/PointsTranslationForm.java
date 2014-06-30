package controllers.admin;

import model.*;
import org.bson.types.ObjectId;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;
import utils.OptaUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;

// https://github.com/playframework/playframework/tree/master/samples/java/forms
public class PointsTranslationForm {
    @Constraints.Required
    public OptaUtils.OptaEventType eventType;

    @Constraints.Required
    public Integer points;

    public String id;

    public List<ValidationError> validate() {

        List<ValidationError> errors = new ArrayList<>();

        if(errors.size() > 0)
            return errors;

        return null;
    }

    public PointsTranslationForm() {}

    public PointsTranslationForm(PointsTranslation pointsTranslation) {
        id = pointsTranslation.pointsTranslationId.toString();
        points = pointsTranslation.points;
        eventType = OptaUtils.OptaEventType.getEnum(pointsTranslation.eventTypeId);
    }

}
