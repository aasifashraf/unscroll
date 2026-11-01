package io.github.aasifashraf.unscroll

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.github.aasifashraf.unscroll.engine.api.EngineHostApi
import io.github.aasifashraf.unscroll.engine.api.EngineHostApiImpl
import io.github.aasifashraf.unscroll.engine.core.Engine
import io.github.aasifashraf.unscroll.engine.service.GuardService

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        EngineHostApi.setUp(flutterEngine.dartExecutor.binaryMessenger, EngineHostApiImpl(this))
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        EngineHostApi.setUp(flutterEngine.dartExecutor.binaryMessenger, null)
        super.cleanUpFlutterEngine(flutterEngine)
    }

    override fun onResume() {
        super.onResume()
        Engine.init(this)
        Engine.onUnscrollOpened()
        GuardService.start(this) // app is in the foreground: always allowed to start the service
    }
}
