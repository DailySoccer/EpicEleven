library webclient_router;

import 'package:angular/angular.dart';

void webClientRouteInitializer(Router router, RouteViewFactory views) {
  views.configure({
    'home': ngRoute(
        defaultRoute: true,
        view: 'view/home.tpl.html'
    ),
    'login': ngRoute(
        path: '/login',
        view: 'view/login.tpl.html'
    ),
    'join': ngRoute(
        path: '/join',
        view: 'view/join.tpl.html'
    ),
    'lobby': ngRoute(
        path: '/lobby',
        view: 'view/lobby.tpl.html'
    ),
    'contest_entry': ngRoute(
        path: '/contest_entry/:contestEntryId',
        mount: {
          'create': ngRoute(
              path: '/create',
              view: 'view/contest_entry.tpl.html')
        })
  });
}