# Security policy

## Reporting a vulnerability

Please do not open a public issue for a suspected security vulnerability. Use GitHub's private vulnerability reporting for this repository when available, or contact the repository owner privately with reproduction steps, affected version, and impact.

Aergis processes camera frames on-device. Camera frames, gesture landmarks, accessibility state, and pointer telemetry must not be uploaded or written to persistent storage unless a future design explicitly documents and protects that behavior.

## Signing and secrets

- Release signing material is supplied through `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.
- The debug build uses the ephemeral Android debug keystore and must not depend on a repository-tracked key.
- Do not commit `.jks`, `keystore.properties`, credentials, generated APKs, or MediaPipe model downloads.
- The previously tracked preview keystore must be treated as compromised: rotate it before distributing any release or trusted build, and remove it from repository history using an approved secret-removal process.

## Local verification

```sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The build verifies the downloaded model's SHA-256 before packaging it. Dependency and workflow security checks run in GitHub Actions.
