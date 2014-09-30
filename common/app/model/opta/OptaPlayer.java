package model.opta;

import model.GlobalDate;
import model.Model;
import org.jdom2.Element;
import play.Logger;
import utils.ListUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class OptaPlayer {
    public String optaPlayerId;
    public String name;
    public String nickname;
    public String position;
    public String teamId;
    public Date updatedTime;
    public boolean dirty = true;

    public OptaPlayer() {}

    public OptaPlayer(Element playerObject, Element teamObject) {

        if (playerObject.getAttribute("firstname") != null) {
            optaPlayerId = OptaProcessor.getStringId(playerObject, "id");
            name = playerObject.getAttributeValue("firstname") + " " + playerObject.getAttributeValue("lastname");
            position = playerObject.getAttributeValue("position");
            teamId = OptaProcessor.getStringId(teamObject, "id");
        }
        else {
            optaPlayerId = OptaProcessor.getStringId(playerObject, "uID");

            if (playerObject.getChild("Name") != null) {
                name = playerObject.getChild("Name").getContent().get(0).getValue();
                optaPlayerId = OptaProcessor.getStringId(playerObject, "uID");
                position = playerObject.getChild("Position").getContent().get(0).getValue();
            }
            else if (playerObject.getChild("PersonName") != null) {
                if (playerObject.getChild("PersonName").getChild("Known") != null) {
                    nickname = playerObject.getChild("PersonName").getChild("Known").getContent().get(0).getValue();
                }
                name = playerObject.getChild("PersonName").getChild("First").getContent().get(0).getValue() + " "
                        + playerObject.getChild("PersonName").getChild("Last").getContent().get(0).getValue();
            }
            else {
                Logger.error("WTF 29211: No name for optaPlayerId " + optaPlayerId);
            }

            if (playerObject.getChild("Position") != null) {
                position = playerObject.getChild("Position").getContent().get(0).getValue();

                if (position.equals("Substitute")) {
                    Logger.info("WTF 23344: Sustituto! {}", name);
                }
            }

            teamId = OptaProcessor.getStringId(teamObject, "uID");
        }

        updatedTime = GlobalDate.getCurrentDate();
    }

    public String getTeamName() {
        return OptaTeam.findOne(this.teamId).name;
    }

    public boolean hasChanged(OptaPlayer optaPlayer) {
        return  (name == null)      || !name.equals(optaPlayer.name)            ||
                (position == null)  || !position.equals(optaPlayer.position)    ||
                (teamId == null)    || !teamId.equals(optaPlayer.teamId);
    }

    static public List<OptaPlayer> findAllFromTeam(String optaTeamId) {
        return ListUtils.asList(Model.optaPlayers().find("{teamId: #}", optaTeamId).as(OptaPlayer.class));
    }

    static public HashMap<String, OptaPlayer> asMap(List<OptaPlayer> optaPlayers) {
        HashMap<String, OptaPlayer> map = new HashMap<>();
        for (OptaPlayer optaPlayer: optaPlayers) {
            map.put(optaPlayer.optaPlayerId, optaPlayer);
        }
        return map;
    }
}
