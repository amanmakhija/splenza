# Splenza — Landing Page

A Next.js (App Router) + Tailwind + Framer Motion landing page for **Splenza**,
an AI-powered bill-splitting app.

## Run it locally

```bash
npm install
npm run dev
```

Then open http://localhost:3000.

## What's a placeholder right now

- **Logo** — `components/Logo.tsx` has a simple inline SVG mark. Swap the
  `<svg>` block (or the whole div) for your real app icon/logo whenever it's ready.
- **Screenshots** — `components/ScreenshotShowcase.tsx` renders 4 phone-frame
  placeholders. Replace the placeholder inner `<div>` in each `<figure>` with
  an `<Image src="/screenshots/xyz.png" .../>` once you have real screens
  (drop the images into `/public/screenshots/`).

## Structure

- `app/layout.tsx` — fonts (Fraunces / Inter / IBM Plex Mono) + global metadata
- `app/page.tsx` — assembles all sections
- `app/globals.css` — paper texture background, selection color, focus styles
- `components/`
  - `Nav.tsx` — sticky pill navbar
  - `Hero.tsx` — headline + the animated receipt (signature element)
  - `ReceiptDemo.tsx` — the voice → itemized split animation (client component)
  - `HowItWorks.tsx` — 3-step sequence
  - `Features.tsx` — bento-style feature grid
  - `ScreenshotShowcase.tsx` — phone-frame placeholders
  - `CTASection.tsx` — waitlist form (hook up to your email provider / backend)
  - `Footer.tsx`

## Design notes

- Palette: sage paper (`#EEF0E6`), deep ink (`#182119`), emerald (`#2B6E4F`),
  amber (`#E3A339`), violet (`#6C5CD9` — reserved for AI-specific moments).
- Type: Fraunces (display/serif), Inter (body), IBM Plex Mono (numbers,
  labels, receipt-style details).
- The hero's animated receipt is the one "signature" moment — it dramatizes
  the actual product mechanic (say a sentence → AI itemizes → split appears).
  Everything else is intentionally quieter so that element carries the page.

## Hooking up the waitlist form

The email input in `CTASection.tsx` currently just prevents default submit.
Wire it to whatever you use (a simple API route + Google Sheet, Resend,
Mailchimp, Supabase table, etc.) inside the `onSubmit` handler.
