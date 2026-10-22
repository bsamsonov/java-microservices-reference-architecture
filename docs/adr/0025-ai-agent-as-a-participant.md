# ADR-0025: The AI agent is a participant: AGENTS.md, a scaffolding skill, rules enforced by tests

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD1, QD2

## Context and drivers
The templates are built to be extended by a developer working with an AI coding agent. The agent must find the rules,
the order of work and the commands without a person repeating them, and its mistakes must be caught by the build (QD2).
Creating a service with real business logic should take one to two days (QD1).

## Considered options
1. **`AGENTS.md` at the root, a `create-service` skill, rules enforced by tests** (ADR-0024).
2. **Tool-specific instruction files only** (one per agent product).
3. **No agent-specific material**: the agent reads the code and the READMEs like a new developer.

## Decision
Option 1. `AGENTS.md` holds the repository map, commands, service architecture, contracts, observability rules, the test table, "done when" and "never".
`CLAUDE.md` only imports it, so other agents read the same file. The skill `.claude/skills/create-service/` sets the order of work for a new service
and `scaffold.sh` copies a template with renames. Every rule an agent is likely to break has a test.

## Consequences
- **Good:** the agent starts from the same rules a developer does; two services (`notifications`, `catalog`) were built through the skill;
  the format is shared across agent tools (AGENTS.md moved to the Agentic AI Foundation under the Linux Foundation).
- **Cost:** `AGENTS.md` must follow every change in the code and the ADRs, or the agent follows stale rules;
  the skill is in one tool's format; a rule without a test is only a request.

## When to choose differently
- A team that does not use coding agents: the same content still works as a contributor guide; the skill becomes a checklist.
- A team on one agent tool with richer features (hooks, subagents): add them, but keep `AGENTS.md` as the single source of rules.

## Evidence
- [`AGENTS.md`](../../AGENTS.md) and [`create-service` skill](../../.claude/skills/create-service/SKILL.md).
- [Linux Foundation: the Agentic AI Foundation](https://www.linuxfoundation.org/press/linux-foundation-announces-the-formation-of-the-agentic-ai-foundation) (2025-12-09), [agents.md](https://agents.md/).
