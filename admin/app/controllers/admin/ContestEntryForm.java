package controllers.admin;

import model.*;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

// https://github.com/playframework/playframework/tree/master/samples/java/forms
public class ContestEntryForm {
    @Constraints.Required
    public String userId;

    @Constraints.Required
    public String contestId;

    @Constraints.Required
    public String goalkeeper;

    @Constraints.Required
    public String defense1, defense2, defense3, defense4;

    @Constraints.Required
    public String middle1, middle2, middle3, middle4;

    @Constraints.Required
    public String forward1, forward2;

    public Map<String, String> getTeamMap() {
        HashMap<String, String> teamMap = new HashMap<String, String>(){{
            put("goalkeeper", goalkeeper);
            put("defense1", defense1);
            put("defense2", defense2);
            put("defense3", defense3);
            put("defense4", defense4);
            put("middle1", middle1);
            put("middle2", middle2);
            put("middle3", middle3);
            put("middle4", middle4);
            put("forward1", forward1);
            put("forward2", forward2);
        }};
        return teamMap;
    }

    public List<String> getTeam() {
        List<String> list = new ArrayList<>();
        list.add(goalkeeper);
        list.add(defense1);
        list.add(defense2);
        list.add(defense3);
        list.add(defense4);
        list.add(middle1);
        list.add(middle2);
        list.add(middle3);
        list.add(middle4);
        list.add(forward1);
        list.add(forward2);
        return list;
    }

    public void addSoccer(String optaPlayerId) {
        if (goalkeeper.isEmpty())       goalkeeper = optaPlayerId;
        else if (defense1.isEmpty())    defense1 = optaPlayerId;
        else if (defense2.isEmpty())    defense2 = optaPlayerId;
        else if (defense3.isEmpty())    defense3 = optaPlayerId;
        else if (defense4.isEmpty())    defense4 = optaPlayerId;
        else if (middle1.isEmpty())     middle1 = optaPlayerId;
        else if (middle2.isEmpty())     middle2 = optaPlayerId;
        else if (middle3.isEmpty())     middle3 = optaPlayerId;
        else if (middle4.isEmpty())     middle4 = optaPlayerId;
        else if (forward1.isEmpty())    forward1 = optaPlayerId;
        else if (forward2.isEmpty())    forward2 = optaPlayerId;
    }

    public List<ValidationError> validate() {

        List<ValidationError> errors = new ArrayList<>();

        // Validar user
        User aUser = User.find(userId);
        if (aUser == null) {
            errors.add(new ValidationError("userId", "User invalid"));
        }

        // Validar contest
        Contest aContest = Contest.findOne(contestId);
        if (aContest == null) {
            errors.add(new ValidationError("contestId", "Contest invalid"));
        }

        Map<String, String> teamMap = getTeamMap();

        // Validar que existan cada uno de los futbolistas
        for (Map.Entry<String, String> entry : teamMap.entrySet()) {
            String key      = entry.getKey();
            String value    = entry.getValue();

            if (Model.templateSoccerPlayers().findOne("{ optaPlayerId: # }", value).as(TemplateSoccerPlayer.class) == null) {
                errors.add(new ValidationError(key, key + " invalid"));
            }
        }

        // Validar que no esten repetidos
        HashMap<String, String>  teamSet = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : teamMap.entrySet()) {
            String key      = entry.getKey();
            String value    = entry.getValue();

            if (teamSet.containsKey(value)) {
                errors.add(new ValidationError(key, key + " duplicated"));
            }
            else {
                teamSet.put(value, key);
            }
        }

        if(errors.size() > 0)
            return errors;

        return null;
    }
}
