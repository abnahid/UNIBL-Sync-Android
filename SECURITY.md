# Security Policy

## Reporting a vulnerability

Do not open a public issue for credentials, authentication bypasses, exposed
student data, or other sensitive vulnerabilities. Contact the repository owner
privately through the GitHub security contact or repository owner account with
steps to reproduce and the affected version.

Never include passwords, tokens, private keys, database URLs, or personal data
in a report.

## Scope

The Android client is a public client and must not contain server credentials or
admin authentication secrets. Firebase client configuration is not a server
credential, but access control must be enforced by Firebase rules and the
private backend.
