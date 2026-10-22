# Repository rulesets

GitHub does not read these files. They are the versioned copy of the rulesets configured on the repository (ADR-0034),
applied by the repository owner after a change.

| File | Ruleset |
|---|---|
| `main.json` | Protects the default branch: no deletion or force push, linear history, pull request with resolved conversations, rebase merge only, required checks on a branch up to date with `main`: CI (`services (...)`, `platform`, `alerting`) governance (`adr-guard`, `pr-scope`, `commit-messages`, `governance-scripts`) and security (`Secrets scan (gitleaks)`, `Analyze (java-kotlin)`); no new CodeQL high or critical alert or error. No bypass, the owner included. |

## Apply

```bash
R=bsamsonov/java-microservices-reference-architecture
# create
gh api -X POST repos/$R/rulesets --input .github/rulesets/main.json
# update the existing ruleset
ID=$(gh api repos/$R/rulesets --jq '.[] | select(.name=="main") | .id')
gh api -X PUT repos/$R/rulesets/$ID --input .github/rulesets/main.json
```

The same JSON can be imported in the UI: Settings → Rules → Rulesets → New ruleset → Import a ruleset.
