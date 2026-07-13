---
name: devcollab-document-governance
description: Create, update, rename, move, classify, index, or review DevCollab Markdown documents under docs/. Use whenever a task adds or changes project documentation, including requirements, architecture, design, ADRs, test plans, local plans, learning notes, and documents that must or must not be committed to Git. Enforce the project document-governance file, numbering, metadata, references, index, and precise .gitignore rules.
---

# DevCollab Document Governance

Apply the project document standard to every Markdown document, whether Git-tracked or local-only.

## Required sources

Before changing documents, read completely:

1. `../../docs/05-devcollab-document-governance-v0.1.md`
2. `../../docs/00-devcollab-document-index.md`
3. `../../.gitignore`

Resolve paths from this skill directory. If the governance file is missing or contradictory, stop document creation and report the issue.

## Workflow

### 1. Inspect

- Inventory `docs/*.md` and occupied two-digit numbers.
- Search affected filenames and titles across the workspace.
- Check repository status when this is a valid Git repository.
- Preserve unrelated user changes.

### 2. Classify before writing

Choose exactly one class:

- **Shared, Git-eligible:** use `00–89`; ensure `.gitignore` does not match it.
- **Local, not committed:** use `90–99`, prefix the topic with `local-`, and add the exact `docs/<filename>` path to `.gitignore`.

Decision order:

1. Follow an explicit user instruction about Git status.
2. Otherwise follow the current document index.
3. Default team product, architecture, API, security, verification, or governance baselines to shared.
4. Default personal plans, learning notes, interview material, and explicitly private working records to local.
5. Ask only when classification materially remains ambiguous.

Never treat `.gitignore` as a secrets mechanism. Do not write credentials or unredacted sensitive data into either class.

### 3. Name and number

- Shared: `NN-devcollab-<topic>-v<major>.<minor>.md`
- Local: `9N-local-<topic>-v<major>.<minor>.md`
- Select an unused number; never overwrite another numbered document.
- Keep every Markdown document inside `docs/`.
- Keep filename, body, and index versions consistent.

### 4. Write or edit

- Start with outcome, scope, and boundaries.
- Include a document-information table with type, status, version, date, and source or applicability.
- Separate confirmed decisions, proposals, evidence, acceptance criteria, and open questions.
- Use Mermaid only when relationships, states, sequence, or layout become materially clearer.
- Use relative document references and primary official sources for external technical facts.
- Mark untested performance or capacity claims as pending verification.
- Do not invent coverage, benchmark data, completion percentages, approvals, or user research.
- Preserve existing layout and terminology when updating.

### 5. Synchronize governance surfaces

For every create, rename, move, classification change, or delete:

- Update `../../docs/00-devcollab-document-index.md`.
- Update exact paths in `../../.gitignore` for local documents.
- Remove stale ignore entries when a document becomes shared or is renamed.
- Search and update all inbound filename references.
- Update status and version metadata when required.

Do not use broad ignore patterns such as `docs/9*.md`.

### 6. Validate

Check:

- All Markdown documents remain under `docs/`.
- Numbers are unique and match Git classification.
- Shared documents are not ignored; every local document is precisely ignored.
- Index entries, filenames, body versions, and references agree.
- Required metadata exists and the stated status is truthful.
- Markdown fences are paired and Mermaid blocks structurally complete.
- No secrets, sensitive logs, personal absolute paths, or unsupported claims appear.
- Unrelated content is unchanged.

If Git is valid, use `git check-ignore` and status output. `.gitignore` does not untrack a committed file; report that condition instead of deleting the working copy.

## Operation-specific rules

### Create

Choose class and number first, write the document, then update index and ignore rules in the same task.

### Update

Preserve intent. Decide whether the change is a correction or a new version. Update dependent documents only when their behavior or references are affected.

### Rename or move

Verify source and destination are within the workspace, verify the target does not exist, move once, then replace all old references and ignore paths.

### Review

Report concrete violations by file and rule. Do not rewrite documents unless the user requests changes.
