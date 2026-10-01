# Doomscroll Duel (Android)

Currently contains the design system only: `app/src/main/kotlin/com/doomscrollduel/core/designsystem`.

Gradle scaffolding (Milestone 1) is not in this folder yet. The design system needs:

- `androidx.compose:compose-bom`, with `ui`, `foundation`, `animation`, `ui-tooling-preview` (and `ui-tooling` for debug)
- `androidx.compose.ui:ui-text` (variable font support, API 26+)
- No `material3` and no `material-icons`: the theme and icons are custom.
- Unit tests: `junit`.
- `namespace` must be `com.doomscrollduel` (the code imports `com.doomscrollduel.R`).

Fonts (`res/font`) are SIL OFL 1.1, licences in `licenses/`.
