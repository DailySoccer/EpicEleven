package controllers.admin;

import model.*;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;

// https://github.com/playframework/playframework/tree/master/samples/java/forms
public class PointsTranslationForm {
    @Constraints.Required
    public Integer eventType;

    @Constraints.Required
    public Integer points;

    public List<ValidationError> validate() {

        List<ValidationError> errors = new ArrayList<>();

        if(errors.size() > 0)
            return errors;

        return null;
    }
}
