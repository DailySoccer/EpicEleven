library match_service;

import "package:json_object/json_object.dart";
import "package:webclient/models/match_event.dart";


class MatchService {

  String getMatchStartDate(String matchEventId) => _matchEvents[matchEventId].date;

  MatchService() : this.initFromJson(INIT_JSON);

  MatchService.initFromJson(String json) {
    _matchEvents = new Map<String, MatchEvent>();

    var collection = new JsonObject.fromJsonString(json);

    for (var x in collection) {
      var match = new MatchEvent.fromJsonObject(x);
      _matchEvents[match.id] = match;
    }
  }

  var _matchEvents;


  static String INIT_JSON = """
[
  {
    "id":"1-BRACRO",
    "teamAId":"BRA",
    "teamBId":"CRO",
    "date":"2014/06/12-21:00"
  },
  {
    "id":"2-MEXCMR",
    "teamAId":"MEX",
    "teamBId":"CMR",
    "date":"2014/06/13-17:00"
  },
  {
    "id":"3-ESPNED",
    "teamAId":"ESP",
    "teamBId":"NED",
    "date":"2014/06/13-20:00"
  },
  {
    "id":"4-CHIAUS",
    "teamAId":"CHI",
    "teamBId":"AUS",
    "date":"2014/06/13-23:00"
  }
]
  """;
}