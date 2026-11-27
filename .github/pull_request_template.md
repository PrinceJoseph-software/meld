## What & why
<!-- What does this change, and what problem does it solve? Link the issue: "Closes #123". -->

## How it was tested
<!-- Check what you ran. -->
- [ ] `./gradlew testDebugUnitTest` (Android)
- [ ] `cd web && npm test && npm run typecheck && npm run build` (web)
- [ ] `cd web && npm run test:api` (server, needs Java + firebase-tools)
- [ ] `cd rules-tests && npm test` (security rules)
- [ ] Tried it by hand (describe below)

## Screenshots
<!-- For UI changes: before / after, light and dark if relevant. -->

## Checklist
- [ ] No secrets, keys, `google-services.json` or service-account files added
- [ ] New user-facing text is in `strings.xml` (Android) so it can be translated
- [ ] Docs updated if behaviour or setup changed
