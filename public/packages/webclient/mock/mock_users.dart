part of mock_daily_soccer_http;

class MockUsers {
  var _users = new Map<String, Map<String, JsonObject>>();

  MockUsers() {
    var collection = new JsonObject.fromJsonString(json);
    collection.forEach((x) => add(x));
  }

  bool exists(String email) {
    return _users.containsKey(email);
  }

  JsonObject get(String email) {
    return _users[email];
  }

  add(JsonObject user) {
    _users[user.email] = user;
  }

  String json = """
  [
    { "firstName": "Test One Two", "lastName": "Three Four", "email":"test@test.com", "nickName": "nick", "password": "test" } 
  ]
  """;
}