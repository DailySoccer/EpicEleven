library profile_service;

import 'dart:async';
import 'dart:html';
import 'dart:convert';
import 'package:json_object/json_object.dart';
import 'package:webclient/models/user.dart';
import 'package:webclient/services/server_service.dart';


class ProfileService {
  User user = null;
  bool get isLoggedIn => user != null;

  ProfileService(this._server) {
    _tryProfileLoad();
  }

  Future signup(String firstName, String lastName, String email, String nickName, String password) {
    return _server.signup(firstName, lastName, email, nickName, password)
                  .then((jsonObject) => login(email, password));
  }

  Future login(String email, String password) {
    return _server.login(email, password).then(_onLoginResponse);
  }

  Future _onLoginResponse(JsonObject loginResponseJson) {
    _server.setSessionToken(loginResponseJson.sessionToken); // to make the getUserProfile call succeed

    return _server.getUserProfile()
                  .then((jsonObject) => _setProfile(loginResponseJson.sessionToken, new User.fromJsonObject(jsonObject), true));
  }

  Future logout() {
    _setProfile(null, null, true);
    return new Completer.sync().future;
  }

  void _setProfile(String theSessionToken, User theUser, bool bSave) {
    _sessionToken = theSessionToken;
    _server.setSessionToken(_sessionToken);
    user = theUser;

    if (bSave) {
      _saveProfile();
    }
  }

  void _tryProfileLoad() {
    var storedSessionToken = window.localStorage['sessionToken'];
    var storedUser = window.localStorage['user'];

    if (storedSessionToken != null && storedUser != null) {
      _setProfile(storedSessionToken, new User.fromJsonString(storedUser), false);
    }
  }

  void _saveProfile() {
    if (user != null && _sessionToken != null) {
      window.localStorage['sessionToken'] = _sessionToken;
      window.localStorage['user'] = JSON.encode(user);
    }
    else {
      window.localStorage.remove('sessionToken');
      window.localStorage.remove('user');
    }
  }

  ServerService _server;
  String _sessionToken;
}