# Location Facts

An Android app with one job: open it, and it tells you a few genuinely interesting,
**sourced** things about the exact spot you are standing on.

It gets a location fix, reverse-geocodes it into a place description, and asks Claude
(`claude-opus-5`) to research that spot with the Anthropic API's server-side **web search**
tool. Facts come back with the URLs the model actually read, taken from the API's own
citation blocks — not from URLs typed into the answer text.

## How it works

```
MainActivity ──> FactsViewModel ──> LocationSource   (platform LocationManager, one fix)
                                ──> PlaceResolver    (Geocoder → PlaceContext)
                                ──> ClaudeFactsService
                                        └─ POST /v1/messages, model claude-opus-5,
                                           tool: web_search_20260209 (localized to the
                                           user's city/region/country/timezone)
                                        └─ FactsParser: TITLE/BODY blocks + the citations
                                           the API attached to each span of text
```

| Piece | File |
| --- | --- |
| Claude call, prompt, refusal + `pause_turn` handling | `app/src/main/java/com/ivi3/locationfacts/ai/ClaudeFactsService.kt` |
| Response parsing and source attribution | `ai/FactsParser.kt` |
| Location fix without Play Services | `location/LocationSource.kt` |
| Reverse geocoding | `location/PlaceResolver.kt` |
| API key at rest (AES/GCM, Android Keystore) | `data/ApiKeyStore.kt` |
| Compose UI | `ui/FactsScreen.kt` |

Design choices worth knowing about:

* **Nothing is answered from memory.** The system prompt requires web search, and the
  response carries `searchCount` — how many searches the server actually ran. If it is
  zero, the UI says up front that nothing in the answer is sourced.
* **A fact with no citation is labelled as such**, in red, rather than shown as if a
  source existed.
* **Effort is set to `medium`** (`BetaOutputConfig.Effort.MEDIUM`) to keep the wait to
  something a person standing on a street corner will tolerate. Raise it to `high` or
  `xhigh` in `ClaudeFactsService` if you would rather wait longer for deeper digging.
* **Server-side refusal fallbacks** are enabled (`fallbacksDefault()` with the
  `server-side-fallback-2026-07-01` beta). Local trivia is not a refusal risk, but this
  means a declined request is routed to a fallback model rather than failing.
* **No Play Services dependency.** `LocationManager` with the FUSED provider where the
  platform has it (API 31+), then GPS, then network, then a stale cached fix rather than
  nothing.

## Build and run

There is no Gradle wrapper committed. Either open the project in Android Studio (it
supplies Gradle), or generate one once:

```sh
cd android-location-facts
gradle wrapper --gradle-version 8.11.1
./gradlew :app:installDebug
```

Requirements: JDK 17+, Android SDK with API 35 (`compileSdk = 35`), `minSdk = 26`.

On first launch the app asks for an Anthropic API key (from
[console.anthropic.com](https://console.anthropic.com)) and for location permission. The
key is encrypted with an AES-256-GCM key held in the device keystore and stored as
ciphertext in the app's private preferences.

Run the parser tests with:

```sh
./gradlew :app:testDebugUnitTest
```

## The API key problem — read this before shipping it to anyone else

This app calls the Anthropic API **directly from the phone**, which means a usable API key
lives on the device. Keystore encryption protects it from someone reading the app's files;
it does not protect it from whoever is holding the unlocked phone or attaching a debugger,
and it does not stop that key being used to run up your bill.

That is an acceptable trade for a build you install on your own phone with a key you can
rotate. For anything you hand to other people, put a small server in front:

1. The app authenticates to your server (Play Integrity, Firebase Auth, whatever you use).
2. Your server holds the Anthropic key, calls `/v1/messages`, and applies your own per-user
   rate limits.
3. Only `ClaudeFactsService` changes — swap the `AnthropicOkHttpClient` for a call to your
   endpoint; the prompt, parsing and UI stay as they are.

## What the model is told

The prompt is in `ClaudeFactsService.SYSTEM_PROMPT`. In short: search before answering;
every fact must come from a result actually read; never invent a date, name, quotation or
source; prefer this block over this country; if the searches turn up little, return fewer
facts and say so. The requested output shape is `TITLE:` / `BODY:` pairs with an optional
final `NOTE:` line, which is what `FactsParser` reads.

Costs are per request and depend on how much the searches return; `claude-opus-5` is
$5/$25 per million input/output tokens, plus the web search tool's own per-search charge.
