---
name: add-frontend-page
description: Use when adding a new frontend page or feature — creating the HTML file, the IIFE-wrapped JS module, adding API wrappers in api.js, registering in navigation.js, and cache-busting via ?v= query params. Front-load keywords: add page, new page, frontend, html, vanilla js, iife, ui, dashboard, static.
---# Add a New Frontend Page

## FILES TO MODIFY
1. HTML: `src/main/resources/static/{feature}.html` (new)
2. JS: `src/main/resources/static/js/{feature}.js` (new, IIFE pattern)
3. API wrappers: `src/main/resources/static/js/api.js` (add functions)
4. Navigation: `src/main/resources/static/js/navigation.js` (register nav item)

The frontend is server-rendered static HTML + vanilla JS served directly from `src/main/resources/static/` by Spring Boot — **no build step**. One HTML file per feature, one JS module per page, all wrapped in IIFEs to avoid globals.

## Directory Layout

```
src/main/resources/static/
├── index.html                       # Dashboard
├── stocks.html                      # Stock management
├── strategy.html                    # Strategy config
├── <your-feature>.html              # ← new page
├── strategy-manager.js              # (top-level — see note below)
├── <your-feature>.js                # ← new JS module (top-level, NOT in js/)
├── css/theme.css                     # Shared dark theme
├── js/
│   ├── api.js                       # API wrappers — ALL fetch calls live here
│   ├── navigation.js                 # Shared nav component (auto-injects)
│   ├── stock-management.js
│   ├── watchlist.js
│   ├── portfolio.js
│   └── ...
```

## Conventions to Match

| Convention | Detail |
|------------|--------|
| **JS module pattern** | `(function() { ... })();` IIFE — no globals leak |
| **HTML structure** | Dark theme: `<body class="dark min-h-screen">`, container `max-w-7xl`, TailwindCSS via CDN (`<script src="https://cdn.tailwindcss.com">`) |
| **CSS** | Local theme overrides in `css/theme.css`; page-specific styles inline in `<style>` |
| **API calls** | All wrapped in `js/api.js` — import via function name (script tag order matters) |
| **Cache-busting** | Use `?_=Date.now()` (or `?v=<timestamp>`) query params on script tags — the browser caches aggressively |
| **Icons** | Font Awesome 6.4 (`<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">`) |
| **Strategic page modules** | `strategy-manager.js` lives top-level (not under `js/`) — that's a historical quirk. New modules can go top-level too. |

## Step 1: Create the HTML page

Use `strategy.html` as the minimal canonical template — copy its skeleton:

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Stock Tracker - Your Feature</title>
<link rel="icon" type="image/x-icon" href="favicon.ico">
<link rel="apple-touch-icon" href="apple-touch-icon-precomposed.png">
<script src="https://cdn.tailwindcss.com"></script>
<link rel="stylesheet" href="css/theme.css">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
<style>
    /* page-specific styles here */
</style>
</head>
<body class="dark min-h-screen">
<div class="container mx-auto px-4 py-8 max-w-7xl">
    <div class="flex justify-between items-center mb-8">
        <div>
            <h1 class="text-3xl font-bold"><i class="fas fa-<icon> text-blue-400 mr-3"></i>Your title</h1>
            <p class="text-secondary mt-1">Subtitle here</p>
        </div>
    </div>

    <div id="navigation"></div>

    <!-- Page content -->
    <div class="card rounded-xl shadow-lg overflow-hidden">
        <div id="yourContent"></div>
    </div>
</div>

<!-- Scripts in this exact order, with cache-busting -->
<script>document.write('<script src="js/api.js?_=' + Date.now() + '"><\/script>');</script>
<script>document.write('<script src="js/navigation.js?_=' + Date.now() + '"><\/script>');</script>
<script>document.write('<script src="js/your-feature.js?_=' + Date.now() + '"><\/script>');</script>
</body>
</html>
```

**Why `document.write` for script tags:** This is the repo's chosen cache-busting pattern (the browser would otherwise cache `api.js` indefinitely between sessions on the same Spring Boot app). Keep that pattern — don't replace it with a static `<script src>` tag unless the user asks.

## Step 2: Create the JS module — IIFE, no globals

Create `src/main/resources/static/your-feature.js` (or `js/your-feature.js` — match neighboring files):

```js
(function() {
    // All state lives inside the IIFE closure — NO globals leak
    let pageState = {
        isLoading: false,
        data: null,
        filter: ''
    };

    async function init() {
        try {
            pageState.isLoading = true;
            renderLoading();
            pageState.data = await getYourData();   // from api.js
            render();
            attachEventListeners();
        } catch (err) {
            renderError(err);
            console.error('your-feature init failed:', err);
        } finally {
            pageState.isLoading = false;
        }
    }

    function render() {
        const container = document.getElementById('yourContent');
        if (!container) return;
        // build DOM imperatively or via innerHTML
        container.innerHTML = `
            <div class="...">
                ${pageState.data.map(item => `...`).join('')}
            </div>
        `;
    }

    function attachEventListeners() {
        // delegate or directly add listeners
    }

    // Kick off on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
```

### JS Conventions

- **No globals.** All state lives inside the IIFE closure. Don't `window.x = ...` unless you have a strong reason (the only global is `window.NavigationComponent` from `navigation.js`, and that's an exception).
- **All API calls via `api.js` wrappers** — don't `fetch()` directly in your page module. Add the wrappers in `api.js` first (Step 3).
- **Tailwind classes, dark-mode**: Use the `card`, `text-secondary`, `rounded-xl`, `shadow-lg` utilities — they're defined in `css/theme.css` and consistent across all pages.
- **Toasts**: Look for a shared toast helper in existing `.js` files (some pages use `.toast-notification` CSS class — see `strategy.html`'s `<style>`). Reuse, don't reinvent.
- **Toggle switches**: AVOID `pointer-events-none` on parent containers of toggles — it blocks clicks on children (documented gotcha in AGENTS.md).

## Step 3: Add API wrappers in `js/api.js`

Every API call the page makes must have a wrapper in `js/api.js`. Append in the obvious section:

```js
// Your Feature API
async function getYourData() {
    return apiCall('/your-resource');
}

async function getYourItem(id) {
    return apiCall(`/your-resource/${id}`);
}

async function createYourItem(payload) {
    return apiCall('/your-resource', {
        method: 'POST',
        body: JSON.stringify(payload)
    });
}

async function updateYourItem(id, payload) {
    return apiCall(`/your-resource/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
    });
}

async function deleteYourItem(id) {
    return apiCall(`/your-resource/${id}`, { method: 'DELETE' });
}
```

Note: `apiCall()` parses JSON, throws on `!response.ok`, and unwraps the `ApiResponse<T>` envelope by returning `data` directly. The wrappers in `api.js` should return whatever `data` is — its `.data` field will contain the actual payload.

## Step 4: Register in `js/navigation.js`

Add your page to `NAV_ITEMS` (top nav) or `DROPDOWN_ITEMS` ("More" dropdown) in `js/navigation.js`:

```js
const NAV_ITEMS = [
  { href: 'index.html', label: 'Dashboard', icon: 'fas fa-chart-pie' },
  { href: 'strategy.html', label: 'Strategy', icon: 'fas fa-chess-knight' },
  { href: 'stocks.html', label: 'Stock Management', icon: 'fas fa-database' }
  // Add top-nav items here (keep this list short — 3-4 max in the top bar)
];

const DROPDOWN_ITEMS = [
  // ...existing entries
  { href: 'your-feature.html', label: 'Your Feature', icon: 'fas fa-<your-icon>' }
];
```

Pick a Font Awesome icon name (`fa-<your-icon>`) — see https://fontawesome.com/icons for the full free set. Match existing style: lowercase kebab-case, `fas` prefix.

## Verification

1. **App running:** `mvn spring-boot:run -Dspring-boot.run.profiles=dev` → http://localhost:8080/your-feature.html
2. **Cache busting:** Hard-refresh browser (`Cmd+Shift+R`) or open a private window — Spring Boot caches static aggressively.
3. **Console clean:** Check browser DevTools console for `404`s on the JS files (means wrong script tag path) or `500`s from API calls (means backend endpoint missing — see the `add-endpoint` skill).
4. **Nav active state:** Click your nav item — its background should turn `bg-blue-600` (auto-detected from `CURRENT_PAGE` in `navigation.js`).
5. **Mobile:** Resize to mobile width (~375px) — verify container wraps and no horizontal scroll.

## Gotchas

- **Browser caching is your #1 frustration.** If the page looks weird or old, ALWAYS hard-refresh or use `?_=Date.now()` cache-busting. Editing HTML and not seeing changes usually means cache, not a code bug.
- **Script tag order matters.** `api.js` MUST load before your page module — your IIFE calls its functions at parse time.
- **Spring Boot serves `static/` directly.** Don't add a build step, don't add a bundler — this is intentional. Keep it vanilla.
- **No JS framework.** Don't pull in React, Vue, or any frontend framework unless explicitly requested. Use Tailwind utility classes + vanilla DOM manipulation.
- **`@Transactional` reminder** — if your page calls delete endpoints, the corresponding service method must be `@Transactional` or you'll see 500s.
