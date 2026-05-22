<p align="center">
  <a href="README.md">English</a> |
  <a href="README.zh-CN.md">中文</a>
</p>

# Knowmad — Knowledge Nomad

**Knowmad** (知行) is a context-aware AI learning companion for Android. It fuses Android
system-level capabilities — accessibility service, GPS, calendar sync, quick settings — with large
language models to create a proactive, privacy-first study assistant that understands where you are,
what you're doing, and what you need to learn next.

> ⚠️ **Status:** Active development. The app is functional but not yet released on any app store.
> Expect breaking changes.

## Screenshots

<!-- TODO: Add screenshots -->

## Features

### 🧠 AI Agent — Koog-powered conversational agent

- Graph-based state machine agent with tool-calling capabilities
- Streaming responses with real-time reasoning, tool call, and text display
- Persistent conversation history with Room-backed storage
- Multi-provider LLM support (remote APIs + on-device models, hot-swappable)
- Automatic conversation title and message summary generation

### 👁️ Screen Perception — Accessibility-based UI understanding

- Real-time extraction of foreground app UI structure via `AccessibilityService`
- Semantic node tree parsing with caching, debouncing, and coroutine-based concurrency
- Service suspend/resume to minimize battery impact when not actively needed
- Provides the Agent with awareness of the user's current digital context

### 📅 Smart Calendar — Multi-source schedule fusion

- iCalendar import/export with custom property extensions (semester, course, instructor metadata)
- System calendar two-way sync via Android `CalendarContract` and `SyncAdapter`
- Recurrence rule engine supporting complex academic timetables
- Semester-aware time zone handling via `kotlinx-datetime`

### 📱 System Integration — Deep Android platform leverage

- **Quick Settings Tile** — one-tap launch of Picture-in-Picture AI chat
- **Glance Widget** — today's class schedule overview on the home screen
- **Rich Notifications** — class progress tracking with HyperIsland custom UI, quick reply, and
  suggestion cards
- **Calendar Intent Filter** — direct `.ics` file import from file managers or browsers

### 📦 On-Device AI — Privacy-first edge inference

- ExecuTorch runtime for running embedding models (Qwen3) locally
- Model download via ModelScope with incremental download and resume
- HuggingFace tokenizer integration for local tokenization
- Seamless switching between on-device and remote LLM in the same prompt executor

### 🧪 Other Highlights

- **MathJax rendering** for mathematical formula display
- **Custom Compose UI** — blurred backgrounds, shared element transitions, drag-to-reorder, adaptive
  layouts
- **Remend** — webview-based UI rendering extension support
- **QuickJS** — embedded JavaScript runtime for code execution within agent tools

## Architecture

```
app/
├── agent/                   # AI Agent engine (Koog GraphAIAgent)
│   ├── ChatAgent.kt         # Core chat agent state machine
│   ├── ModelService.kt      # Background LLM service, job queue, message dispatch
│   ├── Tool.kt              # Agent tool interfaces
│   ├── Schedule.kt          # Schedule-from-web sub-agent
│   ├── Browser.kt           # Web browsing capability
│   ├── Run.kt               # Agent run orchestration
│   └── client/              # LLM client abstraction (remote + local)
├── accessibility/           # System-level screen perception
│   └── semantic/
│       ├── SemanticAnalysisService.kt
│       ├── Node.kt
│       └── Rect.kt
├── data/                    # Data layer (Room + DataStore)
│   ├── chat/                # Conversation & message persistence
│   ├── schedule/            # Calendar events, iCalendar, recurrence rules
│   ├── geo/                 # Location history
│   ├── file/                # File attachments
│   ├── llm/                 # LLM provider & model config
│   └── database/            # DB migrations, callbacks
├── sync/                    # System calendar sync (SyncAdapter)
├── notification/            # Rich notifications, class progress, suggestions
├── tile/                    # Quick Settings tile for PiP launch
├── widget/                  # Glance home screen widget
├── ui/                      # Jetpack Compose UI layer
│   ├── component/           # Reusable composables
│   ├── page/                # Feature pages (Chat, Calendar, Settings, etc.)
│   ├── viewmodel/           # ViewModels
│   ├── theme/               # Material 3 theming
│   └── util/                # UI utilities
├── util/                    # Shared utilities (crypto, serialization, etc.)
├── agent/                   # Koog agent framework usage
└── modelscope/              # ModelScope API integration
```

### Key Dependencies

| Library                      | Purpose                     |
|------------------------------|-----------------------------|
| Koog Agents                  | AI agent framework          |
| Jetpack Compose + Material 3 | Declarative UI              |
| Room                         | Local persistence           |
| biweekly                     | iCalendar read/write        |
| ExecuTorch                   | On-device ML inference      |
| DJL + HuggingFace Tokenizers | Local tokenization          |
| Ktor                         | HTTP client                 |
| MathJax                      | LaTeX formula rendering     |
| HyperIsland Kit              | Rich notification UI        |
| Glance                       | Home screen widgets         |
| QuickJS                      | Embedded JavaScript runtime |

## Building

### Prerequisites

- Android Studio (latest stable recommended)
- JDK 17+
- Android SDK 35+

### Steps

```bash
git clone https://github.com/ltfan/Knowmad.git
cd Knowmad
./gradlew :app:assembleDebug
```

The build system automatically downloads MathJax assets, Remend runtime, and DJL native libraries
during the build process.

### Release Build

To build a release APK, create `app/key.jks` and `app/key.properties` with signing configuration:

```properties
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Then run:

```bash
./gradlew :app:assembleRelease
```

## Permissions

Knowmad uses the following Android permissions:

| Permission                                   | Purpose                                               |
|----------------------------------------------|-------------------------------------------------------|
| `POST_NOTIFICATIONS`                         | Class progress & suggestion notifications             |
| `READ_CALENDAR` / `WRITE_CALENDAR`           | Two-way academic schedule sync                        |
| `READ_SYNC_SETTINGS` / `WRITE_SYNC_SETTINGS` | Calendar sync adapter management                      |
| `SCHEDULE_EXACT_ALARM`                       | Timed reminders and class alerts                      |
| `RECEIVE_BOOT_COMPLETED`                     | Restore notifications after reboot                    |
| `FOREGROUND_SERVICE*`                        | Background AI agent operation                         |
| Accessibility Service                        | Screen content perception (no data leaves the device) |

## License

Copyright (C) 2025-2026 LTFan (aka [xfqwdsj](https://github.com/xfqwdsj))

This program is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

See [LICENSE.md](LICENSE.md) for details.
