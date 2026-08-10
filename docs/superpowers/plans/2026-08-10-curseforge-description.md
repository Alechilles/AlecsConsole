# Alec's Console CurseForge Description Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a polished, paste-ready CurseForge project description for Alec's Console.

**Architecture:** Store the listing copy in one focused Markdown document under `docs/`. Derive every capability and limitation from the existing README and manifest, using the approved problem-first structure.

**Tech Stack:** CurseForge-compatible Markdown

## Global Constraints

- The copy is player-friendly, benefit-led, and practical.
- The supported server version is Hytale `0.5.x`.
- Do not claim remote client-log access, automatic uploads, unrestricted access, or universally cost-free performance.
- Explain that changed historical filters require Search or form submission.
- Preserve the README's ground-up rebuild attribution.

---

### Task 1: Write the CurseForge listing copy

**Files:**
- Create: `docs/curseforge-description.md`
- Reference: `README.md`
- Reference: `src/main/resources/manifest.json`

**Interfaces:**
- Consumes: The approved description design and currently documented mod behavior.
- Produces: One self-contained Markdown description ready to paste into CurseForge.

- [ ] **Step 1: Draft the problem-first description**

Create `docs/curseforge-description.md` with:

- a two-sentence benefit-led opening;
- one concise feature list;
- `/console` usage and the `alechilles.alecsconsole.command.console` permission;
- the historical-search refresh behavior;
- the same-host client-log limitation and `ClientLogDirectory` option;
- a compact configuration summary;
- Hytale `0.5.x` compatibility; and
- the ground-up rebuild attribution.

- [ ] **Step 2: Validate the public claims**

Compare the draft with `README.md` and `src/main/resources/manifest.json`. Confirm that every listed capability is documented, every operational limitation is retained, and no build/development instructions leaked into the public listing.

- [ ] **Step 3: Check Markdown quality**

Run:

```bash
git diff --check -- docs/curseforge-description.md
rg -n 'TBD|TODO|PLACEHOLDER|remote upload|zero[- ]cost' docs/curseforge-description.md
```

Expected: `git diff --check` produces no output, and `rg` finds no placeholder or prohibited claim.

- [ ] **Step 4: Commit the deliverable**

```bash
git add docs/curseforge-description.md
git commit -m "Docs: add CurseForge mod description"
```
