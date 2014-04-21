part of mock_daily_soccer_http;

class MockGroups {
  var  _groups;
  
  MockGroups() {
    _groups = new JsonObject.fromJsonString( json );
  }
  
  JsonObject get all => _groups;
      
  String json = """
  [
    {
      "id":"1-001",
      "matchIdList": ["1-BRACRO", "2-MEXCMR", "3-ESPNED", "4-CHIAUS"]
    }
  ]
  """;  
}