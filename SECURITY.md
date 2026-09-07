# Security policy

## Supported versions

| Version | Supported          |
|---------|--------------------|
| 0.2.x   | Yes                |
| < 0.2   | No                 |

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Instead, email
security reports to the maintainers via the contact listed in
[`/IamCoder18/ftcpubsub`](https://github.com/IamCoder18/ftcpubsub).

We will acknowledge receipt within 48 hours and aim to ship a fix or
mitigation within 7 days for anything that affects the bus's correctness or
thread-safety guarantees.

## Scope

Security issues we care about:

- Anything that lets one `Node` interfere with another via the bus.
- Anything that lets hardware-bound work run on a non-hardware thread, or
  vice-versa.
- Anything that lets a malicious payload crash the orchestrator or leak
  memory.
- Build-chain issues (dependency confusion, signing key compromise, etc.).

Out of scope (please open a regular issue):

- Performance regressions on user code.
- Documentation errors.
