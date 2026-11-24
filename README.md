# SDUI Platform

A production-grade Server-Driven UI platform for Android: a modular Jetpack Compose
SDK, a screen registry with version management, and a visual builder that publishes
to running devices in under a second.

The goal it is built against: **a product manager or designer creates an entire
screen — layout, navigation, API calls, state bindings, theme and actions — in the
dashboard, publishes it, and the Android app renders and executes it immediately,
with no new APK.** Native capabilities (camera, QR, biometrics) stay reachable, but
only through a registry the host app explicitly populates.

```
┌──────────────┐   publish    ┌──────────────┐    SSE     ┌──────────────┐
│  Dashboard   │ ───────────► │   Backend    │ ─────────► │ Android SDK  │
│ (visual      │   drafts &   │ screens,     │  screen.   │ parse →      │
│  builder)    │ ◄─────────── │ versions,    │  published │ bind →       │
└──────────────┘   history    │ flags, A/B   │            │ render       │
                              └──────────────┘            └──────────────┘
```

## Demo

<!-- TODO: record a ~60s screen capture — drag a component onto the canvas in the
     dashboard, then show the emulator updating live — and save it as docs/demo.gif -->
![Editing a screen in the dashboard and watching it update live on the device](docs/demo.gif)

---

## Quick start

```bash
# 1. Backend — screen registry, versions, flags, live-preview stream
cd backend && npm install && npm run dev          # http://localhost:8787

# 2. Dashboard — visual builder
cd dashboard && npm install && npm run dev        # http://localhost:5173

# 3. Android — demo app on an emulator (10.0.2.2 reaches the host)
./gradlew :demo-app:installDebug
```

The backend seeds itself from `demo-app/src/main/assets/sdui/*.json`, so all three
pieces are useful the moment they start. With no backend running the app falls back
to those same bundled screens.

### Using the SDK in your own app

```kotlin
// Application.onCreate
Sdui.initialize(
    context = this,
    config = SduiConfig(baseUrl = "https://sdui.example.com", projectId = "checkout"),
) {
    fallbackScreenSource = AssetScreenSource(this@MyApp)   // offline first launch
}

// Anything the SDK does not ship, the host registers once:
Sdui.registerComponent("rating", RatingRenderer)
Sdui.registerAction("biometricAuth", BiometricHandler)
Sdui.registerNativeCapability("scanQR") { _, _ -> SduiResult.success(scan()) }
```

```kotlin
// Activity
setContent {
    LaunchedEffect(Unit) { Sdui.loadScreen("home") }
    SduiHost(onExit = ::finish)
}
```

That is the entire integration. Every screen after `home` is reached through a
server-described `navigate` action — there is no navigation graph in the app.

---

## Repository layout

| Module | Responsibility | Compose |
|---|---|---|
| `sdui-core` | Domain model, JSON config, `Registry`, `SduiResult`, `UiState`, contracts | — |
| `sdui-parser` | Wire JSON → model, schema validation, forward-compat decoding | — |
| `sdui-state` | Reactive `StateStore`, binding scopes, expression engine, validators | — |
| `sdui-theme` | Server-driven design system → `MaterialTheme`, colour/token resolution | ✓ |
| `sdui-cache` | Memory + disk tiers, TTL, offline-stale serving | — |
| `sdui-network` | `ApiEngine` (verbs, auth, retry, cache policies), screen sources, SSE client | — |
| `sdui-analytics` | Automatic instrumentation, pluggable sinks | — |
| `sdui-navigation` | Runtime back stack: push/replace/pop/popToRoot/modal/bottomSheet | — |
| `sdui-actions` | `ActionRegistry`, dispatcher, 24 built-in handlers, native-capability bridge | — |
| `sdui-renderer` | `ComponentRegistry`, recursive engine, modifier pipeline, animations | ✓ |
| `sdui-components` | 29 built-in components: layout, content, forms, lists, feedback | ✓ |
| `sdui` | Public facade — `Sdui`, `SduiHost`, overlays, screen lifecycle | ✓ |
| `demo-app` | Reference integration with Hilt, a custom component and a native capability | ✓ |
| `backend/` | Node + TypeScript registry: screens, versions, environments, flags, A/B, SSE | — |
| `dashboard/` | React visual builder: canvas, palette, inspector, JSON, versions, theme | — |

`build-logic/` holds the Gradle convention plugins, so every module's build file is
just its dependencies.

---

## What is server-driven

Everything below moves without an app release.

**Layout** — 29 components with a shared modifier vocabulary (padding, margin,
background, corner radius, elevation, width/height, weight, alignment, clickable,
enabled, alpha, rotation, border, gradients).

**Navigation** — `{"type":"navigate","params":{"screen":"detail","mode":"push"}}`.
Modes: `push`, `replace`, `pop`, `popToRoot`, `popTo`, `modal`, `bottomSheet`.

**APIs** — declared per screen; GET/POST/PUT/DELETE/PATCH with headers, query, body,
auth, timeout, retry with backoff, and five cache policies.

**State & binding** — `{{user.name}}`, `{{currency(wallet.balance, 'INR')}}`,
`{{cart.items.length > 0 ? 'Checkout' : 'Empty'}}`. Writes to the store recompose
exactly the readers that changed.

**Forms** — nine input types with server-defined validation rules *and* error copy.

**Lists** — `lazyColumn`, `lazyRow`, `grid`, infinite pagination, pull-to-refresh.

**Themes** — full Material 3 colour scheme, type ramp, spacing scale, shapes, custom
tokens, light/dark, dynamic colour.

**Animations** — `fade`, `slideUp/Down/Start/End`, `scale`, `expand`, `collapse`,
with duration, delay and easing.

**Flags, rollouts and A/B** — percentage buckets, country, app version, premium and
user allow-lists, resolved server-side per request; experiments assign variants
deterministically.

**Analytics** — screen opens, time spent, clicks, impressions, API success/failure
and navigation are emitted automatically; hosts add sinks.

---

## What stays native

By design, the JSON vocabulary can *request* a capability but never implement one.
`scanQR`, `camera` and `openNativeScreen` resolve through `NativeCapabilityRegistry`,
which only the host populates. A payload cannot name a class, reach a method by
reflection, or run code the app did not deliberately expose. An unregistered key
fails loudly instead of silently doing nothing.

---

## Extending it

Adding a component is one registration; there is no `when` over component types
anywhere in the engine, so the SDK itself never changes:

```kotlin
object RatingRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) { /* … */ }
}

Sdui.registerComponent("rating", RatingRenderer)
```

Actions, expression functions, analytics sinks, screen sources, caches and HTTP
interceptors are all the same shape: an interface plus a registration.

---

## Testing

```bash
./gradlew testDebugUnitTest      # 64 JVM tests: expressions, parser, validator, state, navigation
cd backend && npm run typecheck
cd dashboard && npm run typecheck
```

The expression engine, parser, schema validator, state store, validators and
navigation manager are pure Kotlin with no Android dependencies, so they are tested
on the JVM without Robolectric or a device.

---

## Design decisions

The choices that shape the codebase, and why:

| Decision | Why |
|---|---|
| **No DI framework inside the SDK** | `SduiBuilder` wires everything by constructor, so the library drops into Hilt, Koin or no-DI apps alike. The demo app shows the Hilt bridge. |
| **Compose lives in only 4 modules** | Parser, state, expressions, navigation and cache are plain Kotlin, so they are unit-tested on the JVM with no Robolectric and no device. |
| **Registry lookup, never `when (type)`** | Components and actions are registered, not hard-coded, so a host adds new ones without touching the SDK. |
| **`ResolvedNode` between binding and rendering** | Renderers receive already-resolved, stable props, so Compose skips any node whose values did not change. |
| **Native capabilities are an allow-list** | JSON can only *name* a capability; only a handler the host registered runs. No reflection anywhere. |
| **Append-only versions; publish and rollback are pointer moves** | Every published screen can be restored instantly, and history is never rewritten. |
| **SSE, not WebSocket, for live preview** | Traffic is one-way (server → device), SSE runs over plain HTTP, and reconnecting is built in. |

### Bugs that only showed up on a real device

- **Android's ICU regex engine rejects unescaped `}` and `]`.** The same `Regex`
  passed every JVM test but crashed the app when the class was first loaded on a
  device. Fix: escape both brackets in every pattern.
- **`JsonNull` is a `JsonPrimitive` whose `content` is the string `"null"`.** Bindings
  rendered the literal word "null" and `?:` fallbacks never fired. Fix: every
  accessor filters `JsonNull` explicitly.
- **SSE sockets leaked on cancellation.** The read loop ran before `awaitClose`
  registered, so cancelled flows kept connections open and the server over-counted
  devices. Fix: run the read loop in a launched coroutine.

## Known limits

No offline write queue, approximate (CSS-based) canvas preview, A/B metrics as raw
counts, and a single-node backend. Each one, and where it would be fixed, is in
[`docs/ARCHITECTURE.md` §12](docs/ARCHITECTURE.md#12-known-limits).

---

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — module boundaries, render pipeline,
  the decisions behind them and their trade-offs.
- [`docs/SCHEMA.md`](docs/SCHEMA.md) — complete JSON reference for screens, nodes,
  modifiers, styles, actions, APIs, validation and themes.

---

## License

[MIT](LICENSE)
