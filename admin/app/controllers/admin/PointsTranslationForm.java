package controllers.admin;

import model.*;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;
import model.opta.OptaProcessor;

import java.util.ArrayList;
import java.util.List;

// https://github.com/playframework/playframework/tree/master/samples/java/forms
public class PointsTranslationForm {
    @Constraints.Required
    public OptaProcessor.OptaEventType eventType;

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
        eventType = OptaProcessor.OptaEventType.getEnum(pointsTranslation.eventTypeId);
    }

}
