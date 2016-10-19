package controllers.admin;

import model.PointsTranslation;
import model.opta.OptaEventType;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

public class PointsTranslationForm {
    @Constraints.Required
    public OptaEventType eventType;

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
        eventType = OptaEventType.getEnum(pointsTranslation.eventTypeId);
    }

}
