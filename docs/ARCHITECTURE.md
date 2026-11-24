# Architecture

This document covers the decisions that shape the platform, and what each one costs.

---

## 1. Module boundaries

The SDK is twelve Gradle modules. The split is not cosmetic — it enforces
dependency direction and keeps most of the logic testable without a device.

```
                        ┌───────────┐
                        │ sdui-core │  models, Registry, SduiResult, contracts
                        └─────┬─────┘
        ┌───────────┬─────────┼──────────┬───────────┬────────────┐
   sdui-parser  sdui-state  sdui-theme  sdui-cache  sdui-analytics
                     │                     │
                     │              ┌──────┴──────┐
                     │              │ sdui-network│
                     │              └──────┬──────┘
              sdui-navigation              │
                     └────────┬────────────┘
                        ┌─────┴──────┐
                        │sdui-actions│
                        └─────┬──────┘
                        ┌─────┴──────┐
                        │sdui-renderer│
                        └─────┬──────┘
                       ┌──────┴───────┐
                       │sdui-components│
                       └──────┬───────┘
                          ┌───┴───┐
                          │ sdui  │  public facade
                          └───────┘
```

Two rules do the work:

**Compose is confined to four modules** (`theme`, `renderer`, `components`, `sdui`).
The parser, state store, expression engine, navigation manager, cache, network and
action handlers are plain Kotlin, so they run in a JVM unit test in milliseconds.
That is why 64 tests need no Robolectric and no emulator.

**Nothing depends upward.** The API engine does not know about actions; the action
layer calls it and owns state writes and callbacks. Without that split, `ApiSpec`
would need to dispatch `onSuccess` actions and the two modules would be circular.

### Trade-off

Twelve modules cost configuration time and make a small change touch more files
than a single-module SDK would. In exchange, a consumer can depend on
`sdui-parser` alone to validate screens in a CI job, and the build parallelises.
For a library expected to outlive several apps, that trade is worth taking; for a
one-app SDK it would not be.

---

## 2. The render pipeline

`RenderNode` is the whole engine. Per node:

```
repeat  →  visible  →  resolve bindings  →  animate  →  registry lookup  →  renderer
```

1. **repeat** expands a collection binding into N siblings, each with `item`/`index`
   in scope. It produces siblings rather than a wrapper, so the parent's layout
   (Column, Row, LazyColumn item) still governs arrangement.
2. **visible** evaluates a literal or an expression. A false guard skips the subtree
   entirely; it is not rendered invisibly.
3. **resolve** substitutes `{{…}}` into `props`, producing a `ResolvedNode`.
4. **animate** wraps in `AnimatedVisibility` only when the node declares an animation.
5. **registry lookup** is a hash-map hit. There is no `when (node.type)` anywhere.

### Why `ResolvedNode` exists

Compose skips a composable when its parameters are `equals` to the previous ones.
If renderers received the raw `SduiNode` plus the binding scope, every node in the
tree would recompose on any state write, because the scope changed.

Resolution happens once per node in `remember(node, binding)`, and the leaf receives
a `ResolvedNode` whose `props` are already substituted. A text node bound to
`{{user.name}}` therefore skips recomposition when an unrelated `ui.loading` flag
flips, because its resolved props are unchanged.

### Cost

Resolution is a tree walk over each node's props on every scope change. For screens
in the hundreds of nodes this is far cheaper than the recomposition it prevents. For
a screen with thousands of nodes it would not be, which is why `SchemaValidator`
rejects trees above 5,000 nodes and tells the author to paginate.

Style and modifier are resolved only in their colour slots, not wholesale — dynamic
colours are useful, dynamic padding almost never is, and resolving the entire spec
on every state change would cost more than it buys.

---

## 3. Expressions

`{{ }}` bindings are compiled to an AST once, cached by source text, and evaluated
by a tree walk.

The alternative — regex substitution — cannot express `cart.total > 0 ? 'Pay' : 'Add items'`,
and a JavaScript engine would ship an interpreter and a remote-code-execution
surface. The AST is neither: the grammar is fixed, function calls resolve through a
map the host controls, and there is no way to reach a class or method.

**Evaluation is total.** A missing path, a type mismatch, a division by zero and a
syntax error all yield `null`. A broken binding degrades to an empty label; it never
crashes a screen. Syntax errors are cached as failures so a bad expression is parsed
once, not on every frame.

`JsonNull` is treated as absent throughout. This looks like a detail and is not:
`JsonNull` is a `JsonPrimitive` whose `content` is the string `"null"`, so without
explicit filtering an absent value renders as the word "null" and `?:` fallbacks
never fire. Two unit tests pin this down.

---

## 4. State

One reactive `JsonObject` per navigation entry, plus one global store.

Typed view models are not an option: the shape of a screen's state is decided by the
server at runtime. Writes are copy-on-write along the mutated path only, and each
write publishes a new immutable snapshot.

Scope layering, most specific first:

| Layer | Source | Example |
|---|---|---|
| locals | `repeat` aliases, action results | `{{item.title}}`, `{{response.id}}` |
| screen | this entry's store | `{{form.email}}` |
| global | session store | `{{user.name}}` |
| env | config, read-only | `{{env.apiBase}}` |

Stores are keyed by **navigation entry**, not screen id, so pushing `detail` twice
gives each copy independent state — what a master/detail stack needs.

A background refresh merges only keys the store does not already have, so a
re-fetch never wipes a half-filled form.

---

## 5. Actions

`ActionDispatcher` owns everything common to all actions — binding substitution,
`if`/`else` guards, debounce, `then`/`onError` chaining, analytics — so a handler
implements one effect and nothing else. Adding an action is a few lines.

Handlers cannot call composables. Anything needing UI (`showDialog`, `bottomSheet`,
`showToast`) emits a `UiEffect`, and one collector in the host turns effects into
Compose. That is what keeps handlers plain suspend functions and unit-testable.

### The security boundary

`scanQR`, `camera` and `openNativeScreen` resolve through `NativeCapabilityRegistry`.
The SDK ships the *vocabulary*, the host ships the *implementation* along with its
permissions and consent. Nothing is dispatched by class name or reflection, so a
malicious payload cannot reach code the app did not deliberately expose. An
unregistered key returns an explicit error rather than failing silently, so a typo
surfaces in QA instead of in production.

---

## 6. Forms

Validation rules live on the field node, where an author expects them. The
`validateForm` action lives elsewhere in the tree. Coupling those through the JSON
would mean duplicating rules in two places.

Instead, fields register with a `FormRegistry` for as long as they are composed, and
the action asks the registry what to validate. Registration is scoped to composition,
so a field scrolled out of a lazy list stops being validated — which matches what
the user can actually see and submit.

Rules *and* error copy come from the server, so tightening a password policy or
rewording an error ships without an app release.

---

## 7. Network and caching

The engine deals only in already-resolved specs: the action layer substitutes every
`{{binding}}` before calling in. That keeps the engine free of state and expression
concerns and makes it trivially testable.

Caching is handled by the SDK, not by OkHttp's HTTP cache, because the SDK needs
*offline-first* semantics — serve a stale screen indefinitely when there is no
connectivity — which HTTP freshness rules cannot express.

Five policies: `networkOnly`, `cacheFirst`, `networkFirst`, `cacheOnly`,
`staleWhileRevalidate`. Memory sits in front of disk; reads promote, writes go to
both, so a cold start serves the last known screen instantly.

---

## 8. Dependency injection

The SDK builds its own object graph with explicit constructor wiring rather than
requiring Hilt.

An SDK that mandates a DI framework forces that framework on every consumer. The
engine is assembled by `SduiBuilder` and works identically in a Hilt app, a Koin app
or an app with no DI at all. Hosts that use Hilt expose the pieces they need with a
small module — the demo app's `SduiModule` does exactly that, and `SessionViewModel`
shows native MVVM code and server-driven screens sharing the same global store.

Registration happens *inside* `initialize`, before anything is wired, so the first
render already sees every custom component. Registering after start-up would leave a
frame where a host component renders as "unknown".

---

## 9. Version management

Every save is an immutable row in `versions`. Publishing writes a pointer in
`deployments (project, screen, environment) → version`.

Rollback is therefore the same operation as publish, with an older row — a pointer
move, never a data migration, and never a rewrite of history. Promoting dev → prod is
also the same operation. The audit trail is complete because nothing is ever mutated
in place.

Screen resolution order on a request:

```
explicit ?version=   →   active A/B assignment   →   environment deployment
                                                  →   newest draft (dev only)
```

The dev fallback means a newly created screen is renderable immediately, without a
publish step, which is what makes the builder feel live.

---

## 10. Live preview

Server-Sent Events, not WebSocket. The traffic is strictly one-way, SSE survives
proxies that block upgrades, and reconnection is a `while (true)` loop rather than a
protocol state machine.

Two paths share one transport:

- **`preview/push`** — the canvas sends the working tree on every edit (debounced
  350 ms). Nothing is committed to history; only devices watching that environment
  re-render.
- **`publish`** — commits a version and broadcasts it to everyone on the environment.

A republished screen is applied **in place, preserving state**, so a designer
tweaking padding does not reset the screen a tester is looking at.

One subtlety worth recording: the client's read loop runs in its own coroutine so
that `awaitClose` is registered immediately. An earlier version blocked inside the
read before reaching `awaitClose`, so cancelling the flow left the socket parked and
the server kept counting the device as connected — the dashboard showed twelve
devices for one emulator. Cancelling the OkHttp call is what actually unblocks the
read.

---

## 11. Forward compatibility

The server moves faster than the client; that is the entire premise. So an old app
must survive a newer payload:

| Risk | Mitigation |
|---|---|
| New JSON fields | `ignoreUnknownKeys` |
| New enum values | `coerceInputValues` — falls back to the property default |
| New component type | Registry miss renders nothing (debug builds show a marker) |
| New action type | Dispatcher logs, emits analytics, runs `onError` |
| Schema version bump | `schemaVersion` compared, warning recorded |
| Malformed payload | Every parser entry point returns `SduiResult`, never throws |
| Hostile nesting | Depth capped at 64, node count at 5,000 |

The consequence: a screen using one component an old build does not know still
renders everything else. Partial degradation, never a blank screen.

---

## 12. Known limits

Honest accounting of what this does not do yet:

- **No offline write queue.** `apiCall` failures surface to the screen; there is no
  durable outbox. Add one at the `ApiEngine` seam.
- **Preview fidelity is close, not exact.** The dashboard canvas re-implements the
  modifier pipeline in CSS. Flex semantics, text metrics and font rendering differ
  from Compose. The device remains the source of truth; the canvas is for authoring.
- **A/B metrics are event counts.** `experiments/:id/metrics` counts events by name.
  Statistical significance is not computed — wire the analytics sink to a real
  product-analytics tool for that.
- **Auth is a token provider.** The SDK never stores or refreshes credentials; that
  is the host's job by design, but it means the host must handle 401 refresh.
- **The backend is single-node.** SSE clients live in process memory, so multiple
  instances need a shared bus (Redis pub/sub) before horizontal scaling.
- **No JSON-schema export.** `SchemaValidator` encodes the rules in Kotlin; a
  generated JSON Schema would let non-SDK tooling validate payloads too.
