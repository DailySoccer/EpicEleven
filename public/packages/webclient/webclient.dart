library webclient;

import 'dart:html';

import 'package:angular/angular.dart';
import 'package:angular/routing/module.dart';
import 'package:webclient/routing/webclient_router.dart';

import 'mock/mock_server.dart';

import 'services/profile_service.dart';
import 'services/match_service.dart';
import 'services/match_group_service.dart';
import 'services/contest_service.dart';
import 'services/contest_entry_service.dart';
import 'services/server_service.dart';

import 'controllers/menu_ctrl.dart';
import 'controllers/login_ctrl.dart';
import 'controllers/signup_ctrl.dart';
import 'controllers/lobby_ctrl.dart';
import 'controllers/enter_contest_ctrl.dart';

String HostServer = window.location.origin;


String encodeMap(Map data) {
  return data.keys.map((k) {
    return '${Uri.encodeComponent(k)}=${Uri.encodeComponent(data[k])}';
  }).join('&');
}

bool isLocalHost() {
  return (window.location.hostname.contains("127.") || window.location.hostname.contains("localhost"));
}


class WebClientApp extends Module {
  WebClientApp() {
    // REVIEW: switch entre real y simulación
    // real: DailySoccerServer / simulation: MockDailySoccerServer
    type(ServerService, implementedBy: DailySoccerServer);

    type(ProfileService);
    type(MatchService);
    type(MatchGroupService);
    type(ContestService);
    type(ContestEntryService);

    type(MenuCtrl);
    type(LoginCtrl);
    type(SignupCtrl);
    type(LobbyCtrl);
    type(EnterContestCtrl);

    value(RouteInitializerFn, webClientRouteInitializer);
    factory(NgRoutingUsePushState, (_) => new NgRoutingUsePushState.value(false));
  }
}


startWebClientApp() {

  if (isLocalHost()) {
    HostServer = _LocalHostServer;
  }
  print("Host: $HostServer");

  ngBootstrap(module: new WebClientApp());
}

String _LocalHostServer = "http://localhost:9000";
