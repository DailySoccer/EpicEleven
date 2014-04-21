part of mock_daily_soccer_http;

class MockContests {
  var  _contests;
  
  MockContests() {
    _contests = new JsonObject.fromJsonString( json );
  }
  
  JsonObject get all => _contests;
      
  String json = """
  [
    { "id": "1-001", "name": "", "groupId": "1-001", "maxPlayers": 10, "capSalary": 60000, "playerIdList": [] }
  ]
  """;
}