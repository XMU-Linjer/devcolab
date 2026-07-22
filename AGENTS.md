# DevCollab frontend design authority

## Authoritative design files

Before changing the code/document workbench, read:

- `docs/design/code-doc-linked-workbench/devcollab-linked-workbench.html`
- `docs/design/code-doc-linked-workbench/reference.png`（从 HTML 手动截取，可选但强烈建议）（从 HTML 手动截取，可选但强烈建议）
- `docs/design/code-doc-linked-workbench/DESIGN_SPEC.md`

These files are the acceptance baseline, not optional inspiration.

## Required workflow

1. Inspect the existing frontend architecture and identify reusable components.
2. Run the reference HTML locally and inspect it in a browser.
3. Run the existing application and capture its current state.
4. Explain the minimum component/state changes required.
5. Implement one vertical slice at a time.
6. After each slice, run the application and capture a screenshot at the same viewport.
7. Compare the result against the reference before declaring completion.
8. Run existing lint, type-check, unit tests, and browser tests.

## Non-negotiable behavior

- Code ranges and document Blocks have explicit bidirectional links.
- Clicking either side focuses and scrolls the other side.
- The center Code Anchor Rail remains visible in linked-comparison mode.
- The issue/evidence inspector is collapsible.
- Linked comparison, code focus, document focus, and drift review share the same selected context.
- Existing structured Block editing and document collaboration must be preserved.

## Forbidden shortcuts

- Do not merely iframe the reference HTML.
- Do not replace the product with two static panes.
- Do not redesign the information architecture without explicit approval.
- Do not introduce Monaco, terminal, LSP, or OpenVSCode Server for this task.
- Do not claim completion based only on compilation; visually inspect the running result.
- Do not modify backend architecture unless required by the approved vertical slice.

## Completion evidence

A completion report must include:

- files changed;
- commands run;
- tests passed;
- screenshots produced;
- which acceptance criteria are satisfied;
- any remaining mismatches.
