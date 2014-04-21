library lobby_ctrl;

import 'package:angular/angular.dart';

import 'package:webclient/services/contest_service.dart';
import 'package:webclient/models/contest.dart';


@NgController(
    selector: '[lobby-ctrl]',
    publishAs: 'ctrl'
)
class LobbyCtrl {
  Iterable<Contest> contests;

  LobbyCtrl(this._router, this._contestService) {
    contests = this._contestService.getAllContests();
  }

  EnterContest(Contest contest) {
    _router.go('team.create', { "contestId": contest.id });
  }

  Router _router;
  ContestService _contestService;
}

/*
  LobbyCtrl(Scope scope, this._router, this._contestManager ) {
    print("create LoginCtrl");

    this._contestManager.all
      .then( (values) {
        contests = values;
      });
  }
 */