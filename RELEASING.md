# Releasing a new version

Updates reach phones through GitHub Releases. The app checks
`https://api.github.com/repos/costinsavu784-web/CarLogbook/releases/latest` once a day
(and when you tap the update icon in the garage), compares the tag with its own version,
and offers to download and install the APK attached to the release.

## Steps

1. In `app/build.gradle.kts`, bump both values:
   - `versionCode` (any higher integer, e.g. 2 -> 3)
   - `versionName` (e.g. "1.1.0" -> "1.2.0")
2. Commit and push to `main`.
3. Tag and push the tag. The tag must be `v` + versionName, or the workflow fails:
   ```
   git tag -a v1.2.0 -m "What changed"
   git push origin v1.2.0
   ```
4. GitHub Actions builds a signed APK and publishes it as release `v1.2.0`
   (about 3 minutes). Watch it at https://github.com/costinsavu784-web/CarLogbook/actions
5. Phones pick it up on their next daily check, or immediately via the update icon.

## Signing key

The release keystore lives at `C:\dev\carlogbook-release.jks` (password in
`C:\dev\carlogbook-release-password.txt`) and as GitHub Actions secrets
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

**Back up the .jks and the password somewhere safe.** Android only installs an update over
an existing app if both are signed with the same key. If the key is lost, users must
uninstall and reinstall, losing their data.

Never commit the keystore to the repository.

## Local signed build

```powershell
$env:KEYSTORE_FILE='C:\dev\carlogbook-release.jks'
$env:KEYSTORE_PASSWORD=(Get-Content C:\dev\carlogbook-release-password.txt -Raw)
$env:KEY_ALIAS='carlogbook'
$env:KEY_PASSWORD=$env:KEYSTORE_PASSWORD
.\gradlew.bat assembleRelease
```

Without those variables, `assembleRelease` produces an unsigned APK.
