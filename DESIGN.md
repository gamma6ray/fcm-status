---
version: alpha
name: FCM Status Neon Reliability
description: A focused Android utility for monitoring FCM reachability and keeping the connection active.
colors:
  primary: "#238BFF"
  background: "#050609"
  surface: "#14161B"
  border: "#393C46"
  text-primary: "#F8F8FA"
  text-secondary: "#A6A8B4"
  accent-blue: "#238BFF"
  accent-purple: "#BE4DFF"
  accent-magenta: "#E026F5"
  success: "#46EB66"
  error: "#FF5363"
typography:
  screen-title:
    fontFamily: sans-serif
    fontSize: 32px
    fontWeight: 400
    lineHeight: 1.05
  section-title:
    fontFamily: sans-serif
    fontSize: 21px
    fontWeight: 700
  body:
    fontFamily: sans-serif
    fontSize: 14px
    fontWeight: 400
  metric-label:
    fontFamily: sans-serif
    fontSize: 11px
    fontWeight: 400
  metric-value:
    fontFamily: sans-serif
    fontSize: 15px
    fontWeight: 700
rounded:
  card: 14px
  large-card: 16px
  button: 100px
  navigation-pill: 18px
spacing:
  screen-horizontal: 18px
  card-padding: 14px
  section-gap: 14px
components:
  status-banner:
    backgroundColor: "{colors.background}"
    textColor: "{colors.text-primary}"
    typography: "{typography.screen-title}"
  metric-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.card}"
    padding: "{spacing.card-padding}"
  heartbeat-button:
    backgroundColor: "{colors.accent-blue} -> {colors.accent-magenta}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.button}"
    height: 52px
  keep-alive-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.large-card}"
  selected-navigation:
    backgroundColor: "#0B1F3A"
    textColor: "{colors.accent-blue}"
    rounded: "{rounded.navigation-pill}"
---

# FCM Status Design System

## Overview

FCM Status is a calm, focused reliability utility. The interface should feel technical and trustworthy without looking like a diagnostic console. The visual language is a near-black canvas, restrained gray cards, white typography, and blue-to-purple neon accents used only for connection actions and navigation emphasis.

The primary task is immediately answering one question: can Google Play Services reach the FCM server? The reachability state must remain the strongest visual signal on the screen.

## Colors

Use `background` for the full screen and `surface` for cards. Use `border` sparingly to define card boundaries. Use `success` only for reachable state and recently sent heartbeat information. Use `error` only for unreachable state. The blue-purple-magenta gradient belongs to the heartbeat action and the banner arc; it should not be used for ordinary labels.

## Typography

The screen title is large and lightweight. Section titles are bold and compact. Body labels are readable but subdued. Metric labels are small and secondary; metric values are bold and centered within their columns. Avoid decorative typefaces and avoid all-caps except for short metric labels.

## Layout

Use an 18dp horizontal screen margin. Keep the status banner compact enough that the metrics card follows shortly after the reachability subtitle. The metrics card contains three equal columns—Server, Port, and Network—with centered labels and values and subtle vertical separators.

The primary heartbeat button should be prominent but inset from the card edges. The Keep-alive card follows it with a compact header, interval row, and last-heartbeat row. Icons and their labels share a vertical centerline.

## Elevation & Depth

The design uses contrast and glow rather than heavy elevation. Cards use a thin gray border and a slightly lighter surface against the black background. The banner arc and status indicator may glow softly, but text should remain crisp and never use an excessive shadow.

## Shapes

Cards use medium rounded corners. The heartbeat action is a pill. The selected navigation icon uses a compact rounded blue pill. Avoid mixing sharp rectangles with the rounded card language.

## Components

All visible icons must be PNG assets in `app/src/main/res/drawable-nodpi/`. Do not replace them with Unicode glyphs or programmatically drawn icons. Green and red reachability indicators must be visually identical except for state color.

The recheck control stays beside the Google Play Services subtitle. The “Send heartbeat now” button remains the main action and must not be hidden in Settings. The bottom navigation contains only Status and Settings.

## Do's and Don'ts

- Do keep the reachable/unreachable state obvious at a glance.
- Do center metric labels and values inside their columns.
- Do align row icons and text on the same vertical centerline.
- Do preserve the dark neon visual identity when adding features.
- Do use PNG icons consistently.
- Don't add a Diagnostics tab until a real diagnostic feature exists.
- Don't introduce unrelated colors or large decorative illustrations.
- Don't make secondary settings compete with the heartbeat action.
