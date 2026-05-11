# Modularization plan

## Current status

* `:core` (`kotlin("jvm")`, JDK 17) — **created**. Hosts pure-JVM logic:
  * `com.iptv.core.m3u.M3uParser` + `M3uTrack`
  * `com.iptv.core.text.parseYear`
  Both have their own JUnit tests inside the module; no Android dependency.
  `:app` depends on `:core` via `project(":core")`. The old paths in `:app`
  (`com.iptv.app.data.m3u.M3uParser`, `parseYear` in `AdvancedFilters.kt`)
  still compile via `typealias` / delegation, so consumer code didn't move.
* `:ui`, `:data`, `:domain` — not yet split. Documented below.

Target layout (next steps):

```text
:app          (com.android.application, Hilt entry, UI navigation)
  └── depends on: :ui, :data, :domain, :core

:ui           (com.android.library, Compose, Hilt UI bindings)
  └── depends on: :data, :domain, :core
  └── packages: ui/*

:data         (com.android.library, Room, Retrofit, DataStore, OkHttp)
  └── depends on: :domain, :core
  └── packages: data/api, data/cache, data/db, data/epg, data/m3u, data/prefs, data/reco

:domain       (kotlin("jvm"), no Android deps)
  └── depends on: :core
  └── packages: domain/model, domain/sort

:core         (kotlin("jvm"), pure logic, no Android, no third-party SDKs)
  └── depends on: nothing
  └── packages: core/parser (M3U, year extraction), core/time helpers
```

## Why this layout

* **Pure JVM core/domain** speeds up tests (no Robolectric needed for parser
  logic) and prevents Android types from leaking into business rules.
* **`:data` isolated** means swapping a backend (Stalker, sync, future
  providers) only touches one module — `:ui` and `:app` stay stable.
* **`:ui` separate** unlocks parallel build with `:data` and lets us add a
  TV-specific `:ui-tv` later without rewriting screens.

## Migration order

1. ✅ **Create `:core`** — done. M3U parser + year extraction live here.
2. Create `:domain` module. Move `domain/model/*.kt` and `domain/sort/*.kt`.
   Keep DTO mappers (which need `data/api/*`) in `:data`.
3. Create `:data` module. Move `data/` packages. Hilt module migration: keep
   `AppModule.kt` in `:app` but split into `DataModule` / `NetworkModule` /
   `RoomModule` inside `:data`, exposing only DAOs/repos. Workers in `:app`.
4. Create `:ui` module. Move `ui/` except `MainActivity` and `AppNav`.

## Caveats learned from step 1

* `kotlin("jvm")` modules need the plugin declared in the **root** `plugins {}`
  block with `apply false` so child modules can `id("org.jetbrains.kotlin.jvm")`
  without repeating the version. Otherwise Gradle errors out with
  "plugin already on the classpath with an unknown version".
* JDK 21 dev boxes default Kotlin's JVM target to 21, but `:app` uses 17. Pin
  with `kotlin { jvmToolchain(17) }` in every JVM module to keep targets in
  sync — otherwise Gradle fails the build with "Inconsistent JVM-target".
* `typealias` shims at the old package path let us move types without rewriting
  every call site at once. Delete the shims once all consumers reference the
  new `:core` package.

## What's already prepared

* No package depends on Android types it shouldn't:
  * `M3uParser`, `AdvancedFilters.parseYear` and most of `domain/` already
    have zero Android imports — they're ready to move as-is.
* Hilt scope already uses `@Singleton` consistently, so module-level
  components stay flat.
* No `internal` visibility leaks across packages (`detekt` would flag if
  we introduce them).

## Why this is not done yet

Splitting modules is a 1+ day refactor with cascade effects: Hilt
`@AndroidEntryPoint` boundaries, KSP component layout, Robolectric test
runner per module, R8 rules. We will tackle it as a dedicated PR after
the next release, not bundled with feature work.
