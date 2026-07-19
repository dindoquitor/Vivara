# Vivara — TV Web Browser

## Product Requirements Document

**Version**: 3.0.0  
**Status**: Draft  
**Target Platform**: Android TV / Google TV / Amazon Fire TV / Android-based set-top boxes  
**Minimum SDK**: 29 (Android 10)  
**Target SDK**: 36 (Android 16)  
**Language**: Kotlin  
**License**: MPL 2.0  

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Target Audience & Use Cases](#2-target-audience--use-cases)
3. [Architecture Overview](#3-architecture-overview)
4. [Feature Specifications](#4-feature-specifications)
5. [User Interface](#5-user-interface)
6. [Data Model & Persistence](#6-data-model--persistence)
7. [Web Engine Layer](#7-web-engine-layer)
8. [Build & Distribution](#8-build--distribution)
9. [Implementation Stages](#9-implementation-stages)
10. [Non-Functional Requirements](#10-non-functional-requirements)

---

## 1. Executive Summary

Vivara is an **open-source web browser purpose-built for the living room screen**. Unlike repurposed mobile browsers, Vivara is designed from the ground up for **remote control, gamepad, and D-pad navigation** — the only input methods available on TV platforms. It provides a full desktop-class browsing experience on television, complete with tabbed browsing, bookmarks, history, downloads, ad blocking, voice search, and privacy features.

### 1.1 Core Value Proposition

- **Navigate any website with a TV remote** via a physics-based on-screen virtual cursor
- **Dual engine support**: System WebView (lightweight) or Mozilla GeckoView (Gecko engine, advanced privacy)
- **Privacy-first**: Incognito mode runs in a separate OS process; GeckoView offers Enhanced Tracking Protection
- **Open source**: FOSS build flavor with no proprietary dependencies
- **Zero-touch updates**: Built-in self-updater for sideloaded installations

### 1.2 Key Metrics

| Metric | Target |
|--------|--------|
| Cold start to interactive | < 2 seconds |
| Tab switch latency | < 500ms |
| APK size (geckoIncluded) | < 120 MB |
| APK size (geckoExcluded) | < 15 MB |
| Memory per tab (WebView) | < 80 MB |
| Memory per tab (GeckoView) | < 150 MB |
| ANR rate | < 0.1% |
| Crash rate | < 0.5% |

---

## 2. Target Audience & Use Cases

### 2.1 Primary Personas

| Persona | Needs |
|---------|-------|
| **Living room surfer** | Quickly look up information, watch videos, check social media during TV downtime |
| **Cord-cutter** | Access web-based streaming services not available as native TV apps |
| **Privacy-conscious user** | Browse without tracking, isolated incognito sessions |
| **Power user** | Multiple tabs, keyboard shortcuts, custom search engines, advanced settings |
| **FOSS enthusiast** | Fully open-source build with no Google Play Services dependency |

### 2.2 Core Use Cases

1. **Search the web**: Type or speak a query → see results → navigate pages
2. **Watch web video**: Navigate to YouTube/streaming site → enter fullscreen → control playback with remote
3. **Multi-tab research**: Open links in new tabs → switch between tabs → close when done
4. **Download files**: Click download link → see progress in notification → open file when complete
5. **Private browsing**: Enter incognito → browse → exit → all traces cleared
6. **Bookmark important sites**: Save to bookmarks → access from home page quick links
7. **Check browsing history**: Open history → search or scroll → revisit a past page

---

## 3. Architecture Overview

### 3.1 Module Structure

```
vivara/
├── app/                    # Main application module
│   ├── src/main/           # Activities, Services, UI, database, WebView engine
│   └── src/test/           # Unit tests
├── common/                 # Shared library module
│   └── src/main/           # Models, WebEngine interface, cursor system, utilities
├── gecko/                  # GeckoView engine module
│   └── src/main/           # GeckoWebEngine, delegates, WebExtension
└── buildSrc/               # Gradle convention plugins
```

**Dependency direction**: `app → common ← gecko`  
(`app` depends on `common`; `gecko` depends on `common`; `app` optionally depends on `gecko`)

### 3.2 Layered Architecture

```
┌──────────────────────────────────────┐
│            UI Layer (Activities)      │
│  MainActivity, HistoryActivity,       │
│  DownloadsActivity, SettingsDialog    │
├──────────────────────────────────────┤
│        ViewModel / ActiveModel Layer  │
│  MainActivityViewModel, TabsModel,    │
│  SettingsModel, HistoryModel, etc.    │
├──────────────────────────────────────┤
│         Engine Abstraction Layer      │
│  WebEngine interface                  │
│  ├── WebViewWebEngine                 │
│  └── GeckoWebEngine                   │
├──────────────────────────────────────┤
│         Data Layer (Room DB)          │
│  AppDatabase, DAOs, Entities          │
├──────────────────────────────────────┤
│         Platform / Android SDK        │
└──────────────────────────────────────┘
```

### 3.3 Key Design Patterns

| Pattern | Where Used |
|---------|-----------|
| **Provider/Factory** | `WebEngineFactory` — engine implementation registered at init, created per activity |
| **Observer/Observable** | `ObservableValue<T>`, `ObservableList<T>` — reactive UI updates without LiveData/Flow |
| **Strategy** | `WebEngine` interface — interchangeable web rendering backends |
| **Singleton** | `AppDatabase`, `FaviconsPool`, `UserPreferences` — app-wide state |
| **Delegate** | GeckoView delegates (`NavigationDelegate`, `ContentDelegate`, etc.) |
| **Active Model** | Scoped data holders (`ActiveModel`) with lifecycle-bound coroutine scopes |

---

## 4. Feature Specifications

### 4.1 Navigation System (Virtual Cursor)

**Problem**: TV remotes lack touchscreens and mice. Standard D-pad focus navigation doesn't work on arbitrary web pages.

**Solution**: A physics-based virtual cursor rendered as an overlay on top of the web content.

#### 4.1.1 Cursor Behavior

| Property | Specification |
|----------|--------------|
| **Movement** | D-pad directions accelerate cursor (configurable speed + acceleration) |
| **Visual** | Semi-transparent white circle with gray stroke; pulsing animation on appear |
| **Click** | D-pad CENTER / ENTER / A button = tap at cursor position |
| **Long-press** | Hold CENTER ≥ 800ms = context menu at cursor position |
| **Hide timeout** | Cursor fades after 5 seconds of inactivity; reappears on next D-pad input |
| **Edge scrolling** | When cursor reaches viewport edge, the page scrolls in that direction |
| **Scroll hack** | Touch-drag emulation for pages that don't respond to `scrollTo()` (optional, configurable) |

#### 4.1.2 Cursor Modes

| Mode | Description |
|------|------------|
| **Virtual Cursor** (default) | Cursor overlay visible; D-pad moves cursor; CENTER = click at cursor |
| **Direct Navigation** | Cursor hidden; D-pad keys forwarded to the page directly (for sites with native TV focus) |
| **Grab Mode** | Cursor becomes crosshair; drag-to-scroll by moving cursor while holding CENTER |

#### 4.1.3 Gamepad / Joystick Support

- Analog stick axes are translated to D-pad events via `DPADNavigationEventsAdapter`
- Configurable dead zone and sensitivity
- Option to disable axis-to-D-pad translation (for games or apps that use analog directly)
- Triggers: L2/R2 mapped to BACK/FORWARD navigation

#### 4.1.4 Configuration

| Setting | Range | Default |
|---------|-------|---------|
| Cursor max speed | 25% – 200% | 100% |
| Cursor acceleration | 25% – 200% | 100% |
| Joystick D-pad translation | on / off | on |

### 4.2 Tabbed Browsing

#### 4.2.1 Tab Management

- **Create tab**: "+" button in tab strip; "Open in new tab" from context menu; CTRL+T keyboard shortcut
- **Close tab**: "×" button on tab; CTRL+W; close all tabs option
- **Switch tab**: Click tab in horizontal tab strip; CTRL+1–9
- **Reorder tabs**: "Move left" / "Move right" from tab's options menu
- **Tab limit**: Soft cap of 20 tabs (configurable); warn user at limit

#### 4.2.2 Tab State Persistence

- All tabs persisted in Room database (`WebTabState` table)
- Tab state includes: URL, title, scroll position, zoom level, back/forward history (serialized), thumbnail
- On app restart: all tabs restored to their last state
- Thumbnail rendered as PNG → cached on disk per tab → loaded asynchronously

#### 4.2.3 Tab Strip UI

- Horizontal scrolling row at top of screen
- Each tab shows: favicon (left), truncated title (center), close button (right)
- Active tab highlighted with accent color
- Animated add/remove transitions
- Auto-hides after 3 seconds of inactivity (slides up); reappears on D-pad UP

### 4.3 URL Bar & Search

#### 4.3.1 URL Entry

- **Input method**: On-screen keyboard (Android TV IME) or voice input
- **Auto-detection**: Input without TLD or spaces → treated as search query; input with dots/scheme → treated as URL
- **Autocomplete**: Suggest from bookmarks and history as user types (top 5 matches, debounced at 300ms)
- **URL display**: Show domain only when page is loaded (hide scheme); show full URL on focus

#### 4.3.2 Search Engines

| Engine | Search URL Template |
|--------|-------------------|
| Google | `https://www.google.com/search?q=[query]` |
| Bing | `https://www.bing.com/search?q=[query]` |
| Yahoo | `https://search.yahoo.com/search?p=[query]` |
| DuckDuckGo | `https://duckduckgo.com/?q=[query]` |
| Yandex | `https://yandex.com/search/?text=[query]` |
| Startpage | `https://www.startpage.com/do/dsearch?query=[query]` |
| **Custom** | User-defined URL with `[query]` placeholder |

#### 4.3.3 URL Bar Actions

| Button | Position | Action |
|--------|----------|--------|
| Back | Left of URL | Go back in tab history |
| Forward | Left of URL | Go forward in tab history |
| Refresh/Stop | Right of URL | Reload page or stop loading |
| Voice search | Right of URL | Start voice recognition |
| Bookmarks | Right of URL | Open bookmarks dialog |
| Tabs overview | Right | Open tabs overview |
| Settings | Right | Open settings dialog |

### 4.4 Voice Search

#### 4.4.1 Implementation

- Android 11+ (API 30+): Use `SpeechRecognizer` directly for real-time partial results
- Android 10 (API 29): Use `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` with system dialog fallback
- Custom overlay shows real-time speech visualization (RMS amplitude bars)
- Final result inserted into URL bar; auto-submitted as search if recognized as query
- Hidden on Fire TV devices (Amazon `amazon.hardware.fire_tv` feature flag absent on most models)

#### 4.4.2 Permissions

- `RECORD_AUDIO` — requested at runtime on first use
- `MODIFY_AUDIO_SETTINGS` — for audio focus management during recognition

### 4.5 Bookmarks & Favorites

#### 4.5.1 Data Model

```
FavoriteItem {
    id: Long (PK)
    title: String
    url: String?           // null → this item is a folder
    favicon: String?       // cached favicon file path
    parent: Long?          // parent folder ID (null = root)
    home_page_bookmark: Boolean
    i_order: Int           // sort order within parent
    dest_url: String?      // redirect/link-out URL (for recommended items)
    description: String?
    valid_until: Long?     // expiry timestamp (for promotional items)
    useful: Boolean        // user marked as useful (for ML-based recommendations)
}
```

#### 4.5.2 Features

- **Add bookmark**: From context menu or star icon in URL bar
- **Edit bookmark**: Title, URL, folder assignment
- **Folder support**: Create folders; nested bookmarks; folder icon in list
- **Reorder**: Drag-to-reorder within a folder (or move up/down buttons for remote)
- **Delete**: Single delete or multi-select batch delete
- **Search**: Filter bookmarks by title/URL
- **Home page quick links**: Flag any bookmark/folder as "show on home page"
- **Import/Export**: Bookmarks HTML file (Netscape format) import/export
- **Sync** (v3.1): Firefox Sync / custom sync service for cross-device bookmarks

### 4.6 Browsing History

#### 4.6.1 Features

- **Chronological list**: Grouped by date (Today, Yesterday, This Week, Older)
- **Search**: Full-text search across title + URL
- **Multi-select delete**: Select multiple entries → delete
- **Clear all**: "Clear browsing data" with time range options (Last hour, Today, This week, All time)
- **Visit count**: Track most-visited URLs for home page recommendations
- **Auto-cleanup**: Entries older than 90 days auto-purged; 5000 entry soft cap

#### 4.6.2 Incognito Behavior

- History entries created during incognito sessions are never persisted
- No gap/indication in normal history that incognito browsing occurred

### 4.7 Downloads

#### 4.7.1 Download Sources

| Source | Description |
|--------|-------------|
| **HTTP/HTTPS** | Standard file download via `DownloadManager` or custom `HttpURLConnection` |
| **Blob** | JavaScript `blob:` URLs → base64 data extracted → saved to file |
| **Data URI** | `data:` scheme → decoded → saved to file |

#### 4.7.2 Download Lifecycle

```
1. User clicks download link / context menu "Download"
2. Filename guessed from Content-Disposition header, URL path, or MIME type
3. Foreground Service started with notification (icon + progress bar)
4. File saved to Downloads/vivara/ directory
5. On completion: notification updated with "Open" / "Delete" actions
6. APK files: prompt to install after download completes
```

#### 4.7.3 Download Manager UI

- **Active downloads**: Show progress bar, filename, size, speed; cancel button
- **Completed downloads**: Show filename, size, date; open / delete / open folder actions
- **Empty state**: Illustration + "No downloads yet" message
- **Sort**: By date, name, size

#### 4.7.4 Filename Resolution

- Parse `Content-Disposition` header per RFC 6266
- Fallback: extract filename from URL path
- Fallback: guess from MIME type + extension map
- Final fallback: "downloadfile.bin"
- Collision resolution: append `_(1)`, `_(2)`, etc.

### 4.8 Content Blocking

#### 4.8.1 Ad Blocking

- **Engine**: EasyList-based filter rules via `AdBlockClient` (Brave's adblock library)
- **Serialized cache**: Compiled filter list cached to disk (`adblock_ser.dat`); loaded at startup for instant blocking
- **Auto-update**: Filter list refreshed every 30 days from configurable URL
- **Per-tab toggle**: Override global adblock setting per tab
- **Blocked counter**: Badge showing blocked requests on current page
- **Filter types**: Image, CSS, script, XMLHttpRequest, subdocument, object

#### 4.8.2 Popup Blocking

| Level | Behavior |
|-------|----------|
| 0 — Allow all | No popup blocking |
| 1 — Block dialogs | Block `window.open()` triggered popups |
| 2 — Block dialogs + new tabs | Also block script-initiated new tab requests |
| 3 — Block all (strict) | Block any attempt to open a new window |

- Configuration is **per-host** (stored in `HostConfig` table)
- Blocked popup toast notification: "Popup blocked — tap to allow"
- Override: user can tap notification to allow popups for this host

#### 4.8.3 Tracking Protection (GeckoView only)

- GeckoView Enhanced Tracking Protection (ETP) enabled by default
- Strict mode: blocks all known trackers
- Anti-fingerprinting: resist browser fingerprinting techniques
- Cookie policy: accept non-tracker cookies only (`ACCEPT_NON_TRACKERS`)

### 4.9 Home Page & Speed Dial

#### 4.9.1 Home Page Modes

| Mode | Behavior |
|------|----------|
| **Vivara Home** | Loads the Vivara home page from server (cached locally); shows speed dial grid + search bar |
| **Search engine** | Loads the selected search engine's homepage |
| **Custom URL** | User-defined URL |
| **Blank** | Empty page (`about:blank`) |

#### 4.9.2 Vivara Home Page Content

- **Search bar**: Prominently centered; uses configured search engine
- **Speed dial grid**: 2 rows × 4 columns of site shortcuts (8 total)
- **Content sources** (configurable):
  - **Bookmarks**: User's home-page bookmarks, sorted by custom order
  - **Latest History**: 8 most recent non-incognito history entries
  - **Most Visited**: Aggregated top URLs by visit frequency
- **Empty state**: Curated recommended sites (Wikipedia, Reddit, IMDb, BBC News, etc.)
- **Edit mode**: Long-press a shortcut → edit title/URL or remove
- **Add shortcut**: "+" tile at end of grid

#### 4.9.3 Home Page Architecture

- Rendered as an **HTML page** (not native views) loaded in a WebView
- JavaScript bridge (`AndroidJSInterface`) connects HTML UI to native Android features:
  - `onHomePageLoaded()` — page signals ready → native sends configuration
  - `renderLinks(linksJson)` — native sends bookmark/history data
  - `setSearchEngine(name, url, icon)` — configure search bar
  - `onEditBookmark(id)` — open native bookmark editor
  - `markBookmarkRecommendationAsUseful(id)` — feedback for recommendations
- Offline fallback: bundled HTML/CSS/JS assets in `assets/`
- Favicon loading: home page requests `favicon://<url>` → native intercepts and serves cached favicon

### 4.10 Privacy & Incognito Mode

#### 4.10.1 Incognito Architecture

- **Separate OS process** (`android:process=":incognito"`)
- **Isolated WebView data directory**: `WebView.setDataDirectorySuffix("incognito")`
- **Session cleanup on exit**:
  - Clear all cookies (`CookieManager.removeAllCookies()`)
  - Clear WebStorage (localStorage, sessionStorage, IndexedDB)
  - Clear HTTP cache
  - Delete incognito data directory
- **No history recording**: History DAO calls gated by `isIncognito` flag
- **Separate tab set**: Incognito tabs stored with `incognito = true` flag; never mixed with normal tabs

#### 4.10.2 Incognito UI Indicators

- Dark-themed toolbar (charcoal/black) distinct from normal theme
- Incognito icon in URL bar area
- First-launch hint dialog explaining incognito behavior (one-time, with "Don't show again")

#### 4.10.3 Mode Switching

- Toggle from ActionBar → kills current process → starts new activity in target mode
- Quick: preserves last normal tab URL for easy continuation

### 4.11 Fullscreen & Media Playback

#### 4.11.1 Fullscreen Video

- Detect `<video>` fullscreen request via `onShowCustomView` callback
- Hide system bars, action bar, tab strip
- Show minimal controls overlay: exit fullscreen button
- D-pad CENTER toggles overlay visibility
- Exit: system BACK button or overlay exit button

#### 4.11.2 Media Controls

| Action | Keyboard Shortcut | Gamepad | Remote |
|--------|-------------------|---------|--------|
| Play/Pause | Media Play/Pause key | A (configurable) | CENTER on overlay |
| Stop | Media Stop key | B (configurable) | — |
| Rewind | Media Rewind key | L1 | — |
| Fast Forward | Media Fast Forward key | R1 | — |

- WebView: controls injected via JavaScript (`vivaraTogglePlayback()`)
- GeckoView: controls via `MediaSession` delegate

### 4.12 Keyboard Shortcuts

#### 4.12.1 Configurable Shortcuts

| Action | Default Key | Modifiers | Long-press |
|--------|------------|-----------|------------|
| Navigate Back | ESC / Back | — | — |
| Navigate Home | Browser Home | — | — |
| Refresh Page | F5 | — | — |
| Voice Search | F6 | — | — |
| Play/Pause | Media Play/Pause | — | — |
| Media Stop | Media Stop | — | — |
| Rewind | Media Rewind | — | — |
| Fast Forward | Media Fast Forward | — | — |

#### 4.12.2 Shortcut Configuration UI

- List of 8 action slots
- Per slot: key code picker (press key to capture), modifier checkboxes (Alt/Ctrl/Shift), long-press toggle
- Reserved keys (DPAD, HOME, VOLUME) cannot be assigned
- Conflict detection: warn if two actions share the same binding

### 4.13 Context Menu

#### 4.13.1 Trigger Methods

- Long-press CENTER while cursor is over web content
- Right-click on connected mouse
- Menu key on keyboard

#### 4.13.2 Menu Items (Web Content)

| Item | Action |
|------|--------|
| Open in new tab | Create tab, load URL, don't switch |
| Open in external app | Intent resolution for URL |
| Download link | Trigger download for link URL |
| Copy link | Copy URL to clipboard |
| Share | `ACTION_SEND` intent with URL |
| Refresh | Reload current page |

#### 4.13.3 Cursor Menu (Radial)

On long-press over non-link content, a 4-item radial menu appears:

| Direction | Action |
|-----------|--------|
| Grab mode | Enter scroll-drag mode |
| Context menu | Show system context menu |
| DPAD mode | Toggle direct navigation |
| Zoom | Zoom in/out sub-menu |

### 4.14 Settings

#### 4.14.1 Settings Categories

**General**
- Web engine selection (WebView / GeckoView)
- Search engine + custom URL
- Home page mode + home page links source
- User Agent string (6 presets: Android Phone/Tablet, Chrome Desktop, Firefox Desktop, Safari Desktop, Edge Desktop + custom)
- Allow autoplay media (on/off)

**Appearance**
- Theme: System default / Light / Dark
- Algorithmic page darkening (WebView only, dark theme only)
- Keep screen on while browsing

**Content Blocking**
- AdBlock enabled/disabled
- Custom filter list URL
- Update filter list now

**Navigation**
- Cursor max speed (slider 25%–200%)
- Cursor acceleration (slider 25%–200%)
- Joystick D-pad translation (on/off)

**Privacy & Security**
- Clear browsing data (select: history, cookies, cache, saved passwords)
- Clear all data button

**Shortcuts**
- 8 configurable keyboard shortcuts

**About**
- Version number + build flavor
- Web engine version
- GitHub link
- License (MPL 2.0)
- Privacy policy
- Check for updates (generic flavor only)
- Update channel selector

#### 4.14.2 Data Storage

Settings persisted via `SharedPreferences` (key-value) with `UserPreferences` wrapper class. Type-safe accessors for each preference.

### 4.15 Auto-Update (Generic Flavor Only)

#### 4.15.1 Mechanism

1. App fetches `latest_version.json` from GitHub releases (configurable URL)
2. Compares `versionCode` with installed version
3. If newer available: show notification with changelog
4. User taps "Update" → APK downloaded to cache → `FileProvider` URI → system install intent
5. Progress bar in notification during download
6. Daily check throttle (configurable interval)

#### 4.15.2 Update Channels

- Stable (default)
- Beta
- Alpha/Nightly
- Channel selector in version settings

#### 4.15.3 Security

- APK verified by Android package manager signature check before install
- HTTPS only for manifest and APK download
- User must explicitly approve each update

### 4.16 Accessibility

- Content description labels on all interactive UI elements
- Minimum touch/cursor target size: 48dp
- Sufficient color contrast (WCAG AA minimum) in both light and dark themes
- System font size scaling respected where applicable
- TalkBack / screen reader compatibility for chrome UI

### 4.17 Localization

- All user-facing strings externalized to `strings.xml`
- Supported languages: English, Russian, German, Polish, Italian, Chinese (Simplified), Chinese (Traditional), Hebrew (RTL), Persian (RTL), Ukrainian, Vietnamese
- RTL layout support for Hebrew and Persian

---

## 5. User Interface

### 5.1 Screen Layout (Main Browsing View)

```
┌──────────────────────────────────────┐
│ [Tab Strip — auto-hiding]            │  ← Horizontal scrollable tabs
├──────────────────────────────────────┤
│ [←] [→] [📍 URL BAR          ] [🎤] [★] [⊞] [⚙] │  ← Action bar
├──────────────────────────────────────┤
│                                      │
│          WEB CONTENT AREA            │  ← WebView / GeckoView
│      (virtual cursor overlay)        │
│                                      │
├──────────────────────────────────────┤
│ [Status bar: loading / adblock count]│  ← Footer (transient)
└──────────────────────────────────────┘
```

### 5.2 Design Tokens

| Token | Light Theme | Dark Theme |
|-------|-------------|------------|
| Primary background | `#FFFFFF` | `#1A1A1A` |
| Surface (cards, dialogs) | `#F5F5F5` | `#2A2A2A` |
| Accent color | `#1976D2` | `#64B5F6` |
| Text primary | `#212121` | `#E0E0E0` |
| Text secondary | `#757575` | `#9E9E9E` |
| Divider | `#E0E0E0` | `#424242` |
| Error / destructive | `#D32F2F` | `#EF5350` |

### 5.3 Dialog System

All dialogs use Android `AlertDialog` / `DialogFragment` pattern via custom base class providing:
- Consistent theming (light/dark)
- D-pad navigation between dialog buttons
- Dismiss on BACK key
- 48dp minimum touch targets

### 5.4 Loading States

| State | Visual |
|-------|--------|
| Page loading | Progress bar at top of web content (2dp height, accent color); refresh button becomes stop (✕) |
| Empty list | Illustration + descriptive text (e.g., "No downloads yet") |
| Error | Full-screen error with icon, message, and "Retry" / "Go back" buttons |
| Network offline | Banner below action bar: "You're offline" |

---

## 6. Data Model & Persistence

### 6.1 Room Database

| Table | Primary Key | Purpose |
|-------|------------|---------|
| `tabs` | `id` (autoIncrement) | Web tab state persistence |
| `favorites` | `ID` (autoIncrement) | Bookmarks and folders |
| `history` | `id` (autoIncrement) | Browsing history |
| `downloads` | `id` (autoIncrement) | Download records |
| `hosts` | `id` (autoIncrement), `host_name` (unique) | Per-host popup blocker config + favicon |

### 6.2 Database Versioning & Migration

- **Schema version**: Tracked in `@Database(version = N)`
- **Migration strategy**: Explicit `Migration(startVersion, endVersion)` objects for each version jump
- **Destructive fallback**: `fallbackToDestructiveMigration()` in debug builds only; release builds require migration path
- **Schema export**: Enabled in CI (`room.schemaLocation` → checked into VCS); disabled in release
- **Testing**: Migration tests with Robolectric verifying data integrity across versions

### 6.3 File System Cache

| Directory | Contents | Max Size | Cleanup |
|-----------|----------|----------|---------|
| `tabthumbs/` | Tab thumbnail PNGs (200×120) | 50 MB | LRU, trim on app close |
| `favicons/` | Site favicon PNGs | 10 MB | LRU, trim on app close |
| `adblock/` | `adblock_ser.dat` (compiled filter list) | ~5 MB | Replace on update |
| `Downloads/vivara/` | User downloaded files | Unlimited | User-managed |
| `cache/WebView/` | WebView HTTP cache | Managed by system | Managed by WebView |

### 6.4 In-Memory Cache

- **Favicon LRU**: `LruCache<String, Bitmap>` — 10 MB
- **Tab thumbnail LRU**: `LruCache<Int, Bitmap>` — 5 thumbnails (~3 MB)
- **ActiveModel instances**: `HashMap<Class, ActiveModel>` — scoped to activity lifecycle

---

## 7. Web Engine Layer

### 7.1 `WebEngine` Interface

```kotlin
interface WebEngine {
    // Navigation
    fun loadUrl(url: String)
    fun goBack()
    fun goForward()
    fun reload()
    val canGoBack: Boolean
    val canGoForward: Boolean

    // Zoom
    fun zoomIn()
    fun zoomOut()
    fun zoomBy(factor: Float)
    val canZoomIn: Boolean
    val canZoomOut: Boolean

    // State
    fun saveState(): Bundle?
    fun restoreState(state: Bundle)

    // JavaScript
    fun evaluateJavascript(script: String)

    // Lifecycle
    fun onResume()
    fun onPause()
    fun onAttachToWindow()
    fun onDetachFromWindow()
    fun trimMemory(level: Int)

    // View management
    fun getView(): View?
    fun renderThumbnail(): Bitmap?

    // Fullscreen
    fun hideFullscreenView()

    // Media
    fun togglePlayback()
    fun stopPlayback()
    fun rewind()
    fun fastForward()

    // Cursor
    fun setVirtualCursorMode(enabled: Boolean)
    val isVirtualCursorMode: Boolean
    fun getCursorDrawerDelegate(): CursorDrawerDelegate?

    // Content blocking
    fun onUpdateAdblockSetting(enabled: Boolean)

    // Callbacks
    fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    fun onFilePicked(data: Uri?)
}

interface WebEngineProvider {
    val name: String
    val version: String
    fun create(context: Context, callback: WebEngineWindowProviderCallback): WebEngine
}
```

### 7.2 System WebView Engine

- Wraps Android `WebView` with custom subclass (`WebViewEx`)
- `WebViewClient` for page lifecycle, SSL errors, URL interception
- `WebChromeClient` for fullscreen, file chooser, progress, geolocation
- `AndroidJSInterface` injected via `addJavascriptInterface`
- Favicon serving: intercept `favicon://` scheme requests → serve from cache
- Version info from `WebViewCompat.getCurrentWebViewPackage()`

### 7.3 GeckoView Engine

- Uses **single shared `GeckoView`** instance across tabs (session switching, not view switching)
- `GeckoRuntime` configured with:
  - Cookie behavior: `ACCEPT_NON_TRACKERS`
  - Tracking protection: `ETP_LEVEL_DEFAULT | ETP_LEVEL_STRICT`
  - Safe browsing enabled
- Custom WebExtension (versioned, bundled in APK) for content script messaging
- Delegates:
  - `NavigationDelegate` — URL loading, redirects, SSL errors
  - `ProgressDelegate` — page load progress, security info
  - `ContentDelegate` — context menu, fullscreen, title, favicon
  - `PromptDelegate` — JavaScript dialogs, HTTP auth, file chooser
  - `PermissionDelegate` — geolocation, media permissions
  - `HistoryDelegate` — back/forward list, history state changes
  - `ContentBlockingDelegate` — blocked tracker reporting
  - `MediaSessionDelegate` — media playback controls
  - `SelectionActionDelegate` — text selection
- Session state serialization: `GeckoSession.saveState()` / `restoreState()`

### 7.4 Engine Selection

- Selected at app startup; requires restart to change
- Stored as string preference (`"webview"` or `"gecko"`)
- GeckoView flavor must be installed (`geckoIncluded` build variant)
- Fallback to WebView if GeckoView unavailable

---

## 8. Build & Distribution

### 8.1 Build Variants

| Flavor Combination | `applicationId` | Auto-update | Engine | Play Store |
|-------------------|-----------------|-------------|--------|------------|
| `fossGeckoExcludedDebug` | `com.vivara.browser.foss` | No | WebView | ❌ |
| `fossGeckoExcludedRelease` | `com.vivara.browser.foss` | No | WebView | ❌ |
| `fossGeckoIncludedRelease` | `com.vivara.browser.foss` | No | Both | ❌ |
| `genericGeckoExcludedRelease` | `com.vivara.browser` | Yes | WebView | ❌ |
| `genericGeckoIncludedRelease` | `com.vivara.browser` | Yes | Both | ❌ |
| **`googleGeckoExcludedRelease`** | **`com.vivara.browser`** | **No** | **WebView** | **✅** |
| **`googleGeckoIncludedRelease`** | **`com.vivara.browser`** | **No** | **Both** | **✅** |

> **The `google` flavor is the only Play Store track.** It uses `applicationId = "com.vivara.browser"` and publishes under the same listing as the `generic` flavor, but with auto-update disabled and Play-restricted permissions removed.

### 8.2 Build System

- **Gradle** with Kotlin DSL
- **Version catalog** (`gradle/libs.versions.toml`) for all dependencies
- **Convention plugins** (`buildSrc/`) for shared Android/Kotlin config
- **KSP** (Kotlin Symbol Processing) for Room annotation processing
- **ProGuard/R8** in release builds (`proguard-android-optimize.txt`)

### 8.3 Distribution Channels

| Channel | Flavor | Notes |
|---------|--------|-------|
| **Google Play Store** | `google` | Play-managed updates; restricted permissions removed; Data Safety section required |
| GitHub Releases | `generic`, `foss` | APK + changelog + auto-update JSON manifest |
| F-Droid | `foss` | Fully FOSS, no proprietary deps, reproducible build |
| Amazon Appstore | `generic` | Fire TV compatible, sideloaded |
| Side-load | `generic` | Built-in updater for ongoing updates; `REQUEST_INSTALL_PACKAGES` allowed |

### 8.4 Permissions Manifest (with Flavor Scoping)

Permissions are scoped by build flavor to ensure Play Store compliance for the `google` variant.

#### Main Manifest (shared across all flavors)

```xml
<!-- Essential — no Play declaration needed -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Downloads — Play declaration: Foreground service (dataSync) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- Voice search — Play declaration: Microphone -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<!-- Device features — Play declarations required for location, camera -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.CAMERA" />

<!-- Storage (API ≤ 32 only) — no Play declaration needed -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29" />
```

#### `google` Flavor Manifest Overlay (`app/src/google/AndroidManifest.xml`)

```xml
<!-- QUERY_ALL_PACKAGES — requires Play Console declaration form -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
    tools:node="merge" />

<!-- Explicitly REMOVE REQUEST_INSTALL_PACKAGES — 
     Google Play restricts this to apps that are device "file managers, 
     app stores, or enterprise device management" only. 
     A browser does not qualify. -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"
    tools:node="remove" />
```

#### `generic` Flavor Manifest Overlay (`app/src/generic/AndroidManifest.xml`)

```xml
<!-- Self-updating APK installer — only for sideloaded/generic flavor -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- Package visibility for voice search, external app launching -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

#### Package Visibility (`<queries>` block)

```xml
<queries>
    <!-- Open URLs in external browsers/apps -->
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="https" />
    </intent>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="http" />
    </intent>
    <!-- Voice recognition services -->
    <intent>
        <action android:name="android.speech.RecognitionService" />
    </intent>
    <!-- Detect installed browsers/launchers -->
    <intent>
        <action android:name="android.intent.action.MAIN" />
    </intent>
</queries>
```

#### Hardware Features (all `required="false"`)

```xml
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<uses-feature android:name="android.hardware.faketouch" android:required="false" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.microphone" android:required="false" />
<uses-feature android:name="android.hardware.location" android:required="false" />
<uses-feature android:name="android.hardware.location.network" android:required="false" />
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.screen.landscape" android:required="false" />
<uses-feature android:name="android.software.webview" android:required="false" />
```

> **Note on `android.software.leanback`**: Declared but `required="false"` so the app is discoverable on both TV and mobile Play Store listings. TV is the primary target. If the app should appear **only** on TV devices, change to `required="true"` — this is a Play Console listing decision.

### 8.5 Signing

| Environment | Signing Method |
|-------------|---------------|
| **Local development** | Default Android debug keystore |
| **CI (GitHub Actions)** | Upload keystore via `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` env vars |
| **Google Play Store** | **Play App Signing** (recommended): upload key for CI, Google manages the release signing key |
| **F-Droid** | Reproducible build; F-Droid signs with its own key |

#### Play App Signing Setup

1. Generate an **upload key** (separate from the release signing key):
   ```bash
   keytool -genkey -v -keystore upload-keystore.jks -alias upload -keyalg RSA \
     -keysize 2048 -validity 10000
   ```
2. In Play Console → App integrity → App signing: enroll in Play App Signing
3. Upload the **upload key certificate** (PEM); Google generates the release key
4. CI uses the **upload keystore**; Google re-signs with the release key before delivery
5. Benefit: if the upload key is lost/compromised, contact Google to reset — release key stays safe

---

### 8.6 Google Play Store Compliance

This section covers every requirement for submitting and maintaining the `google` flavor on Google Play.

#### 8.6.1 Flavor-Specific Compliance Summary

| Requirement | `google` (Play Store) | `generic` (Sideload) |
|------------|----------------------|----------------------|
| `REQUEST_INSTALL_PACKAGES` | ❌ **Removed** (violates policy for non-file-manager/installer apps) | ✅ Allowed (self-update) |
| `QUERY_ALL_PACKAGES` | ⚠️ Kept — requires **Play Console declaration form** justifying use | ✅ Allowed |
| Built-in auto-updater | ❌ Disabled via `BUILT_IN_AUTO_UPDATE = false` | ✅ Enabled |
| Update mechanism | Play Store managed updates only | Self-update via GitHub manifest |
| Crash reporting / analytics | Opt-in only | Opt-in only |
| Ad SDKs | None | None |
| User-generated content flag | No (browser renders web, doesn't host content) | N/A |
| Child-directed ads | No (no ads) | N/A |

#### 8.6.2 Restricted Permissions — Play Console Declaration Forms

Every "sensitive" permission must be declared in Play Console under **App content → Sensitive app permissions**. A reviewer evaluates the justification before the app is approved.

| Permission | Requires Declaration? | Justification for Vivara |
|-----------|----------------------|--------------------------|
| `QUERY_ALL_PACKAGES` | **Yes** — fill out declaration form | Needed to detect installed browsers/apps for "Open in external app" feature, and to discover speech recognition services for voice search on pre-API-30 devices |
| `RECORD_AUDIO` | **Yes** — Microphone | Voice search: user explicitly taps microphone button to start recognition; audio is processed on-device via `SpeechRecognizer`, never recorded or transmitted |
| `CAMERA` | **Yes** — Camera | WebRTC support: websites may request camera access for video calls/conferencing; permission is granted per-site via WebView/GeckoView runtime prompt, not at app level |
| `ACCESS_COARSE_LOCATION` | **Yes** — Location (API ≤ 32 only) | Geolocation for websites (maps, local services); permission is granted per-site via WebView/GeckoView runtime prompt; limited to `maxSdkVersion="32"` |
| `FOREGROUND_SERVICE_DATA_SYNC` | **Yes** — Foreground service | Download manager: shows persistent notification with progress bar while files are being downloaded; service stops immediately when queue is empty |
| `POST_NOTIFICATIONS` | No (auto-granted on API 33+) | Download progress and completion notifications |

#### 8.6.3 Data Safety Section (Play Console)

Must be filled out in Play Console under **Policy → App content → Data safety**. This is publicly visible on the store listing.

| Data Type | Collected? | Shared? | Purpose | Optional? | Encryption |
|-----------|-----------|---------|---------|-----------|------------|
| **Web browsing history** | ✅ (on-device only) | ❌ Never shared | App functionality (history feature, autocomplete) | Yes — can clear in settings | N/A (on-device) |
| **Bookmarks** | ✅ (on-device only) | ❌ Never shared | App functionality | Yes — user-managed | N/A (on-device) |
| **Downloaded files** | ✅ (on-device only) | ❌ Never shared | App functionality | Yes — user-managed | N/A (on-device) |
| **Voice / audio** | ❌ Not collected | ❌ | Voice search uses on-device `SpeechRecognizer`; audio buffer is transient, never stored | N/A | N/A |
| **Location** | ❌ Not collected by app | ❌ | App does not access location; websites may request it via WebView (per-site consent) | N/A | N/A |
| **App interactions** | ❌ Not collected | ❌ | No analytics, no usage tracking | N/A | N/A |
| **Crash logs** | ❌ Not collected (FOSS/Google flavors) | ❌ | No crash reporting SDK | N/A | N/A |
| **Device ID** | ❌ Not collected | ❌ | No advertising ID, no device fingerprinting | N/A | N/A |
| **Cookies** | ⚠️ Managed by WebView | ❌ | Standard web cookies stored per-site by the rendering engine; cleared on incognito exit; user can clear all via Settings | Yes — clear browsing data | N/A |

> **Key point**: Vivara does not operate any backend servers. All user data (history, bookmarks, downloads, cookies) stays on-device in the app's private storage. Nothing is transmitted to the developer or any third party. This must be clearly stated in the privacy policy and Data Safety section.

#### 8.6.4 Privacy Policy

**Requirement**: A publicly accessible privacy policy URL is **mandatory** for Play Store submission. It must be linked in:
- Play Console → Policy → App content → Privacy policy
- The app itself (Settings → Version → Privacy policy)

**Required content** (per Google Play DDA §5.1 and GDPR):

```
PRIVACY POLICY FOR VIVARA

Last updated: [DATE]

1. DATA WE COLLECT
   Vivara does NOT collect, transmit, or store any personal data on external 
   servers. All browsing data (history, bookmarks, downloads, cookies) is 
   stored locally on your device in the app's private storage directory.

2. DATA STAYS ON YOUR DEVICE
   - Browsing history: stored in local SQLite database
   - Bookmarks: stored in local SQLite database
   - Downloads: saved to your device's Downloads folder
   - Cookies & site data: managed by the system WebView/GeckoView engine
   - Voice input: processed on-device via Android SpeechRecognizer; audio is 
     not recorded or stored

3. THIRD-PARTY SERVICES
   Vivara uses the following third-party services, each governed by its own 
   privacy policy:
   - System WebView (Google): renders web pages; subject to Google's privacy 
     policy for Android System WebView
   - GeckoView (Mozilla, optional engine): renders web pages; subject to 
     Mozilla's privacy policy
   - Adblock filter lists (EasyList): downloaded periodically from 
     easylist.to to enable content blocking; no user data is sent
   - SpeechRecognizer (Android system service): processes voice input on-device

4. WEBSITES YOU VISIT
   Websites you browse may collect data per their own privacy policies. 
   Vivara's incognito mode deletes cookies and site data when the session ends.

5. YOUR CONTROL
   You can clear all local data at any time via: Settings → Clear browsing data
   You can browse in Incognito mode (separate process, no data persisted).

6. CHILDREN'S PRIVACY
   Vivara does not knowingly collect data from children under 13.

7. CONTACT
   For privacy questions: [CONTACT EMAIL]
   GitHub: CHANGEME_GITHUB_REPO_URL

8. CHANGES
   This policy may be updated. The latest version is always available at:
   https://[YOUR_DOMAIN]/vivara/privacy
```

> The privacy policy URL must be hosted on a domain you control. Options: GitHub Pages, a custom domain, or a privacy-policy-as-a-service provider. It must be functional before submitting to Play Console.

#### 8.6.5 Target API Level Compliance

Google Play requires apps to target an API level within **one year** of the latest Android release.

| Requirement | Vivara Status |
|------------|---------------|
| Target SDK | **36** (Android 16) — compliant ✓ |
| Minimum SDK | 29 (Android 10) — no Play restriction on minSdk |
| New apps | Must target API 35+ (as of Aug 2025) |
| App updates | Must target API 35+ (as of Nov 2025) |
| Update cadence | Bump `targetSdk` in `libs.versions.toml` → `gradle/libs.versions.toml:4` with each Android release |

**Process for SDK bumps:**

1. When Google announces a new API level requirement deadline
2. Update `android-targetSdk` in `gradle/libs.versions.toml:4`
3. Update `android-compileSdk` in `gradle/libs.versions.toml:2`
4. Test on emulator with new API level
5. Address any new permission/behavior changes
6. Submit update to Play Console before the deadline

#### 8.6.6 Content Rating

Must complete the **Content rating questionnaire** in Play Console → Policy → App content → Content rating.

**Expected rating**: **Teen** or **Everyone 10+**

Rationale for the questionnaire answers:

| Question | Answer |
|----------|--------|
| User-generated content? | **No** — the app is a web browser, not a content platform |
| Social networking features? | **No** |
| Unfiltered internet access? | **Yes** — this is a web browser; users can access any URL. Rating agencies typically assign Teen/Mature for browsers |
| In-app purchases? | **No** |
| Gambling? | **No** |

> The final rating is determined by IARC (International Age Rating Coalition) based on the questionnaire. Browsers typically receive **Teen** or **PEGI 12** due to unfiltered internet access.

#### 8.6.7 Developer Program Policies Checklist

| Policy Area | Requirement | Vivara Compliance |
|------------|-------------|-------------------|
| **Impersonation** | Don't impersonate brands | App named "Vivara" — unique, not infringing. Confirm no trademark conflict |
| **Intellectual Property** | Own or license all code and assets | MPL 2.0 licensed; all icons and assets are original or appropriately licensed. Third-party libs (GeckoView, Brave AdBlock) are properly licensed |
| **Privacy & Security** | Privacy policy, Data Safety, no unauthorized data collection | See §8.6.3 and §8.6.4 |
| **Monetization** | Play billing for digital goods | No in-app purchases or paid features — N/A |
| **Impersonation / Spam** | No keyword stuffing in listing | Use "TV Web Browser" with feature-based description, not keyword lists |
| **Malware** | No malicious behavior | Open source (MPL 2.0); F-Droid reproducible build |
| **Mobile Unwanted Software** | No deceptive behavior | All features user-initiated; incognito mode clears data; no background tracking |
| **Families** | Additional rules if targeting children | Not targeting children; Teen rating. If changing to Everyone, adhere to Families Policy (no ad tracking, COPPA compliance) |
| **Android TV Quality** | TV apps must work with D-pad, no touch-only UI | **Compliant** — entire app is D-pad-first, virtual cursor system, no touch-dependent features |
| **Foreground Service** | Must show persistent notification, declare type | Download service shows progress notification with cancel action; `foregroundServiceType="dataSync"` declared |
| **Background Location** | Declaration required | **Not used** — only coarse location for geolocation permission (per-website), `maxSdkVersion="32"` |
| **App Set ID** | Declaration if used for analytics/fraud | **Not used** — no analytics SDKs |
| **Financial Features** | Additional review | **None** — no payments, banking, or crypto features |

#### 8.6.8 Play Console Submission Checklist

**Before first submission:**

- [ ] Create Google Play Developer account ($25 one-time fee)
- [ ] Fill out **Store listing**:
  - [ ] App name: "Vivara: TV Web Browser"
  - [ ] Short description (80 chars): "A fast, private web browser built for your TV. Navigate with your remote."
  - [ ] Full description (4000 chars): Feature-based, honest, no keyword stuffing
  - [ ] Screenshots (minimum 4): TV UI — home page with speed dial, web page with cursor, tabs overview, settings. 1280×720 or 1920×1080 PNG/JPEG
  - [ ] Feature graphic (1024×500): Branded Vivara banner for store listing header
  - [ ] App icon (512×512): Adaptive icon (foreground + background layers)
  - [ ] Banner (1280×720): Android TV banner for Leanback launcher
  - [ ] Category: **Tools** or **Communication** (Android TV browsers typically under Tools)
  - [ ] Tags: browser, web, tv, internet, privacy
- [ ] Upload **privacy policy URL** (see §8.6.4)
- [ ] Complete **Data Safety** section (§8.6.3)
- [ ] Complete **Content rating** questionnaire (§8.6.6)
- [ ] Complete **Sensitive app permissions** declarations (§8.6.2):
  - [ ] `QUERY_ALL_PACKAGES` declaration form
  - [ ] Microphone (`RECORD_AUDIO`) declaration
  - [ ] Camera (`CAMERA`) declaration
  - [ ] Location (`ACCESS_COARSE_LOCATION`) declaration
  - [ ] Foreground service (`FOREGROUND_SERVICE_DATA_SYNC`) declaration
- [ ] Verify `REQUEST_INSTALL_PACKAGES` is **removed** in `google` flavor
- [ ] Verify `BUILT_IN_AUTO_UPDATE = false` in `google` flavor
- [ ] **App integrity**: enroll in Play App Signing (§8.5)
- [ ] Upload **release AAB** (Android App Bundle) for `googleGeckoExcludedRelease` and/or `googleGeckoIncludedRelease`
- [ ] Set **target audience**: 13+ (Teen rating)
- [ ] Set **news & updates**: tick "I am not a news app"
- [ ] Verify app runs on at least one physical TV device (Google recommends testing on actual hardware)
- [ ] Check for **Android vitals** thresholds: ANR < 0.47%, crash rate < 1.09% (bad behavior thresholds)

**Before each update:**

- [ ] Increment `versionCode` in `app/build.gradle.kts:11`
- [ ] Update `versionName` in `app/build.gradle.kts:12`
- [ ] Update release notes in Play Console
- [ ] Verify `targetSdk` meets deadline
- [ ] Test migration path from previous version
- [ ] Check for new Play policy requirements (announced quarterly)
- [ ] Verify no new sensitive permissions were added without declaration
- [ ] Re-run Content rating if new features change answers
- [ ] Upload and roll out (staged rollout recommended — 10% → 50% → 100%)

**Ongoing compliance monitoring:**

- [ ] Monitor Android vitals dashboard (crash rate, ANR rate)
- [ ] Respond to user reviews, especially bug reports
- [ ] Update privacy policy URL if domain changes
- [ ] Renew/verify Play Developer account annually
- [ ] Watch for Google Play policy announcement emails

---

## 9. Implementation Stages

### Stage 1 — Foundation (Weeks 1–3)

**Goal**: Project scaffolding, core engine abstraction, basic browsing

| Task | Description | Deliverable |
|------|-------------|-------------|
| 1.1 | Set up Gradle multi-module project with convention plugins | Build passes: `./gradlew assembleDebug` |
| 1.2 | Create `WebEngine` interface + `WebEngineFactory` in `:common` | Interface + provider pattern ready |
| 1.3 | Implement `WebViewWebEngine` in `:app` | Load URLs, navigate back/forward, reload |
| 1.4 | Implement `MainActivity` with WebView and basic URL bar (no tabs yet) | Single-page browsing works |
| 1.5 | Implement URL bar: text input, URL detection, search engine dispatch | Type URL → load; type query → search |
| 1.6 | Set up Room database with `WebTabState` entity + `TabsDao` | Tab state persistence ready |
| 1.7 | Implement basic tab system (create, close, switch) | Multi-tab browsing works |
| 1.8 | Tab strip UI: horizontal scrollable tabs with favicon + title | Visual tab management |
| 1.9 | Tab state save/restore on activity lifecycle | Survive rotation and app restart |

### Stage 2 — Navigation & Input (Weeks 4–6)

**Goal**: Virtual cursor system, D-pad and gamepad navigation

| Task | Description | Deliverable |
|------|-------------|-------------|
| 2.1 | Implement `CursorDrawerDelegate`: physics model, rendering, hide timeout | Cursor appears and moves with D-pad |
| 2.2 | Implement cursor click → touch event dispatch at cursor position | Tap works on web pages |
| 2.3 | Implement edge-scroll: cursor at viewport edge scrolls the page | Scrollable content navigable |
| 2.4 | Implement long-press detection + cursor menu (radial menu) | Context actions accessible |
| 2.5 | Implement `CursorLayout` overlay container | Cursor renders above web content |
| 2.6 | Implement Grab Mode (drag-to-scroll) | Alternative scroll method |
| 2.7 | Implement Direct Navigation Mode (DPAD mode) | Native TV-focus sites supported |
| 2.8 | Implement `DPADNavigationEventsAdapter` (gamepad axes → D-pad) | Gamepads work for navigation |
| 2.9 | Implement `BackNavigationEventsAdapter` (back key with channel dedupe) | Back navigation works reliably |
| 2.10 | Add cursor settings (speed, acceleration, joystick toggle) | User-adjustable cursor behavior |

### Stage 3 — Bookmarks & History (Weeks 7–8)

**Goal**: Data management features

| Task | Description | Deliverable |
|------|-------------|-------------|
| 3.1 | Implement `FavoriteItem` entity + `FavoritesDao` (CRUD + folder hierarchy) | Bookmark storage ready |
| 3.2 | Implement `FavoritesDialog` with folder tree, add/edit/delete | Full bookmark management |
| 3.3 | Implement `HistoryItem` entity + `HistoryDao` (CRUD + date grouping + search) | History storage ready |
| 3.4 | Implement `HistoryActivity` with date headers, search, multi-select delete | Full history browsing |
| 3.5 | Implement URL bar autocomplete from bookmarks + history | Quick URL suggestions |
| 3.6 | Implement "most visited" aggregation query | Home page data source |

### Stage 4 — Home Page & Speed Dial (Weeks 9–10)

**Goal**: Beautiful, functional new-tab experience

| Task | Description | Deliverable |
|------|-------------|-------------|
| 4.1 | Design and build home page HTML/CSS/JS (responsive, TV-optimized) | Standalone home page assets |
| 4.2 | Implement `AndroidJSInterface` bridge methods | JS-to-native communication |
| 4.3 | Implement `HomePageHelper` with favicon interception | Home page with live favicons |
| 4.4 | Implement speed dial grid from bookmarks/history/most-visited | Dynamic link tiles |
| 4.5 | Implement search bar on home page with engine selection | Search from home page |
| 4.6 | Implement edit/add/remove speed dial tiles | User customization |
| 4.7 | Bundle default recommended sites for empty state | First-launch experience |
| 4.8 | Add offline fallback (bundled assets) | Works without internet |

### Stage 5 — Downloads & File Management (Week 11)

**Goal**: Reliable download system

| Task | Description | Deliverable |
|------|-------------|-------------|
| 5.1 | Implement `DownloadService` foreground service with notification | Background downloads |
| 5.2 | Implement HTTP/HTTPS download via `HttpURLConnection` | Standard file downloads |
| 5.3 | Implement Blob and Data URI download support | JS-triggered downloads |
| 5.4 | Implement filename extraction from headers/URL/MIME | Correct file naming |
| 5.5 | Implement `DownloadsActivity` with active + history lists | Download management UI |
| 5.6 | Implement APK install flow | Download → install prompt |
| 5.7 | Implement file open / open folder actions | File access from app |

### Stage 6 — Content Blocking (Weeks 12–13)

**Goal**: Ad and popup blocking

| Task | Description | Deliverable |
|------|-------------|-------------|
| 6.1 | Integrate adblock library + EasyList | Filter engine running |
| 6.2 | Implement filter list caching (serialize/deserialize `adblock_ser.dat`) | Fast startup, no re-download |
| 6.3 | Implement auto-update scheduler (30-day cycle) | Filters stay current |
| 6.4 | Implement blocked request counter + badge | Visibility into blocking |
| 6.5 | Implement per-tab adblock toggle | Granular control |
| 6.6 | Implement popup blocking (4 levels, per-host config) | Popup management |
| 6.7 | Implement `HostConfig` entity + `HostsDao` | Per-host settings persistence |
| 6.8 | Add adblock settings UI (enable/disable, filter URL, manual update) | User control over blocking |

### Stage 7 — Privacy & Incognito (Week 14)

**Goal**: Private browsing mode

| Task | Description | Deliverable |
|------|-------------|-------------|
| 7.1 | Implement separate `:incognito` process with `IncognitoModeMainActivity` | Process isolation |
| 7.2 | Implement isolated WebView data directory per incognito session | Data isolation |
| 7.3 | Implement session cleanup on incognito exit | No data leaks |
| 7.4 | Gate history recording on incognito flag | No incognito history |
| 7.5 | Implement incognito tab separation in DB | Separate tab sets |
| 7.6 | Implement incognito UI theming (dark toolbar, icon indicator) | Visual distinction |
| 7.7 | Implement first-launch incognito hint dialog | User education |
| 7.8 | Implement mode switch flow (kill → restart) | Seamless switching |
| 7.9 | Clear browsing data dialog (time-range selection) | Privacy hygiene |

### Stage 8 — GeckoView Engine (Weeks 15–17)

**Goal**: Second rendering engine with enhanced privacy

| Task | Description | Deliverable |
|------|-------------|-------------|
| 8.1 | Set up `:gecko` module with GeckoView dependency | Module structure |
| 8.2 | Implement `GeckoRuntime` initialization (singleton, lazy) | Runtime ready |
| 8.3 | Implement shared `GeckoView` instance with session switching | View reuse across tabs |
| 8.4 | Implement `GeckoSession` per tab with `GeckoSessionSettings` | Session management |
| 8.5 | Implement `NavigationDelegate` (URL loading, redirect, SSL error) | Page navigation |
| 8.6 | Implement `ProgressDelegate` (loading progress, security info) | Loading feedback |
| 8.7 | Implement `ContentDelegate` (context menu, fullscreen, favicon) | Content callbacks |
| 8.8 | Implement `PromptDelegate` (JS dialogs, HTTP auth, file chooser) | User prompts |
| 8.9 | Implement `PermissionDelegate` (geolocation, media) | Permission handling |
| 8.10 | Implement `ContentBlockingDelegate` (tracker reporting, ETP) | Anti-tracking |
| 8.11 | Implement `MediaSessionDelegate` (play/pause/stop/seek) | Media controls |
| 8.12 | Implement SessionState save/restore | Tab state persistence |
| 8.13 | Create and bundle WebExtension for content script messaging | JS bridge |
| 8.14 | Register `GeckoWebEngine` in `WebEngineFactory` | Engine selection works |
| 8.15 | Add engine selection UI in settings | User can switch engines |
| 8.16 | Theme propagation (light/dark → `preferredColorScheme`) | Themed browsing |

### Stage 9 — Voice Search & Input (Week 18)

**Goal**: Hands-free search input

| Task | Description | Deliverable |
|------|-------------|-------------|
| 9.1 | Implement `VoiceSearchHelper` with `SpeechRecognizer` (API 30+) | Real-time recognition |
| 9.2 | Implement `RecognizerIntent` fallback (API 29) | Android 10 support |
| 9.3 | Implement voice overlay with RMS visualization | Visual feedback |
| 9.4 | Implement runtime permission request flow | `RECORD_AUDIO` permission |
| 9.5 | Add voice search button to URL bar | UI trigger |
| 9.6 | Hide on Fire TV (feature detection `amazon.hardware.fire_tv`) | Platform compatibility |

### Stage 10 — Fullscreen & Media Playback (Week 19)

**Goal**: Video and media controls

| Task | Description | Deliverable |
|------|-------------|-------------|
| 10.1 | Implement `onShowCustomView` / `onHideCustomView` handling | Fullscreen enters/exits |
| 10.2 | System bar visibility management during fullscreen | Immersive video |
| 10.3 | Implement media control injection for WebView (`vivaraTogglePlayback`, etc.) | Media keys work in WebView |
| 10.4 | Wire GeckoView `MediaSessionDelegate` to media key events | Media keys work in GeckoView |
| 10.5 | Implement minimal fullscreen overlay with exit button | Fullscreen UI |
| 10.6 | Implement keep-screen-on during video playback | Screen doesn't sleep |

### Stage 11 — Settings & Configuration (Week 20)

**Goal**: Complete settings UI

| Task | Description | Deliverable |
|------|-------------|-------------|
| 11.1 | Implement `SettingsDialog` with tabbed layout | Settings entry point |
| 11.2 | Implement General settings tab (all options) | Core configuration |
| 11.3 | Implement Shortcuts settings tab (key capture, conflict detection) | Keyboard customization |
| 11.4 | Implement Version/About tab (versions, links, updates) | App info + updates |
| 11.5 | Implement algorithmic darkening for WebView | Better dark mode |
| 11.6 | Implement User Agent presets + custom UA | UA switching |
| 11.7 | Implement clear cache / data actions | Data management |

### Stage 12 — Keyboard Shortcuts (Week 21)

**Goal**: Hardware keyboard support

| Task | Description | Deliverable |
|------|-------------|-------------|
| 12.1 | Implement shortcut storage schema in SharedPreferences | Persistence |
| 12.2 | Implement key capture UI (press to assign) | Shortcut configuration |
| 12.3 | Implement modifier support (Alt/Ctrl/Shift) | Combo shortcuts |
| 12.4 | Implement long-press detection for shortcuts | Long-press actions |
| 12.5 | Implement global key dispatch hook (`Window.Callback`) | System-level handling |
| 12.6 | Implement reserved key blocking | DPAD/HOME/VOLUME protected |
| 12.7 | Implement conflict detection in settings UI | No overlapping shortcuts |

### Stage 13 — Auto-Update System (Week 22)

**Goal**: Self-updating for sideloaded installations

| Task | Description | Deliverable |
|------|-------------|-------------|
| 13.1 | Implement `UpdateChecker` with JSON manifest fetch | Version comparison |
| 13.2 | Implement APK download with progress notification | Background download |
| 13.3 | Implement `FileProvider` + install intent | System install flow |
| 13.4 | Implement update channel selector (stable/beta/nightly) | Channel switching |
| 13.5 | Implement daily check throttle | Rate-limited updates |
| 13.6 | Implement changelog dialog | Release notes display |
| 13.7 | Gate feature on `BUILT_IN_AUTO_UPDATE` build config | Flavor-specific |

### Stage 14 — Favicon System (Week 23)

**Goal**: Rich site icons everywhere

| Task | Description | Deliverable |
|------|-------------|-------------|
| 14.1 | Implement `FaviconExtractor` (parse HTML for icon links) | Find favicons |
| 14.2 | Implement `FaviconsPool` with LRU cache | In-memory caching |
| 14.3 | Implement file-based favicon cache in `favicons/` | Disk persistence |
| 14.4 | Implement `WebChromeClient.onReceivedIcon` fallback | Alternative source |
| 14.5 | Integrate favicons into tabs, bookmarks, history, home page | Visual polish |
| 14.6 | Implement `favicon://` scheme interception for home page | JS bridge icons |

### Stage 15 — Polish & Quality (Weeks 24–26)

**Goal**: Production-ready quality

| Task | Description | Deliverable |
|------|-------------|-------------|
| 15.1 | Accessibility audit + TalkBack labels | Screen reader support |
| 15.2 | D-pad navigation audit on all dialogs and lists | No focus traps |
| 15.3 | Performance profiling (startup time, tab switch, memory) | Meet metrics |
| 15.4 | Memory leak detection + fix (LeakCanary) | No leaks |
| 15.5 | ANR investigation + fix (StrictMode) | No ANRs |
| 15.6 | Edge case testing (rotation, process death, Doze mode) | Resilient |
| 15.7 | Crash reporting integration (Sentry/Firebase optional, FOSS excludes) | Crash visibility |
| 15.8 | ProGuard/R8 verification — ensure all reflection paths kept | No obfuscation bugs |
| 15.9 | CI pipeline: lint, test, assemble on PR | Quality gate |
| 15.10 | Unit test coverage for DAOs, models, utilities | ≥60% coverage |
| 15.11 | Integration test for engine switching, tab lifecycle | Critical paths |
| 15.12 | Migration test for all Room version jumps | Data integrity |
| 15.13 | Localization review by native speakers | Accurate translations |
| 15.14 | RTL layout verification (Hebrew, Persian) | Correct RTL rendering |
| 15.15 | Final APK size optimization | Within size targets |
| 15.16 | Release notes, changelog, store listing copy | Ready for distribution |

### Stage 16 — v3.1+ Roadmap (Future)

| Feature | Priority | Notes |
|---------|----------|-------|
| Firefox Sync / bookmark sync | High | Cross-device bookmarks |
| Password manager integration | High | Autofill support |
| Reader mode | Medium | Distraction-free reading |
| Offline reading list | Medium | Save pages for later |
| Picture-in-Picture for videos | Medium | PiP while using other apps |
| Custom CSS injection | Low | User stylesheets |
| Userscript support (Greasemonkey-style) | Low | Advanced customization |
| Remote debugging over network | Low | DevTools without USB |
| Casting / DLNA support | Low | Send video to other screens |

---

## 10. Non-Functional Requirements

### 10.1 Performance

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| Cold start → URL bar interactive | < 1500ms | Android Profiler / systrace |
| Tab creation (new tab → loaded) | < 500ms | Timer instrumentation |
| Tab switch (tap → content visible) | < 300ms | Timer instrumentation |
| Page load (TTI, 3G network) | < 5s (median) | WebView onPageFinished callback |
| Scroll FPS | 60 FPS consistent | GPU profiling / dumpsys gfxinfo |
| Memory (idle, 1 tab) | < 60 MB (WebView) / < 120 MB (GeckoView) | `dumpsys meminfo` |
| APK download size | < 15 MB (no Gecko) / < 120 MB (with Gecko) | APK Analyzer |

### 10.2 Reliability

- Crash-free session rate: > 99.5%
- ANR rate: < 0.1%
- Graceful degradation when WebView/GeckoView unavailable
- Timeout handling for all network operations
- Multi-process safety (incognito process isolation)

### 10.3 Security

- HTTPS for all home page content, update manifests, filter lists
- Certificate pinning for update server (optional, FOSS-excluded)
- Sanitized URL handling to prevent intent injection
- File provider with strict `external-files-path` only
- No JavaScript in home page runs in same origin as web content
- Sensitive data (passwords, form data) never persisted by the app
- Incognito mode: no forensic traces on disk after session end

### 10.4 Privacy

- No telemetry or analytics in FOSS build
- Opt-in crash reporting only (generic/google flavors)
- No advertising SDKs
- User data never leaves the device (except bookmarks sync, opt-in)
- Clear privacy policy (bundled in app, linked in store listing)
- GDPR-compliant data practices

### 10.5 Compatibility

- Android 10 (API 29) through Android 16 (API 36)
- Tested on: Google TV (Chromecast), Android TV (NVIDIA Shield, Xiaomi Mi Box), Amazon Fire TV (Stick, Cube), generic AOSP set-top boxes
- Screen sizes: 720p, 1080p, 4K
- Aspect ratios: 16:9 (primary), 4:3 (supported)
- Input: D-pad (IR/Bluetooth remote), gamepad (Xbox, PlayStation, generic), keyboard, mouse, touchpad remote

### 10.6 Maintainability

- Modular architecture with clear dependency boundaries
- Documented public APIs (KDoc on all interface methods)
- Consistent code style (enforced by ktlint / detekt)
- Version catalog for all dependencies
- CI pipeline with lint + test on every PR
- Meaningful commit history following conventional commits

### 10.7 Testing Strategy

| Level | Scope | Tools |
|-------|-------|-------|
| Unit | DAOs, models, utilities, parsers, cursor math | JUnit 4, Mockito |
| Integration | Engine switching, tab lifecycle, download flow | Robolectric, Espresso |
| Migration | Room database version jumps | Robolectric |
| UI | Critical user journeys | Espresso (TV-aware) |
| Manual | Exploratory, accessibility, RTL, localization | Checklist |

---

## Appendix A: Glossary

| Term | Definition |
|------|-----------|
| **D-pad** | Directional pad — the up/down/left/right buttons on a TV remote |
| **Cursor** | The virtual on-screen pointer moved by the D-pad |
| **Direct Navigation (DPAD mode)** | Mode where D-pad keys are forwarded to the web page instead of moving the cursor |
| **Grab Mode** | Cursor mode where moving the cursor while holding CENTER drags the page |
| **GeckoView** | Mozilla's embeddable Gecko browser engine for Android |
| **WebView** | Android's system WebView component (Chromium-based) |
| **ETP** | Enhanced Tracking Protection — GeckoView's anti-tracking feature |
| **FOSS** | Free and Open Source Software build variant |
| **KSP** | Kotlin Symbol Processing — Kotlin's annotation processing tool |
| **Room** | Android Jetpack's SQLite ORM library |

## Appendix B: Dependencies

### Runtime

| Library | Purpose | Version |
|---------|---------|---------|
| AndroidX AppCompat | Backward-compatible UI | Latest stable |
| AndroidX WebKit | WebView extensions | Latest stable |
| AndroidX ConstraintLayout | Flexible layouts | Latest stable |
| AndroidX RecyclerView | List rendering | Latest stable |
| AndroidX Lifecycle | Lifecycle-aware components | Latest stable |
| Kotlin Coroutines | Async operations | Latest stable |
| Room | SQLite ORM | Latest stable |
| Brave AdBlock | Ad blocking engine | Latest stable |
| GeckoView (optional) | Gecko rendering engine | Latest stable |

### Development

| Tool | Purpose |
|------|---------|
| KSP | Room annotation processing |
| JUnit 4 | Unit testing |
| Robolectric | Android unit testing |
| LeakCanary | Memory leak detection (debug) |
| ProGuard/R8 | Code shrinking and obfuscation |
