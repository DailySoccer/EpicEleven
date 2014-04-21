library group;

import "package:json_object/json_object.dart";

class MatchGroup {
  String id;
  List<String> matchsIds;

  MatchGroup(this.id, this.matchsIds);

  MatchGroup.fromJsonObject(JsonObject json) {
    id        = json.id;
    matchsIds = json.matchsIds;
  }
}