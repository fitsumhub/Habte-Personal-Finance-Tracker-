# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.0.2   | ✅ Active  |
| 1.0.1   | ⚠️ Patch only |
| < 1.0.0 | ❌ Not supported |

## Reporting a Vulnerability

**Please do NOT report security vulnerabilities via public GitHub issues.**

If you discover a security vulnerability in Habte, please report it privately:

1. Email: **security@fitsumhub.dev** (or open a private advisory via GitHub Security tab)
2. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (optional)

We will acknowledge your report within **48 hours** and aim to release a patch within **7 days** for critical issues.

## Security Design

Habte is designed privacy-first:
- **All data is stored locally** on device — no data is transmitted to external servers
- **No user accounts** — no credentials to compromise
- **Biometric lock** protects app access
- **`FLAG_SECURE`** prevents screen capture of financial data
- **No sensitive data in logs** in release builds
