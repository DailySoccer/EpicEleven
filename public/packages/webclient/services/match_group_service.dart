library match_group_service;

import "package:json_object/json_object.dart";

import "package:webclient/services/match_service.dart";
import "package:webclient/models/match_group.dart";

class MatchGroupService {

  // The group starts whenever the first match in the group starts
  String getMatchGroupStartDate(String groupId) => _matchService.getMatchStartDate(_groups[groupId].matchsIds[0]);


  MatchGroupService(this._matchService) {
    initFromJson(INIT_JSON);
  }

  initFromJson(String json) {
    _groups = new Map<String, MatchGroup>();

    var collection = new JsonObject.fromJsonString(json);

    for (var x in collection) {
      var group = new MatchGroup.fromJsonObject(x);
      _groups[group.id] = group;
    }
  }

  MatchService _matchService;
  var _groups;

  static String INIT_JSON = """
[
  {
    "id":"1-001",
    "matchsIds": ["1-BRACRO", "2-MEXCMR", "3-ESPNED", "4-CHIAUS"]
  }
]
  """;
}