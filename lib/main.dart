import 'package:flutter/material.dart';

void main() {
  runApp(const UnscrollApp());
}

/// Root widget. Screens and the design system are added in later tasks.
class UnscrollApp extends StatelessWidget {
  const UnscrollApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Unscroll',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark,
      darkTheme: ThemeData(brightness: Brightness.dark, useMaterial3: true),
      theme: ThemeData(useMaterial3: true),
      home: const Scaffold(body: Center(child: Text('Unscroll'))),
    );
  }
}
