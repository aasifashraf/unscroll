import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app_state.dart';
import '../../design_system/components/surfaces.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';

class WebsitesScreen extends StatefulWidget {
  const WebsitesScreen({super.key, required this.state});

  final AppState state;

  @override
  State<WebsitesScreen> createState() => _WebsitesScreenState();
}

class _WebsitesScreenState extends State<WebsitesScreen> {
  final _site = TextEditingController();
  final _keyword = TextEditingController();

  @override
  void dispose() {
    _site.dispose();
    _keyword.dispose();
    super.dispose();
  }

  Future<void> _addSite() async {
    final text = _site.text.trim();
    if (text.isEmpty) return;
    var valid = false;
    final error = await widget.state.edit(
      (api) async => valid = await api.addBlockedSite(text),
    );
    if (!mounted) return;
    if (error != null || !valid) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            error ?? 'That doesn\'t look like a web address, e.g. example.com',
          ),
        ),
      );
      return;
    }
    _site.clear();
  }

  Future<void> _addKeyword() async {
    final word = _keyword.text.trim().toLowerCase();
    if (word.length < 3 || word.contains(' ')) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Use one word of at least 3 letters.')),
      );
      return;
    }
    // Read the keywords when the edit runs, so quick adds and removes don't undo each other.
    final error = await widget.state.edit(
      (api) => api.setBlockedKeywords(
        {...?widget.state.snapshot?.blockedKeywords, word}.toList(),
      ),
    );
    if (!mounted) return;
    if (error != null) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
      return;
    }
    _keyword.clear();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Websites')),
    body: ListenableBuilder(
      listenable: widget.state,
      builder: (context, _) {
        final snap = widget.state.snapshot;
        final sites = snap?.blockedSites ?? const [];
        final keywords = snap?.blockedKeywords ?? const [];
        return ListView(
          padding: const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, Space.xxl),
          children: [
            TextField(
              controller: _site,
              keyboardType: TextInputType.url,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _addSite(),
              decoration: InputDecoration(
                hintText: 'Add a site, e.g. example.com',
                prefixIcon: const Icon(Icons.language),
                suffixIcon: IconButton(
                  tooltip: 'Add site',
                  onPressed: _addSite,
                  icon: const Icon(Icons.add),
                ),
              ),
            ),
            const SizedBox(height: Space.md),
            for (final s in sites)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(Icons.public, color: context.colors.accentText),
                title: Text(s),
                subtitle: const Text('Includes subdomains'),
                trailing: IconButton(
                  tooltip: 'Remove $s',
                  icon: const Icon(Icons.close),
                  onPressed: () async {
                    await widget.state.edit((api) => api.removeBlockedSite(s));
                    if (!context.mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('Removed $s'),
                        action: SnackBarAction(
                          label: 'Undo',
                          onPressed: () => widget.state.edit(
                            (api) async => api.addBlockedSite(s),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),
            const SizedBox(height: Space.xl),
            const SectionTitle('Keywords in the address'),
            TextField(
              controller: _keyword,
              textInputAction: TextInputAction.done,
              // The engine ignores longer keywords.
              inputFormatters: [LengthLimitingTextInputFormatter(40)],
              onSubmitted: (_) => _addKeyword(),
              decoration: InputDecoration(
                hintText: 'e.g. shorts',
                prefixIcon: const Icon(Icons.text_fields),
                suffixIcon: IconButton(
                  tooltip: 'Add keyword',
                  onPressed: _addKeyword,
                  icon: const Icon(Icons.add),
                ),
              ),
            ),
            const SizedBox(height: Space.sm),
            Wrap(
              spacing: Space.sm,
              children: [
                for (final k in keywords)
                  InputChip(
                    label: Text(k),
                    onDeleted: () => widget.state.edit(
                      (api) => api.setBlockedKeywords([
                        for (final x
                            in widget.state.snapshot?.blockedKeywords ??
                                const <String>[])
                          if (x != k) x,
                      ]),
                    ),
                    deleteButtonTooltipMessage: 'Remove $k',
                  ),
              ],
            ),
            const SizedBox(height: Space.xl),
            const UBanner(
              text: 'Works in Chrome, Firefox, Brave, Edge and Samsung Internet when accessibility is on. Other browsers may not be detected; block them as apps if needed.',
            ),
          ],
        );
      },
    ),
  );
}
