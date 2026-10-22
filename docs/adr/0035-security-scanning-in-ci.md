# ADR-0035: Security scanning in CI: gitleaks, CodeQL and dependency review

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD2, QD4
- **Amends:** ADR-0024, the secret scan runs in CI instead of by hand

## Context and drivers
ADR-0024 lists `.gitleaks.toml` among the checks, but the scan ran only when someone ran it, and nothing looked at the Java code
or at new dependencies for known vulnerabilities. The repository is public and an AI agent writes much of the code (QD2),
so a leaked key or an injectable query must stop the merge, not wait for a reader to notice (QD4).

## Considered options
1. **GitHub-native tools in CI**: `gitleaks` over the whole history, CodeQL for the Java code, dependency review on pull requests;
   the ruleset requires the first two and blocks new high alerts.
2. **One commercial platform** (Snyk, Sonar with security rules): one dashboard for all of it.
3. **Local hooks only** (`gitleaks` in pre-commit): fast feedback, nothing enforced.

## Decision
Option 1.
- `Secrets scan (gitleaks)` in `ci.yml`: the whole history with `.gitleaks.toml` (default rules, allowlist for local demo values). Required.
- `Analyze (java-kotlin)` in `codeql.yml`: CodeQL `security-and-quality` with `build-mode: none`, on pull requests, on `main` and weekly. Required,
  and the ruleset rule `code_scanning` blocks a merge that adds a high or critical security alert or an error.
- `Dependency review` in `ci.yml`, pull requests only: fails when a change adds a dependency with a known high or critical vulnerability.
  Not required, because it does not run on `main`; it still shows as a failed check.
- Dependabot alerts and security updates, secret scanning and push protection are turned on in the repository settings.

## Consequences
- **Good:** a secret, a known vulnerable dependency or a new high CodeQL alert cannot reach `main` unnoticed; the checks are free for a public repository.
- **Cost:** CodeQL without a build does not see the generated OpenAPI code and adds a few minutes to a pull request;
  a false positive needs a dismissal in the Security tab; a private copy of the template needs GitHub Advanced Security for CodeQL and push protection.

## When to choose differently
- A private repository without GitHub Advanced Security: `gitleaks` in CI stays, CodeQL gives way to SpotBugs with Find Security Bugs or Semgrep.
- An organisation with a security platform already in place: option 2, keep `gitleaks` in pre-commit.

## Evidence
- [GitHub: about code scanning with CodeQL](https://docs.github.com/en/code-security/code-scanning/introduction-to-code-scanning/about-code-scanning-with-codeql).
- [gitleaks](https://github.com/gitleaks/gitleaks) and `.gitleaks.toml` in this repository.
