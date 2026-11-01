import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:unscroll/engine/engine_api.g.dart';

// Contract test: what the Kotlin host sends back must decode into the same
// PermissionStatus on the Dart side.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel =
      'dev.flutter.pigeon.unscroll.EngineHostApi.getPermissionStatus';
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() => messenger.setMockMessageHandler(channel, null));

  test('getPermissionStatus decodes every field from the host reply', () async {
    final hostReply = PermissionStatus(
      usageAccess: true,
      accessibility: false,
      overlay: true,
      notifications: false,
      batteryUnrestricted: true,
      accessibilityRunning: false,
    );
    messenger.setMockMessageHandler(channel, (_) async {
      return EngineHostApi.pigeonChannelCodec.encodeMessage(<Object?>[
        hostReply,
      ]);
    });

    final status = await EngineHostApi().getPermissionStatus();

    expect(status.usageAccess, isTrue);
    expect(status.accessibility, isFalse);
    expect(status.overlay, isTrue);
    expect(status.notifications, isFalse);
    expect(status.batteryUnrestricted, isTrue);
    expect(status.accessibilityRunning, isFalse);
  });

  test('a missing host implementation surfaces as an error', () async {
    // No handler registered: the engine side isn't connected.
    await expectLater(
      EngineHostApi().getPermissionStatus(),
      throwsA(isA<PlatformException>()),
    );
  });
}
