// Contract between the Flutter UI and the Kotlin engine.
// Edit this file, then regenerate with: dart run pigeon --input pigeons/engine_api.dart
import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/engine/engine_api.g.dart',
    dartOptions: DartOptions(),
    kotlinOut: 'android/app/src/main/kotlin/io/github/aasifashraf/unscroll/engine/api/EngineApi.g.kt',
    kotlinOptions: KotlinOptions(
      package: 'io.github.aasifashraf.unscroll.engine.api',
    ),
    dartPackageName: 'unscroll',
  ),
)
/// Which special-access permissions Unscroll currently holds.
class PermissionStatus {
  PermissionStatus({
    required this.usageAccess,
    required this.accessibility,
    required this.overlay,
    required this.notifications,
    required this.batteryUnrestricted,
  });

  /// Usage access (screen time and limits).
  bool usageAccess;

  /// Unscroll's accessibility service is enabled (app, feed and site blocking).
  bool accessibility;

  /// Display over other apps (pause and block screens).
  bool overlay;

  /// Notifications are allowed (limit warnings, foreground service).
  bool notifications;

  /// Battery optimization is off, so the system won't stop blocking.
  bool batteryUnrestricted;
}

@HostApi()
abstract class EngineHostApi {
  /// Read-only check of every permission the engine depends on.
  PermissionStatus getPermissionStatus();
}
