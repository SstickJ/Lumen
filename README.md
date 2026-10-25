# Lumen: AI calorie tracker (Android)

Kotlin + Jetpack Compose. Photo → calories with a vision model, barcode scan, editable ingredients,
exercise log (Health Connect / Mi Fitness), liquid-glass UI, everything configurable at runtime.

## Build
**Android Studio:** open this folder, let it sync, Run. (It generates the Gradle wrapper for you.)
**Command line:** JDK 17 + Android SDK + Gradle 8.9: `gradle :app:assembleDebug`
**No local setup:** push to GitHub; `.github/workflows/build.yml` runs the unit tests and uploads the APK as an artifact.

APK: `app/build/outputs/apk/debug/app-debug.apk`

## First run
1. Settings → AI meal recognition → pick provider, paste API key → **Test AI**.
2. Settings → Goal & energy → enter your stats (or a manual target).
3. Home → camera button → Meal photo / Barcode.
4. Mi Fitness: enable Health Connect sync inside Mi Fitness, then Exercise → Grant access.

## Tests
`app/src/test/.../CoreTest.kt` covers the pure logic (energy maths, status message, AI request/response
parsing for all 3 providers, Open Food Facts + USDA parsing, settings import/export). Run: `gradle :app:testDebugUnitTest`.

## Layout
- `core/` pure Kotlin logic (no Android classes): settings, energy, AI + food API parsing
- `data/` Room database + settings store · `net/` OkHttp · `health/` Health Connect
- `ui/` Compose screens and the glass components · `AppViewModel.kt` ties it together
