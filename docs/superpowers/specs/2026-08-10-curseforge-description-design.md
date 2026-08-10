# Alec's Console CurseForge Description Design

## Goal

Create a polished, player-friendly CurseForge description that quickly explains why Alec's Console is useful and accurately documents how to use it.

## Audience and tone

The primary audience is Hytale server owners, administrators, mod developers, and players who have permission to inspect logs. The copy should be approachable and benefit-led, while retaining enough operational detail to prevent confusion. It should sound confident and practical rather than exaggerated.

## Structure

1. Open with the problem Alec's Console solves: checking live and historical logs without leaving the game.
2. Summarize the mod in one short paragraph.
3. Present a scannable feature list covering live logs, historical server logs, same-host client logs, filtering, pagination, Pause/Follow, and copyable full-text selection.
4. Explain basic use through the `/console` command and its permission node.
5. State the client-log limitation clearly: the Hytale plugin API cannot retrieve files from a remote player's computer, so client logs must be on the server host or in the configured local directory.
6. Include a compact configuration section with the meaningful settings, without reproducing build instructions.
7. End with the ground-up rebuild attribution already present in the README.

## Accuracy constraints

- Do not claim remote client-log access, automatic log uploads, or unrestricted access.
- Do not imply that historical searches update continuously; changed filters require Search or form submission.
- Describe resource use as bounded, not as zero-cost or universally performance-free.
- Keep the supported server version aligned with the manifest: Hytale `0.5.x`.
- Avoid undocumented installation steps and dependencies.

## Formatting

Use CurseForge-compatible Markdown with short headings, brief paragraphs, and one main bullet list. Avoid tables so the page remains readable on narrow layouts. The finished copy should be ready to paste directly into the project description field.

## Acceptance criteria

The final description communicates the benefit within its opening two sentences, includes every user-visible capability documented in the README, states the permission and client-log limitation, and contains no unsupported claims.
