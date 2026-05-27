# APK File Manager

A powerful native Android file manager and APK analyzer inspired by MT Manager. Built with Kotlin, Material Design 3, and modern Android architecture.

## Features

- **Dual-Panel File Browser** — Browse and manage files side by side for efficient file operations
- **APK Viewer & Analyzer** — Inspect APK files with detailed info, manifest, permissions, certificates, and internal file browser
- **Text Editor** — Built-in text editor with syntax highlighting support, find & replace, and line navigation
- **Hex Editor** — View and edit binary files in hexadecimal format with address navigation
- **File Operations** — Copy, cut, paste, rename, delete, and compress/extract archives (ZIP)
- **Bookmarks** — Pin your favorite directories for quick access
- **Search** — Fast file search across directories
- **Dark Mode** — Full support for light and dark themes with Material You design
- **Sort & Filter** — Sort by name, size, date, or type in ascending/descending order
- **Hidden Files** — Toggle visibility of hidden files and folders
- **File Properties** — View detailed file information including size, permissions, and modification date

## Screenshots

<!-- Add screenshots here -->
| File Manager | APK Viewer | Text Editor |
|:---:|:---:|:---:|
| *[Screenshot placeholder]* | *[Screenshot placeholder]* | *[Screenshot placeholder]* |

## Requirements

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34

## Build Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Gradle 8.5

### Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/apk-file-manager.git
   cd apk-file-manager
   ```

2. Open the project in Android Studio

3. Sync Gradle files (File → Sync Project with Gradle Files)

4. Build and run:
   ```bash
   ./gradlew assembleDebug
   ```

   Or from Android Studio: Run → Run 'app'

### Generate Release APK

```bash
./gradlew assembleRelease
```

The signed APK will be generated at `app/build/outputs/apk/release/`.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin 1.9.22 |
| Build System | Gradle 8.5 + AGP 8.2.2 |
| UI Framework | Android Views + Material Design 3 |
| Min SDK | 24 (Android 7.0 Nougat) |
| Target SDK | 34 (Android 14 Upside Down Cake) |
| Architecture | MVVM with ViewModels and LiveData |
| Navigation | AndroidX Navigation Component |
| Coroutines | Kotlinx Coroutines for async operations |
| Archives | Zip4j for ZIP compression/extraction |
| JSON Parsing | Gson |
| Memory | LeakCanary (debug builds) |
| Testing | JUnit 4, Espresso, AndroidX Test |

## Project Structure

```
app/src/main/
├── java/com/hoshiyomi/filemanager/
│   ├── ui/
│   │   ├── main/          # Main activity & file manager fragments
│   │   ├── apkviewer/     # APK viewer activity & fragments
│   │   ├── editor/        # Text & hex editors
│   │   └── settings/      # Settings activity
│   ├── data/              # Data models & repositories
│   └── util/              # Utility classes & Application class
├── res/
│   ├── layout/            # XML layouts
│   ├── values/            # Strings, colors, themes, dimens
│   ├── drawable/          # Vector icons
│   ├── menu/              # Menu XML files
│   ├── xml/               # File provider paths
│   └── values-night/      # Dark theme colors
└── AndroidManifest.xml
```

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -am 'Add new feature'`
4. Push to the branch: `git push origin feature/my-feature`
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
