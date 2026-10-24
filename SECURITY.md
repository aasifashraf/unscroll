# Security Policy

## Supported versions
Only the latest release receives security fixes.

## Reporting a vulnerability
**Do not open a public issue.** Report privately through
[GitHub Security Advisories](https://github.com/aasifashraf/unscroll/security/advisories/new).

Please include:
- Affected version and where you installed it from (GitHub, F-Droid, Play)
- Device and Android version
- Steps to reproduce, and the impact

What to expect:
- Acknowledgement within **7 days**
- An assessment and a fix plan within **30 days**
- Credit in the release notes, unless you'd rather stay anonymous

## In scope
- Data leaving the device in any way
- Escalation through our exported components, intents, overlays or the Accessibility service
- Bypassing strict mode **through a bug** (as opposed to the designed emergency unlock)
- Secrets or signing material in the repository

## Out of scope
- Bypasses that need root, ADB, a factory reset or safe mode. These are documented limits of any Android blocker.
- Rooted or modified devices
