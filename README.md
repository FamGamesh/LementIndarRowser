# Element Finder Browser - Android APK Project

## Overview
This is a complete Android project for the **Element Finder Browser** app - an intelligent web element selector tool that helps users find and extract CSS/XPath selectors from web pages.

## Features
- **Intelligent Selector Detection**: Automatically generates optimal CSS selectors with XPath fallback
- **Long Press Detection**: Hold any webpage element for 2 seconds to view its selector
- **Macro Recording**: Record click sequences and save selectors to text files
- **Desktop Mode**: Toggle between mobile and desktop browser modes
- **Smart URL/Search Processing**: Enter URLs directly or search terms for Google search

## Technical Details
- **Package Name**: com.elementfinder.browser
- **App Name**: Element Finder Browser
- **Target SDK**: 34 (Android 14)
- **Minimum SDK**: 21 (Android 5.0)
- **Architecture**: Native Android with WebView + JavaScript injection

## Custom Icon Configuration

The app now uses your custom 512x512px icon, converted and optimized for all Android densities:

### Icon Densities Created:
- **mdpi**: 48x48px (baseline density)
- **hdpi**: 72x72px (1.5x density) 
- **xhdpi**: 96x96px (2x density)
- **xxhdpi**: 144x144px (3x density)
- **xxxhdpi**: 192x192px (4x density - highest quality)

### Icon Types:
- **Standard Icons**: Square launcher icons for all densities
- **Round Icons**: Circular versions for devices that support round icons
- **Adaptive Icons**: Modern Android 8.0+ adaptive icons with background and foreground layers
- **Legacy Support**: Full compatibility with older Android versions

### Automatic Quality Selection:
- Android automatically selects the highest appropriate density icon for the device
- On high-DPI devices, the xxxhdpi (192x192) version provides maximum quality
- Adaptive icons provide consistent appearance across different device themes
- The original 512x512px quality is preserved in the highest density versions

## Building the APK in GitHub Codespace

### Prerequisites
1. Java Development Kit (JDK) 8 or higher
2. Android SDK and build tools
3. GitHub Codespace environment

### Build Instructions

1. **Extract the project**:
   ```bash
   tar -xzf ElementFinderBrowser.tar.gz
   cd ElementFinderBrowser
   ```

2. **Set up Android SDK** (if not already configured):
   ```bash
   # Set ANDROID_HOME environment variable
   export ANDROID_HOME=/path/to/android/sdk
   export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
   ```

3. **Build the APK**:
   ```bash
   # Make gradlew executable
   chmod +x gradlew
   
   # Build debug APK
   ./gradlew assembleDebug
   
   # Build release APK (unsigned)
   ./gradlew assembleRelease
   ```

4. **Locate the APK**:
   - Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
   - Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Advanced Build Options

- **Clean build**: `./gradlew clean`
- **Build with specific variant**: `./gradlew assembleDebug` or `./gradlew assembleRelease`
- **Generate signed APK**: Requires keystore configuration in `app/build.gradle`

## Project Structure
```
ElementFinderBrowser/
├── app/
│   ├── src/main/
│   │   ├── java/com/elementfinder/browser/
│   │   │   ├── MainActivity.java          # Main entry activity
│   │   │   └── WebViewActivity.java       # Web browser with selector detection
│   │   ├── res/
│   │   │   ├── layout/                    # UI layouts
│   │   │   ├── values/                    # Strings, colors, themes
│   │   │   ├── drawable/                  # Icons and graphics
│   │   │   └── menu/                      # App menus
│   │   └── AndroidManifest.xml
│   ├── build.gradle                       # App-level build configuration
│   └── proguard-rules.pro
├── gradle/wrapper/                        # Gradle wrapper files
├── build.gradle                           # Project-level build configuration
├── settings.gradle                        # Project settings
└── README.md                              # This file
```

## Key Features Implementation

### 1. Intelligent Selector Generation
- **CSS Priority**: ID → Class → Attributes → nth-child
- **XPath Fallback**: Generated when CSS selectors aren't unique enough
- **Uniqueness Validation**: Ensures selectors match only the target element

### 2. JavaScript Injection
- Advanced DOM manipulation for element detection
- Real-time selector generation and validation
- Touch coordinate to element mapping

### 3. Macro Recording
- Captures click sequences during recording mode
- Saves selectors to timestamped text files
- Internal storage in app-specific directory

### 4. File Storage
- **Location**: Internal storage → "Element Finder Browser" folder
- **Format**: Plain text files with timestamps
- **Content**: Step-by-step selector sequences

## Permissions Required
- `INTERNET`: Web browsing functionality
- `ACCESS_NETWORK_STATE`: Network status checking
- `WRITE_EXTERNAL_STORAGE`: File saving (Android < 10)
- `READ_EXTERNAL_STORAGE`: File access (Android < 10)

## Troubleshooting

### Common Build Issues
1. **Java Version**: Ensure JDK 8+ is installed
2. **Android SDK**: Verify ANDROID_HOME is set correctly
3. **Gradle Wrapper**: Ensure gradlew has execute permissions
4. **Dependencies**: Run `./gradlew --refresh-dependencies` if needed

### Runtime Issues
1. **WebView**: Requires network connectivity for web browsing
2. **File Permissions**: Modern Android versions handle storage permissions automatically
3. **JavaScript**: Ensure WebView JavaScript is enabled (handled automatically)

## Development Notes
- The app uses WebView with JavaScript injection for DOM manipulation
- Selector algorithms prioritize readability and uniqueness
- Touch handling includes precise timing for long-press detection
- File I/O operations are performed on internal storage for security

## Support
This project includes all necessary files for building in GitHub Codespace. The selector detection algorithms are optimized for modern web standards and provide intelligent fallback mechanisms for maximum compatibility.