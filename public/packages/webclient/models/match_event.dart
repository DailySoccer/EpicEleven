library match_day;

import "package:json_object/json_object.dart";

class MatchEvent {
  String id;
  String teamAId;
  String teamBId;
  String date;

  MatchEvent(this.id, this.teamAId, this.teamBId, this.date);

  MatchEvent.fromJsonObject(JsonObject json) {
    id = json.id;
    teamAId = json.teamAId;
    teamBId = json.teamBId;
    date = json.date;
  }
}
