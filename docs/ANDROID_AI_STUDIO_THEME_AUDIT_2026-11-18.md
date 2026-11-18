# RBX Studio Android — AI Hub visual parity audit

## Reported defect

The user-tested AI screen used default Android buttons with blue fill,
full-width action rows, 13dp radius and oversized gaps; it did not
visually belong to the GPUI Studio editor. This is a styling/layout
defect, not a request to remove functionality or replace working buttons.

## Design source of truth

The project's pinned original GPUI editor defines this theme, not a
newly invented dark-mode screenshot or set of fonts:

- `chteau/rbx-native` at `eb85a1088a69f4159598b4fed0ae26956573c175`
- `assets/themes/default/theme.json`
- `crates/rbx_studio/src/tokens.rs`
- `crates/rbx_studio/src/launcher/ui.rs`

Specific upstream tokens: black #0A0A0B, dock #121213,
field_select #191A1C, text #E2E2E6, text2 #8F8F97,
border #FFFFFF0F, border2 #FFFFFF1C, check_on #6C7FDB,
accent_hover #7385DD, secondary_hover #202123.
Original launcher button: 34px (28px compact), radius 5px, hairline border,
12.5px label, 6px icon gap. The mobile adapter uses 36dp for a practical
touch target. The original surfaces are **flat**, not gradients or
bitmap textures. Reproducing the true texture means reproducing fill,
border, text weight, radius and interaction states.

## Implementation

- A single new `StudioAiTheme.java` defines the source-token colors and
  creates background state lists (default/pressed/hover/focus/disabled),
  compact buttons, monochrome line icons, custom chevron dropdowns,
  focused text fields and accented checkboxes.
- Existing `StudioAiHub.java` uses those same builders throughout;
  dynamically inserted VFX/Toolbox/model actions also pass through
  the same `button` factory.
- The previous large filled blue buttons are replaced with compact
  neutral editor controls. Only the intentional primary action
  is accented lilac. Existing button text, handlers, request behavior,
  permissions, confirmation dialogs and content are preserved.
- API key/provider actions are grouped inside a bounded dock panel;
  Save and Remove sit in one horizontal row in landscape rather than
  occupying two full-width rows. The panel remains within the display
  width and scrollability is preserved.
- No user credentials, MCP tokens, project data or API results are copied
  into theme or screenshot test code.

## Real Android screenshot validation

The workflow `ai-theme-android-visual-check.yml` uses a *genuine*
previously built Studio ARM64 APK, keeps native libraries byte-identical,
recompiles the current Java AI Activity, and boots an Android emulator
to capture the actual UI and Android accessibility hierarchy. The
temporary screenshot-test APK alone sets the AI Activity exported=true;
**production AndroidManifest.xml remains nonexported**. This diagnostic
APK is not a user delivery and has an ephemeral signing key.

A previous screenshot on commit `129889dc` proved that the replacement
theme was displayed by Android, with a 500×99 physical-pixel Save button
rather than a full-width blue banner. A follow-up visual job covers
the bounded provider panel, paired actions and icons.

Limitation: the Java UI and GPUI are different renderers. Sharing
source-verified design tokens achieves consistent visual language but
not literal pixel identity under all OS font, display density, custom
Studio themes, accessibility scale and animation settings. The real
device screenshot and comparison with the user's editor theme remain
the final acceptance criteria.
