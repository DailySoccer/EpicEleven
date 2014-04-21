library contest_service;

import "package:json_object/json_object.dart";

import "../services/match_group_service.dart";
import "../models/contest.dart";

class ContestService {

  Iterable<Contest> getAllContests() => _contests.values;
  Contest getContest(String contestId) => _contests[contestId];


  ContestService(this._groupService) {
    initFromJson(INIT_JSON);
  }

  initFromJson(String json) {
    _contests = new Map<String, Contest>();

    var collection = new JsonObject.fromJsonString(json);

    for (var x in collection) {
      var contest = new Contest.fromJsonObject(x);

      if (contest.name.isEmpty) {
        String date = _groupService.getMatchGroupStartDate(contest.groupId);
        contest.name = "Salary Cap ${contest.capSalary} - $date";
      }
      _contests[contest.id] = contest;
    }
  }

  MatchGroupService _groupService;
  var _contests;

  static String INIT_JSON = """
  [
    { "id": "1-001", "name": "", "groupId": "1-001", "maxPlayers": 10, "capSalary": 60000, "playersIds": [] },
    { "id": "1-002", "name": "", "groupId": "1-001", "maxPlayers": 10, "capSalary": 30000, "playersIds": [] }
  ]
  """;
}