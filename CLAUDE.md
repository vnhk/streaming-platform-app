# Streaming Platform App - Project Notes

> **IMPORTANT**: Keep this file updated when making significant changes to the codebase. This file serves as persistent memory between Claude Code sessions.

## Overview
Video streaming application with HLS/MP4 support, remote control via WebSocket, and subtitle management.

## Key Architecture

### Video Players
- `AbstractVideoPlayer` - Base interface for video players
- `HLSVideoPlayerComponent` - HLS streaming with hls.js
- `MP4VideoPlayerComponent` - Direct MP4 playback

### Views

#### AbstractProductionPlayerView
Main video player page with keyboard shortcuts and progress tracking.

**Keyboard Shortcuts:**
- `Space` - Toggle play/pause
- `B` - Toggle subtitles
- `F` - Toggle fullscreen
- `Arrow Left/Right` - Seek ±5 seconds

**Important:** Shortcuts are registered via `Shortcuts.addShortcutListener()` and MUST be removed in `onDetach()`:
```java
private void removeKeyboardShortcuts() {
    if (togglePlayPauseShortcut != null) togglePlayPauseShortcut.remove();
    // ... remove all shortcuts
}

@Override
protected void onDetach(DetachEvent detachEvent) {
    super.onDetach(detachEvent);
    removeKeyboardShortcuts();
}
```

#### AbstractRemoteControlSupportedView
WebSocket-based remote control for TV mode.

**JavaScript RemoteControlManager:**
- Connects to `/ws/remote-control` WebSocket
- Handles keyboard events for remote control
- Supports Media Session API

**Activation Overlay (Browser Autoplay Policy):**
When a TV connects, an activation overlay is shown that must be clicked before remote playback works.
This is required because browsers block `video.play()` without prior user interaction.
The overlay:
- Shows Room ID prominently for easy phone connection
- Has "Click to Enable Playback" button
- On click: briefly plays video muted to enable autoplay permission, then shows small status indicator

**CRITICAL - Cleanup Required:**
The JavaScript `keydown` listener is attached to `document` and MUST be cleaned up when leaving the page:

```java
@Override
protected void onDetach(DetachEvent detachEvent) {
    super.onDetach(detachEvent);
    cleanupRemoteControl();
}

private void cleanupRemoteControl() {
    getElement().executeJs("""
        if (window.remoteControl) {
            window.remoteControl.cleanup();
            window.remoteControl = null;
        }
    """);
}
```

The `cleanup()` method in JavaScript:
- Removes `keydown` event listener from document
- Closes WebSocket without triggering reconnect
- Removes UI elements (connection info div)

Without this cleanup, spacebar and arrow keys will be captured even after navigating away!

#### AbstractRemoteControlView
Mobile remote control interface for controlling TV playback from phone.

**Constructor:**
```java
public AbstractRemoteControlView(Map<String, ProductionData> streamingProductionData)
```

**Features:**
- Connection status indicator (green/red dot)
- Room ID input for WebSocket connection
- Quick navigation: Home, Search, Recent
- Playback controls: play/pause, seek ±10s
- Volume controls
- Display controls: Maximize, PiP, Fullscreen
- Search dialog to find and play productions
- **Episode navigation**: Prev/Next episode buttons (with cross-season support)
- **Audio/Subtitle selection**: Dialogs to select audio tracks (HLS) and subtitles (HLS/MP4)
- **Series browser**: Expandable seasons/episodes navigation for TV series

**WebSocket Cleanup in onDetach:**
```java
@Override
protected void onDetach(DetachEvent detachEvent) {
    super.onDetach(detachEvent);
    getElement().executeJs("""
        if (window.remoteControl) {
            if (window.remoteControl.ws) {
                window.remoteControl.ws.onclose = null;
                window.remoteControl.ws.close();
            }
            window.remoteControl = null;
        }
    """);
}
```

**Styling:** Uses inline CSS with `--glass-*` CSS variables for theme compatibility.

### Remote Control Commands
| Action | Description |
|--------|-------------|
| `PLAY` | Start playback |
| `PAUSE` | Pause playback |
| `TOGGLE_PLAY` | Toggle play/pause |
| `SEEK` | Seek with `{relative: seconds}` |
| `VOLUME` | Adjust volume `{relative: 0.1}` |
| `FULLSCREEN` | Toggle fullscreen |
| `FULLSCREEN_PROMPT` | Toggle fullscreen (for remote) |
| `MAXIMIZE` | Toggle maximized mode |
| `PIP` | Picture-in-Picture |
| `NAVIGATE` | Navigate to URL `{url: '/path'}` |
| `GET_TRACKS` | Request audio/subtitle tracks `{trackType: 'audio'\|'subtitles'}` |
| `SET_AUDIO_TRACK` | Set audio track `{index: number}` |
| `SET_SUBTITLE_TRACK` | Set subtitle track `{index: number}` (-1 = off) |
| `NEXT_EPISODE` | Navigate to next episode (cross-season support) |
| `PREV_EPISODE` | Navigate to previous episode (cross-season support) |

### Remote Control Response Messages
| Type | Description |
|------|-------------|
| `TRACKS_RESPONSE` | Response with available tracks `{tracks: [...], currentIndex: number}` |
| `EPISODE_INFO` | Info about prev/next episode availability |

### Progress Tracking
Video watch progress is saved every 10 seconds via `@ClientCallable`:
```java
@ClientCallable
public void reportWatchProgress(double currentTime) {
    // Saves to WatchDetails entity
}
```

Cleanup in `onDetach`:
```java
getElement().executeJs(
    "if (window._progressTracker) { clearInterval(window._progressTracker); }"
);
```

## File Structure

### Java
- `view/player/AbstractProductionPlayerView.java` - Main player view
- `view/player/HLSVideoPlayerComponent.java` - HLS player
- `view/player/MP4VideoPlayerComponent.java` - MP4 player
- `view/player/SubtitleControlPanel.java` - Subtitle delay controls
- `view/AbstractRemoteControlSupportedView.java` - WebSocket remote control (TV side)
- `view/AbstractRemoteControlView.java` - Mobile remote control interface
- `view/AbstractProductionListView.java` - Production list with search/filters
- `view/AbstractProductionDetailsView.java` - Production details page
- `VideoManager.java` - Video file management
- `VideoController.java` - REST endpoints for video streaming
- `config/ProductionData.java` - Production metadata wrapper
- `config/ProductionDetails.java` - Production details (name, type, rating, etc.)

### Entities
- `WatchDetails` - Watch progress per user/video

### Config/Data Classes
- `config/ProductionData` - Wrapper with mainFolder, productionDetails, base64PosterSrc
- `config/ProductionDetails` - Name, description, type, rating, categories, tags, videoFormat
- `config/StreamingConfigLoader` - Loads production data from config files

### Cross-Season Episode Navigation
`VideoManager` provides methods for navigating between episodes across seasons:
- `getNextVideoWithCrossSeasonSupport(videoFolderId, productionData)` - Gets next episode, moving to next season if needed
- `getPrevVideoWithCrossSeasonSupport(videoFolderId, productionData)` - Gets previous episode, moving to previous season if needed
- `hasNextEpisode(videoFolderId, productionData)` - Checks if next episode exists
- `hasPrevEpisode(videoFolderId, productionData)` - Checks if previous episode exists

Episode sorting uses regex pattern: `(?:Ep(?:isode)?\s?)(\d+)(?![0-9a-zA-Z])`

## Important Notes

1. **Keyboard cleanup is critical** - Without proper cleanup in `onDetach()`, keyboard shortcuts persist across page navigation
2. **WebSocket reconnect** - `RemoteControlManager` auto-reconnects every 3 seconds; cleanup must set `ws.onclose = null` before closing
3. **HLS.js** - Loaded from CDN: `https://cdn.jsdelivr.net/npm/hls.js@latest`
4. **Subtitle sync** - Delays stored per-language (EN, PL, ES) in `WatchDetails`
5. **Production data injection** - Views receive `Map<String, ProductionData> streamingProductionData` as Spring bean (not a service)
6. **Video player URL format** - `/streaming-platform/video-player/:productionName/:videoFolderId` requires both production name and video folder ID
