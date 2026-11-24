# Screen JSON reference

Everything the Android SDK understands. Unknown keys are ignored and unknown enum
values fall back to their default, so a newer payload never breaks an older build.

---

## Screen

```json
{
  "id": "home",
  "schemaVersion": 1,
  "version": "12",
  "title": "Home",
  "background": "background",
  "theme": "brand",
  "scrollable": false,

  "state":    { "ui": { "loading": false } },
  "apis":     { "transactions": { … } },
  "onLoad":   [ { "type": "apiCall", "params": { "api": "transactions" } } ],
  "onAppear": [],
  "onBack":   [],
  "appBar":   { "title": "Hello, {{user.firstName}}", "showBack": false },
  "root":     { "type": "column", "children": [] },
  "metadata": { "author": "ada" }
}
```

| Field | Type | Notes |
|---|---|---|
| `id` | string | Required. Matches the id used by `loadScreen` / `navigate`. |
| `schemaVersion` | int | Wire-format version. A higher value logs a warning, never fails. |
| `version` | string | Content version, assigned by the dashboard on publish. |
| `state` | object | Seed values for this screen's store. Merged without overwriting existing keys on refresh. |
| `apis` | map | Named requests this screen may invoke. |
| `onLoad` | action[] | Runs once when the definition arrives. |
| `onAppear` | action[] | Runs when the screen becomes visible. |
| `onBack` | action[] | Intercepts the back gesture; when present, the SDK does not pop automatically. |
| `scrollable` | bool | Wraps the root in a vertical scroll container. |
| `appBar` | object | `title`, `subtitle`, `showBack`, `centered`, `background`, `contentColor`. Titles support bindings. |

---

## Node

```json
{
  "type": "text",
  "id": "greeting",
  "visible": "{{user.isPremium}}",
  "modifier": { "padding": 16, "width": "match_parent" },
  "style":    { "typography": "titleMedium", "textColor": "primary" },
  "animation": { "enter": "slideUp", "durationMs": 300 },
  "props":    { "text": "Hi {{user.firstName}}" },
  "actions":  { "onClick": { "type": "navigate", "params": { "screen": "profile" } } },
  "repeat":   { "items": "{{transactions}}", "as": "item", "indexAs": "i" },
  "analytics": { "impressionEvent": "greeting_seen" },
  "children": []
}
```

`type` is resolved through the component registry at render time — it is not an
enum, so a host-registered `"rating"` works with no SDK change.

`visible` accepts `true`/`false` or an expression. A falsy guard skips the subtree.

`repeat` renders the node once per element of a bound collection, exposing `as` and
`indexAs` inside the subtree. `emptyNode` renders when the collection is empty.

---

## Modifier

Applied in a fixed order regardless of JSON key order:

```
margin → size → weight → aspectRatio → align → zIndex → shadow
       → clip → background → border → scroll → clickable
       → alpha/rotate/scale → padding → testTag/semantics
```

| Key | Accepted forms |
|---|---|
| `padding`, `margin` | `16` · `{"all":16}` · `{"horizontal":16,"vertical":8}` · `{"start":4,"top":8,"end":4,"bottom":8}` (`left`/`right` alias `start`/`end`) |
| `width`, `height`, `size` | `120` · `"120dp"` · `"match_parent"` · `"wrap_content"` · `"50%"` |
| `minWidth`, `minHeight`, `maxWidth`, `maxHeight` | number (dp) |
| `weight` | number — share of the parent row/column |
| `alignment` | `topStart` `topCenter` `topEnd` `centerStart` `center` `centerEnd` `bottomStart` `bottomCenter` `bottomEnd` |
| `background` | colour (see below) |
| `backgroundGradient` | `{"colors":["#A","#B"],"type":"vertical\|horizontal\|radial\|sweep"}` |
| `cornerRadius` | `12` · `{"topStart":12,"topEnd":12}` |
| `elevation` | number (dp) |
| `border` | `{"width":1,"color":"outline","cornerRadius":12}` |
| `aspectRatio` | number |
| `alpha`, `rotation`, `scale` | number |
| `clip` | bool, default `true` |
| `enabled` | bool, default `true` |
| `scrollable` | bool |
| `zIndex` | number |
| `testTag`, `contentDescription` | string |

### Colours

Three accepted forms, resolved in order:

1. **Literal** — `#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB`, or `transparent`, `black`, `white`, `red`, …
2. **Material role** — `primary`, `onSurfaceVariant`, `surfaceContainer`, `error`, …
3. **Custom token** — anything in the theme's `tokens` map, e.g. `brandGold`.

`@` and `$` prefixes are accepted (`@primary`). Unknown values fall back to the
component's default rather than to an arbitrary colour.

---

## Style

| Key | Values |
|---|---|
| `typography` | Material role: `displayLarge` … `labelSmall` |
| `textColor`, `tint`, `containerColor`, `contentColor` | colour |
| `fontSize`, `lineHeight`, `letterSpacing` | number (sp) |
| `fontWeight` | `thin` `light` `normal` `medium` `semibold` `bold` `extrabold` `black` |
| `italic` | bool |
| `textAlign` | `start` `center` `end` `justify` |
| `decoration` | `none` `underline` `lineThrough` |
| `maxLines` | int |
| `overflow` | `clip` `ellipsis` `visible` |
| `variant` | component variant, e.g. `filled` `tonal` `outlined` `text` `elevated` |

---

## Components

**Layout** — `column` `row` `box` `card` `surface`
`column`/`row` props: `spacing`, `justify` (`start` `center` `end` `spaceBetween` `spaceAround` `spaceEvenly`), `align` (`start` `center` `end` `stretch`).

**Content** — `text` (`text`) · `image` (`url`, `contentScale`, `alt`) · `icon` (`name`, `size`) · `spacer` (`width`, `height`) · `divider` (`thickness`, `orientation`, `color`)

**Interaction** — `button` (`text`, `variant`, `leadingIcon`, `trailingIcon`, `loading`, `enabled`) · `iconButton` (`icon`) · `chip` (`text`, `icon`)

**Collections** — `lazyColumn` `lazyRow` `grid`
Props: `items` (binding to an array), `spacing`, `contentPadding`, `columns` / `minItemWidth` (grid), `pullToRefresh`, `refreshing`, `loadingMore`, `loadMoreThreshold`.
The node's first child is the item template; `item` and `index` are in scope.
Events: `onRefresh`, `onLoadMore`.

**Forms** — `textField` `password` `number` `otp` `checkbox` `switch` `radioGroup` `dropdown` `datePicker`
Props: `name`, `label`, `placeholder`, `helperText`, `path` (state path, defaults to `form.<name>`), `form` (form id), `rules`, `options` (`[{label,value}]`), `maxLength`, `keyboard`, `singleLine`, `maxLines`.
Events: `onChange`, `onComplete` (otp).

**Feedback** — `progress` (`variant`, `value`) · `skeleton` (`lines`, `lineHeight`) · `badge` (`text`) · `empty` (`icon`, `title`, `message`)

**Aliases** — `vstack`→`column`, `hstack`→`row`, `stack`→`box`, `label`→`text`, `img`→`image`, `list`→`lazyColumn`, `carousel`→`lazyRow`, `input`→`textField`, `select`→`dropdown`, `toggle`→`switch`, `loader`→`progress`, `shimmer`→`skeleton`.

---

## Actions

```json
{
  "type": "apiCall",
  "params": { "api": "submitExpense" },
  "if": "{{form.valid}}",
  "then":    [ { "type": "showToast", "params": { "message": "Saved" } } ],
  "else":    [ { "type": "showToast", "params": { "message": "Fix the form" } } ],
  "onError": [ { "type": "showDialog", "params": { "title": "Failed" } } ],
  "debounceMs": 500,
  "ignoreFailure": false
}
```

`then` receives the action's output as `{{result}}`; `onError` receives `{{error}}`
and `{{errorCode}}`.

| Type | Params |
|---|---|
| `navigate` | `screen`, `mode` (`push` `replace` `pop` `popToRoot` `popTo` `modal` `bottomSheet`), `args`, `target`, `result`, `clearStack` |
| `back` / `goBack` / `pop` | `result` |
| `apiCall` | `api` (key in `screen.apis`) or `request` (inline `ApiSpec`), `body`, `query`, `append` |
| `setState` | `path` + `value`, or `values` (map); `scope`: `screen` \| `global` |
| `toggleState`, `clearState` | `path`, `scope` |
| `showToast` | `message`, `duration` (`short` `long`), `snackbar`, `actionLabel`, `action` |
| `showDialog` | `title`, `message`, `content` (node), `confirmLabel`, `cancelLabel`, `confirmAction`, `cancelAction`, `dismissible`, `destructive` |
| `bottomSheet` | `content` (node) or `screen`, `title`, `fullHeight` |
| `dismiss` | `target`: `dialog` \| `sheet` \| `keyboard` |
| `share` | `text`, `subject`, `mimeType`, `title` |
| `copyClipboard` | `text`, `label`, `confirmMessage` |
| `openUrl` | `url` |
| `openNativeScreen`, `scanQR`, `camera` | `capability` plus anything the host handler expects |
| `analytics` | `event`, `properties` |
| `validateForm` | `form`, `errorPath` (default `errors`) |
| `submitForm` | `form` + any `apiCall` params |
| `refreshScreen` | `force` |
| `setTheme` | `theme` (id) |
| `sequence` | `actions` |
| `delay` | `ms` |
| `noop` | — |

---

## APIs

```json
"apis": {
  "transactions": {
    "url": "{{env.apiBase}}/transactions",
    "method": "GET",
    "headers": { "X-Trace": "{{env.userId}}" },
    "query":   { "page": "{{ui.page}}" },
    "body":    null,
    "auth":    { "type": "bearer", "credential": "default" },
    "timeoutMs": 15000,
    "retry":   { "maxAttempts": 3, "backoffMs": 400, "multiplier": 2 },
    "cache":   { "policy": "staleWhileRevalidate", "ttlSeconds": 120 },

    "stateKey":     "transactions",
    "responsePath": "items",
    "loadingKey":   "ui.loading",
    "errorKey":     "ui.error",

    "onSuccess": [],
    "onError":   [],
    "pagination": { "pageParam": "page", "pageSize": 20, "itemsPath": "items" }
  }
}
```

Every string is binding-resolved before the request is built.

- `method` — `GET` `POST` `PUT` `DELETE` `PATCH`
- `auth.type` — `none` `bearer` `basic` `header` `query` `custom`. The spec says *how*
  to authenticate; the token comes from `SduiConfig.authTokenProvider` at runtime, so
  credentials never appear in screen JSON.
- `cache.policy` — `networkOnly` `cacheFirst` `networkFirst` `cacheOnly` `staleWhileRevalidate`
- `retry.retryOnStatus` defaults to `[408, 425, 429, 500, 502, 503, 504]`.

---

## Validation rules

```json
"rules": [
  { "type": "required",  "message": "Enter an amount" },
  { "type": "minLength", "value": 8 },
  { "type": "regex",     "value": "^[A-Z]{2}\\d{4}$", "message": "Format: AB1234" }
]
```

`required` `email` `phone` `url` `numeric` `regex` `minLength` `maxLength` `min`
`max` `equals` `matchesField`.

Every rule except `required` passes on an empty value, so optional fields are not
rejected for being blank. The first failing rule supplies the message; errors are
written to `errors.<name>` and picked up automatically by the field.

---

## Expressions

```
{{ user.name }}
{{ wallet.balance > 1000 }}
{{ user.isPremium ? 'Premium' : 'Free' }}
{{ user.nickname ?: user.firstName }}
{{ currency(wallet.balance, 'INR') }}
{{ items.length }} · {{ items[0].title }}
Hello {{ user.firstName }}, you have {{ cart.items.length }} items
```

**Operators** — `? :` · `?:` `??` · `||` `&&` · `==` `!=` · `<` `<=` `>` `>=` · `+` `-` `*` `/` `%` · `!` · `in` · `matches`

**Pseudo-properties** — `.length` `.size` `.count` `.isEmpty` `.isNotEmpty` `.first` `.last` `.keys` `.values`

**Functions** — `upper` `lower` `trim` `capitalize` `concat` `substring` `replace`
`split` `truncate` `initials` `padStart` · `number` `round` `abs` `min` `max` `sum`
`percent` `currency` `compact` · `length` `join` `take` `contains` `isEmpty` ·
`default` `coalesce` `if` `not` `bool` `string` · `now` `formatDate`

Hosts add their own with `Sdui.registerFunction("maskCard") { … }`.

**Namespaces** — `state` / `screen`, `global` / `session`, `env` / `config`, `local`.
Use them to disambiguate when a screen key shadows a global one.

A single-binding template returns the underlying JSON value, so `"items": "{{list}}"`
yields a real array. A mixed template returns a string. A broken expression yields
an empty string — never an exception.

---

## Theme

```json
{
  "id": "brand",
  "mode": "system",
  "useDynamicColor": false,
  "light":  { "primary": "#4C5BD4", "onPrimary": "#FFFFFF", "background": "#F7F8FC" },
  "dark":   { "primary": "#B7BDFF", "background": "#101116" },
  "typography": { "titleMedium": { "fontSize": 17, "fontWeight": "semibold" } },
  "spacing":    { "xs": 4, "sm": 8, "md": 16, "lg": 24 },
  "shapes":     { "small": 10, "medium": 16, "large": 22 },
  "tokens":     { "brandGold": "#E9A23B", "success": "#1FA971", "muted": "onSurfaceVariant" }
}
```

Any role the server omits keeps its Material baseline, so a partial theme is always
valid. Tokens may point at other tokens or at roles.

---

## Animations

```json
"animation": { "enter": "slideUp", "exit": "fade", "durationMs": 300, "delayMs": 0, "easing": "standard" }
```

`enter`/`exit` — `none` `fade` `slideUp` `slideDown` `slideStart` `slideEnd` `scale`
`expand` `collapse` `shimmer`.
`easing` — `standard` `linear` `easeIn` `easeOut` `easeInOut` `bounce`.

Combined with a bound `visible`, this is how a section animates in when state
changes:

```json
{ "type": "card", "visible": "{{form.billable}}",
  "animation": { "enter": "expand", "exit": "collapse", "durationMs": 220 } }
```
