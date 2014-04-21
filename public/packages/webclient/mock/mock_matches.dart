part of mock_daily_soccer_http;

class MockMatches {
  var  _matches;
  
  MockMatches() {
    _matches = new JsonObject.fromJsonString( json );
  }
  
  JsonObject get all => _matches;
      
  String json = """
      [
        {
          "id":"1-BRACRO",
          "teamA":{
            "id": "BRA",
            "name": "Brazil",
            "soccers": [
              { "id": "1-BRA", "name": "Jugador1-Brazil" }
            ]
          },
          "teamB":{
            "id": "CRO",
            "name": "Croatia",
            "soccers": [
              { "id": "1-CRO", "name": "Jugador1-Croatia" }
            ]
          },
          "date":"2014/06/12-21:00"
        },
        {
          "id":"2-MEXCMR",
          "teamA":{
            "id": "MEX",
            "name": "Mexico",
            "soccers": [
              { "id": "1-MEX", "name": "Jugador1-Mexico" }
            ]
          },
          "teamB":{
            "id": "CMR",
            "name": "Cameroon",
            "soccers": [
              { "id": "1-CMR", "name": "Jugador1-Cameroon" }
            ]
          },
          "date":"2014/06/13-17:00"
        },
        {
          "id":"3-ESPNED",
          "teamA":{
            "id": "ESP",
            "name": "Spain",
            "soccers": [
              { "id": "1-ESP", "name": "Jugador1-Spain" }
            ]
          },
          "teamB":{
            "id": "NED",
            "name": "Netherlands",
            "soccers": [
              { "id": "1-NED", "name": "Jugador1-Netherlands" }
            ]
          },
          "date":"2014/06/13-20:00"
        },
        {
          "id":"4-CHIAUS",
          "teamA":{
            "id": "CHI",
            "name": "Chile",
            "soccers": [
              { "id": "1-CHI", "name": "Jugador1-Chile" }
            ]
          },
          "teamB":{
            "id": "AUS",
            "name": "Australia",
            "soccers": [
              { "id": "1-AUS", "name": "Jugador1-Australia" }
            ]
          },
          "date":"2014/06/13-23:00"
        }
      ]
  """;
}