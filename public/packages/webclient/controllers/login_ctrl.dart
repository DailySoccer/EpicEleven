library login_ctrl;

import 'package:angular/angular.dart';
import 'package:webclient/services/profile_service.dart';


@NgController(
    selector: '[login-ctrl]',
    publishAs: 'ctrl'
)
class LoginCtrl {

  LoginCtrl(this._router, this._profileManager) {
  }

  void login(String email, String password) {
    _profileManager.login(email, password)
        .then((_) => _router.go('lobby', {}))
        .catchError((error) => print("WTF 322: tratar errores $error"));
  }

  Router _router;
  ProfileService _profileManager;
}
