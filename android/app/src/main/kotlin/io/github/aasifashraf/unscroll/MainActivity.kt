package io.github.aasifashraf.unscroll

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.github.aasifashraf.unscroll.engine.api.EngineHostApi
import io.github.aasifashraf.unscroll.engine.api.EngineHostApiImpl

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        EngineHostApi.setUp(flutterEngine.dartExecutor.binaryMessenger, EngineHostApiImpl(this))
    }
}
