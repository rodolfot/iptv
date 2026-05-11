# Publishing TartaTV to F-Droid

This file documents the metadata layout and the steps to submit TartaTV to
the F-Droid public repository (`fdroiddata`).

## What is in this repo

Everything F-Droid needs to build TartaTV from source is committed:

* `fastlane/metadata/android/<locale>/` — store-listing assets in
  Triple-T format. F-Droid picks these up automatically when the manifest
  declares `Metadata: Fastlane`.
  * `title.txt` (≤ 50 chars)
  * `short_description.txt` (≤ 80 chars)
  * `full_description.txt` (≤ 4000 chars)
  * `changelogs/<versionCode>.txt`
* `app/src/main/res/drawable/app_banner.xml` — already used as feature
  graphic candidate.
* No proprietary dependencies, no Google Play Services, no Firebase, no
  analytics SDK — required by F-Droid's inclusion policy.

## fdroiddata manifest

When you open the inclusion PR against
[`fdroiddata`](https://gitlab.com/fdroid/fdroiddata), create
`metadata/com.iptv.app.yml` with the following content (adjust the
`Repo`/`CurrentVersion` lines to the values at the time of submission):

```yaml
Categories:
  - Multimedia
License: <pick the license you ship; if none yet, add a LICENSE file first>
AuthorName: Rodolfo Tartarotti
AuthorEmail: tartarotti.rl@gmail.com
SourceCode: https://github.com/<owner>/iptv
IssueTracker: https://github.com/<owner>/iptv/issues
Changelog: https://github.com/<owner>/iptv/blob/main/CHANGELOG.md

AutoName: TartaTV

RepoType: git
Repo: https://github.com/<owner>/iptv.git

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

## Pre-submission checklist

* [ ] Pick and commit a license file at the repo root (Apache-2.0 or MIT
      are the most painless choices for F-Droid).
* [ ] Make sure `versionCode` and `versionName` in `app/build.gradle.kts`
      match the git tag F-Droid will build (`v1.0.0`).
* [ ] Tag a release: `git tag v1.0.0 && git push --tags`.
* [ ] Confirm the build is reproducible from the tag with no network
      side-effects beyond Gradle/Maven repos:
      `./gradlew :app:assembleRelease` from a clean checkout.
* [ ] `./gradlew detekt` is clean.
* [ ] `./gradlew :app:testDebugUnitTest` is green.
* [ ] No proprietary SDKs in `app/build.gradle.kts`. Run
      `./gradlew :app:dependencies | grep -iE 'firebase|gms|crashlytics|sentry'`
      and verify there is no match.

## Submitting

1. Fork `fdroiddata` on GitLab.
2. Add the manifest above.
3. Open a merge request titled `New app: TartaTV (com.iptv.app)`.
4. F-Droid maintainers run their own build against the tag and will reply
   with build logs if anything fails.

## After acceptance

* F-Droid rebuilds on every tag matching `UpdateCheckMode: Tags`.
* The in-app auto-updater (GitHub Releases) keeps working in parallel for
  users who side-loaded the APK from Releases. F-Droid users get updates
  via the F-Droid client instead.
