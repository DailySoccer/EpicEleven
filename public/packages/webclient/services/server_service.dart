library server_service;

import 'dart:async';
import 'package:angular/angular.dart';
import 'package:json_object/json_object.dart';
import 'package:webclient/webclient.dart';

abstract class ServerService {
  void               setSessionToken(String sessionToken);
  Future<JsonObject> signup(String firstName, String lastName, String email, String nickName, String password);
  Future<JsonObject> login(String email, String password);
  Future<JsonObject> getUserProfile();
  Future<JsonObject> getAllContests();
}

class DailySoccerServer implements ServerService {

  DailySoccerServer(this._http);

  void setSessionToken(String sessionToken) { _sessionToken = sessionToken; }

  Future<JsonObject> signup(String firstName, String lastName, String email, String nickName, String password) {
    return _innerServerCall("$HostServer/signup", {'firstName': firstName, 'lastName': lastName, 'email': email, 'nickName': nickName, 'password': password});
  }

  Future<JsonObject> login(String email, String password) {
    return _innerServerCall("$HostServer/login", {'email': email, 'password': password});
  }

  Future<JsonObject> getUserProfile() {
    return _innerServerCall("$HostServer/get_user_profile", null);
  }

  Future<JsonObject> getAllContests() {
    return _innerServerCall("$HostServer/get_all_contests", null);
  }


  /**
   * This is the only place where we call our server
   */
  Future<JsonObject> _innerServerCall(String url, Map postData) {
    var completer = new Completer<JsonObject>();

    if (_sessionToken != null) {
      url += "?sessionToken=$_sessionToken";
    }

    if (postData != null) {
      _http.post(url, null, params: postData)
          .then((httpResponse) => completer.complete(new JsonObject.fromMap(httpResponse.data)))
          .catchError((httpResponse) {
            print("_innerServerCall url: $url\n_innerServerCall res: $httpResponse");
            completer.completeError(new JsonObject.fromJsonString(httpResponse.data));
          });
    } else {
      _http.get(url)
           .then((httpResponse) => completer.complete(new JsonObject.fromMap(httpResponse.data)))
           .catchError((httpResponse) {
              print("_innerServerCall error: $url\n_innerServerCall: $httpResponse");
              completer.completeError(new JsonObject.fromJsonString(httpResponse.data));
          });
    }

    return completer.future;
  }

  Http _http;
  String _sessionToken;
}