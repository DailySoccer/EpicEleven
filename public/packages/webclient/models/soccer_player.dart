library soccer_player;

import "package:json_object/json_object.dart";

class SoccerPlayer {
  String id;
  String name;
  String teamId;
  String position;
  int    points;
  num    salary;

  SoccerPlayer.fromJsonObject(JsonObject json) {
    id   = json.id;
    name = json.name;
  }
}