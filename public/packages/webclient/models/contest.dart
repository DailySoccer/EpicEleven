library contest;

import "package:json_object/json_object.dart";

class Contest {
  String id;
  String name;
  String groupId;
  int maxPlayers;
  num capSalary;
  List<String> playersIds;

  Contest(this.id, this.name, this.groupId, this.maxPlayers, this.capSalary, this.playersIds);

  Contest.fromJsonObject(JsonObject json) {
     id         = json.id;
     name       = json.name;
     groupId    = json.groupId;
     maxPlayers = json.maxPlayers;
     capSalary  = json.capSalary;
  }

  Contest.fromJsonString(String json) : this.fromJsonObject(new JsonObject.fromJsonString(json));
}