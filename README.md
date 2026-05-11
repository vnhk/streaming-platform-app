# streaming-platform-app

Video streaming platform with HLS/MP4 playback, WebSocket-based remote control (phone → TV), subtitle management, and watch progress tracking.

## Features

- **HLS and MP4 support**: hls.js for adaptive streaming, direct MP4 fallback
- **Remote control**: Connect a phone to control TV playback via WebSocket room
- **Subtitle control**: Per-language delay adjustment (EN/PL/ES), stored in watch history
- **Cross-season navigation**: Next/previous episode across seasons
- **Audio/subtitle track selection**: Via remote control dialogs
- **Watch progress**: Saved every 10 seconds; resume from last position
- **Keyboard shortcuts**: Space (play/pause), B (subtitles), F (fullscreen), ←/→ (±5 s)

## Key Views

| View | Purpose |
|------|---------|
| `AbstractProductionPlayerView` | Main video player with keyboard shortcuts and progress tracking |
| `AbstractRemoteControlSupportedView` | TV-side WebSocket remote control receiver |
| `AbstractRemoteControlView` | Phone-side remote control interface |
| `AbstractProductionListView` | Production list with search and filters |
| `AbstractProductionDetailsView` | Production detail page |

## Remote Control Commands

`PLAY`, `PAUSE`, `TOGGLE_PLAY`, `SEEK`, `VOLUME`, `FULLSCREEN`, `MAXIMIZE`, `PIP`, `NAVIGATE`, `GET_TRACKS`, `SET_AUDIO_TRACK`, `SET_SUBTITLE_TRACK`, `NEXT_EPISODE`, `PREV_EPISODE`

## WebSocket

- Endpoint: `/ws/remote-control`
- Rooms identified by Room ID (shown on TV screen)
- Browser autoplay policy: activation overlay shown when TV connects (click required before remote playback)

## REST

| Endpoint | Description |
|----------|-------------|
| `GET /api/streaming/video` | Stream video (byte-range aware) |
| `GET /api/streaming/light-player/page` | Light HTML player page |

## Episode Sorting

Regex pattern: `(?:Ep(?:isode)?\s?)(\d+)(?![0-9a-zA-Z])`

## Key Entity

`WatchDetails` — watch progress per user/video, subtitle delays per language

## Configuration

Productions are defined in config files loaded by `StreamingConfigLoader`. Each production has: name, description, type, rating, categories, tags, video format, main folder, poster image.

## Build

```bash
mvn clean install -DskipTests
```

Part of the `my-tools` multi-module Maven project. Requires `common` to be built first.
