# Corridor design language

Source boards: the "Corridor Sign-in" design canvas. HTML exports of each board are in `.local/design/` on the dev machine (not committed).

## Look

Calm, architectural, a little warm. Navy for structure, brass for the one accent that says "a person is here". No gradients, no emoji.

## Type

- Display: Familjen Grotesk 600/700, tight tracking (-0.02em to -0.03em). Headings and the wordmark.
- Body: IBM Plex Sans 400/500/600, 15px, line height 1.5.

## Color tokens (`ui/src/styles/tokens.css`)

| Token | Light | Dark | Use |
|------|------|------|-----|
| `--bg` | #F5F6F9 | #0E1521 | page |
| `--surface` | #FFFFFF | #151F2F | cards, header |
| `--ink` | #141B2D | #E6EAF2 | text |
| `--muted` | #5B6478 | #A3ADC2 | secondary text |
| `--line` | #D5DBE4 | #2B3750 | borders |
| `--accent` | #23395D | #8FB0E0 | primary buttons, links |
| `--accent-ink` | #FFFFFF | #0C111B | text on accent |
| `--signal` | #C9A266 | #D6B47A | brass: presence, focus marks |
| `--danger-bg` / `--danger-ink` | #F6DFDB / #8E2F25 | #3D1D1A / #F4A79D | error alerts |
| `--info-bg` / `--info-ink` | #E4EAF4 / #23395D | #1D2A40 / #C5D5EE | neutral notices |
| `--brand-bg` | #1B2A45 | #0B1322 | sign-in brand panel |

## Components

- Inputs and buttons: 46–48px tall, 10px radius, 1px `--line` border; touch targets at least 44px.
- Alerts: icon + one sentence, 10px radius, `role="alert"` for errors, `role="status"` for notices.
- Cards: `--surface`, 1px `--line`, 14px radius, 28px padding.
- Brand mark: 32–36px navy tile, three white strokes, brass inset bottom edge.

## Screens (F-00, F-01)

- **Sign in:** brand panel (headline "Walk up to your team, wherever they work.") + form. States: ready, wrong password ("Email or password is incorrect."), signing in (disabled button, "Signing in…"), session ended ("Your session ended. Sign in again to continue."). On phones the form sits above the brand panel.
- **Profile:** header with wordmark, company and an initials button; "Your profile" card with name, role, work email, company, role; a Sign out section. States: loading skeleton, error with Try again, ready.
- **Not found:** "This page doesn’t exist", "The link may be old, or the room was moved.", button "Go to the office".

Every screen works in light and dark and at 390px wide.
