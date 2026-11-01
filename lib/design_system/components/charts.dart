import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme.dart';
import '../tokens.dart';

/// Gradient progress ring (focus timer, daily goal).
class ProgressRing extends StatelessWidget {
  const ProgressRing({
    super.key,
    required this.fraction,
    required this.size,
    this.stroke = 10,
    this.center,
    required this.semanticLabel,
  });

  final double fraction;
  final double size;
  final double stroke;
  final Widget? center;
  final String semanticLabel;

  @override
  Widget build(BuildContext context) => Semantics(
    label: semanticLabel,
    child: SizedBox.square(
      dimension: size,
      child: CustomPaint(
        painter: _RingPainter(
          fraction.clamp(0.0, 1.0),
          stroke,
          context.colors.surfaceSunken,
          context.colors.gradient,
        ),
        child: center == null
            ? null
            : Center(child: ExcludeSemantics(child: center)),
      ),
    ),
  );
}

class _RingPainter extends CustomPainter {
  _RingPainter(this.fraction, this.stroke, this.track, this.gradient);

  final double fraction;
  final double stroke;
  final Color track;
  final Gradient gradient;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final arc = rect.deflate(stroke / 2);
    canvas.drawArc(
      arc,
      0,
      math.pi * 2,
      false,
      Paint()
        ..color = track
        ..style = PaintingStyle.stroke
        ..strokeWidth = stroke,
    );
    if (fraction <= 0) return;
    canvas.drawArc(
      arc,
      -math.pi / 2,
      math.pi * 2 * fraction,
      false,
      Paint()
        ..shader = gradient.createShader(rect)
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeWidth = stroke,
    );
  }

  @override
  bool shouldRepaint(_RingPainter old) =>
      old.fraction != fraction || old.track != track;
}

/// One bar per day, a dashed goal line, and today highlighted. Tap a bar to read its value.
class WeekBars extends StatefulWidget {
  const WeekBars({
    super.key,
    required this.values,
    required this.labels,
    required this.goal,
    required this.format,
    this.height = 150,
  });

  /// Hours per day, oldest first; the last value is today.
  final List<double> values;
  final List<String> labels;
  final double goal;
  final String Function(double hours) format;
  final double height;

  @override
  State<WeekBars> createState() => _WeekBarsState();
}

class _WeekBarsState extends State<WeekBars> {
  int? _selected;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final selected = _selected ?? widget.values.length - 1;
    final description = [
      for (var i = 0; i < widget.values.length; i++)
        '${widget.labels[i]} ${widget.format(widget.values[i])}',
    ].join(', ');
    return Semantics(
      label: 'Screen time by day: $description',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ExcludeSemantics(
            child: Text(
              '${widget.labels[selected]} · ${widget.format(widget.values[selected])}',
              style: context.text.label,
            ),
          ),
          const SizedBox(height: Space.sm),
          ExcludeSemantics(
            child: LayoutBuilder(
              builder: (context, constraints) => GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTapDown: (d) {
                  final slot = constraints.maxWidth / widget.values.length;
                  setState(
                    () => _selected = (d.localPosition.dx / slot).floor().clamp(
                      0,
                      widget.values.length - 1,
                    ),
                  );
                },
                child: CustomPaint(
                  size: Size(constraints.maxWidth, widget.height),
                  painter: _BarsPainter(
                    values: widget.values,
                    labels: widget.labels,
                    goal: widget.goal,
                    selected: selected,
                    bar: c.chartSingle,
                    grid: c.chartGrid,
                    base: c.chartBase,
                    goalColor: c.teal,
                    label: c.textMuted,
                    labelStrong: c.text,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _BarsPainter extends CustomPainter {
  _BarsPainter({
    required this.values,
    required this.labels,
    required this.goal,
    required this.selected,
    required this.bar,
    required this.grid,
    required this.base,
    required this.goalColor,
    required this.label,
    required this.labelStrong,
  });

  final List<double> values;
  final List<String> labels;
  final double goal;
  final int selected;
  final Color bar;
  final Color grid;
  final Color base;
  final Color goalColor;
  final Color label;
  final Color labelStrong;

  @override
  void paint(Canvas canvas, Size size) {
    const labelSpace = 20.0;
    final chartH = size.height - labelSpace;
    final top = [...values, goal, 1.0].reduce(math.max);
    final max = (top / 2).ceil() * 2.0;
    double y(double v) => chartH * (1 - v / max);

    for (var v = 0.0; v <= max; v += 2) {
      canvas.drawLine(
        Offset(0, y(v)),
        Offset(size.width, y(v)),
        Paint()..color = v == 0 ? base : grid,
      );
    }
    final slot = size.width / values.length;
    final width = math.min(22.0, slot * 0.56);
    for (var i = 0; i < values.length; i++) {
      final x = slot * i + (slot - width) / 2;
      final rect = RRect.fromRectAndCorners(
        Rect.fromLTRB(x, y(values[i]), x + width, y(0)),
        topLeft: const Radius.circular(4),
        topRight: const Radius.circular(4),
      );
      canvas.drawRRect(
        rect,
        Paint()..color = bar.withValues(alpha: i == selected ? 1 : 0.6),
      );
      final tp = TextPainter(
        text: TextSpan(
          text: labels[i],
          style: TextStyle(
            fontSize: 11,
            color: i == selected ? labelStrong : label,
            fontWeight: i == selected ? FontWeight.w700 : FontWeight.w400,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, Offset(x + width / 2 - tp.width / 2, chartH + 5));
    }
    final dash = Paint()
      ..color = goalColor
      ..strokeWidth = 1.5;
    for (var x = 0.0; x < size.width; x += 8) {
      canvas.drawLine(
        Offset(x, y(goal)),
        Offset(math.min(x + 4, size.width), y(goal)),
        dash,
      );
    }
  }

  @override
  bool shouldRepaint(_BarsPainter old) =>
      old.values != values || old.selected != selected || old.bar != bar;
}
