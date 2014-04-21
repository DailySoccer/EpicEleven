library menu_ctrl;

import 'package:angular/angular.dart';
import 'package:webclient/services/profile_service.dart';


@NgController(
    selector: '[menu-ctrl]',
    publishAs: 'menuCtrl'
)
class MenuCtrl implements NgAttachAware {
  bool   isLoggedIn = false;
  String fullName = "";
  String nickName = "";

  MenuCtrl(Scope scope, this._router, this._profileService) {
    isLoggedIn  = _profileService.isLoggedIn;

    scope.watch("isLoggedIn", (value, _) {
      isLoggedIn = value;

      if (isLoggedIn) {
        fullName = _profileService.user.fullName;
        nickName = _profileService.user.nickName;
      }
    }, context: _profileService);
  }

  void attach() {
    if (isLoggedIn) {
      _router.go('lobby', {});
    }
  }

  void logOut() {
    _profileService.logout();
  }

  Router _router;
  ProfileService _profileService;
}