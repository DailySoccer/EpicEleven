library webclient_main;

import 'package:logging/logging.dart';
import 'package:webclient/webclient.dart';


void main() {
  Logger.root.level = Level.FINEST;
  Logger.root.onRecord.listen((LogRecord r) { print(r.message); });

  startWebClientApp();
}
