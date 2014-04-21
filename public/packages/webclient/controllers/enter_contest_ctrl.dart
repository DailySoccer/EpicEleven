library enter_contest_ctrl;

import 'package:angular/angular.dart';

import 'package:webclient/services/contest_service.dart';
import 'package:webclient/models/contest.dart';


@NgController(
    selector: '[enter-contest-ctrl]',
    publishAs: 'ctrl'
)
class EnterContestCtrl {
  Contest contest;

  EnterContestCtrl(Scope scope, RouteProvider routeProvider, ContestService _contestService) {
    contest = _contestService.getContest(routeProvider.parameters['contestEntryId']);
  }

  ContestService _contestService;
}
