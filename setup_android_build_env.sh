#!/bin/bash

# Android APK Build Environment Setup Script for GitHub Codespace
# This script installs Java, Android SDK, and all required tools for building APK

set -e  # Exit on any error

echo "🚀 Starting Android Build Environment Setup for GitHub Codespace..."
echo "=================================================="

# Function to print colored output
print_status() {
    echo -e "\n\033[1;32m✅ $1\033[0m"
}

print_info() {
    echo -e "\033[1;34mℹ️  $1\033[0m"
}

print_error() {
    echo -e "\033[1;31m❌ $1\033[0m"
}

# Update package list
print_info "Updating package list..."
sudo apt-get update -q

# Install Java JDK 17 (recommended for modern Android development)
print_info "Installing OpenJDK 17..."
sudo apt-get install -y openjdk-17-jdk openjdk-17-jre

# Install required packages
print_info "Installing required packages..."
sudo apt-get install -y wget unzip curl git

# Set Java environment
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
echo "export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64" >> ~/.bashrc

print_status "Java JDK 17 installed successfully"
java -version

# Create Android directory
ANDROID_HOME="$HOME/android-sdk"
print_info "Setting up Android SDK in: $ANDROID_HOME"
mkdir -p "$ANDROID_HOME"

# Download Android Command Line Tools
print_info "Downloading Android Command Line Tools..."
cd "$ANDROID_HOME"
wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
unzip -q commandlinetools-linux-9477386_latest.zip
rm commandlinetools-linux-9477386_latest.zip

# Create proper directory structure
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true

# Set Android environment variables
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0"

# Add to bashrc for persistence
echo "export ANDROID_HOME=$HOME/android-sdk" >> ~/.bashrc
echo "export ANDROID_SDK_ROOT=\$ANDROID_HOME" >> ~/.bashrc
echo "export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/build-tools/34.0.0" >> ~/.bashrc

print_status "Android Command Line Tools installed"

# Accept licenses
print_info "Accepting Android SDK licenses..."
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1

# Install required SDK components
print_info "Installing Android SDK components..."
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "cmdline-tools;latest" \
    "emulator" \
    "system-images;android-34;google_apis;x86_64" > /dev/null

print_status "Android SDK components installed successfully"

# Install Gradle (if not already installed)
if ! command -v gradle &> /dev/null; then
    print_info "Installing Gradle..."
    wget -q https://services.gradle.org/distributions/gradle-8.0-bin.zip
    sudo unzip -q gradle-8.0-bin.zip -d /opt/
    sudo ln -sf /opt/gradle-8.0/bin/gradle /usr/local/bin/gradle
    rm gradle-8.0-bin.zip
    print_status "Gradle installed successfully"
else
    print_status "Gradle already installed"
fi

# Create a verification script
cat > "$HOME/verify_android_setup.sh" << 'EOF'
#!/bin/bash
echo "🔍 Verifying Android Build Environment..."
echo "=========================================="

echo -n "Java: "
java -version 2>&1 | head -n 1

echo -n "ANDROID_HOME: "
echo $ANDROID_HOME

echo -n "Android SDK Manager: "
if command -v sdkmanager &> /dev/null; then
    echo "✅ Available"
else
    echo "❌ Not found"
fi

echo -n "ADB: "
if command -v adb &> /dev/null; then
    echo "✅ Available"
else
    echo "❌ Not found"
fi

echo -n "Gradle: "
if command -v gradle &> /dev/null; then
    gradle -version | head -n 1
else
    echo "❌ Not found"
fi

echo -e "\n📱 Installed Android platforms:"
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list_installed | grep "platforms;" | head -5

echo -e "\n🛠️  Installed build tools:"
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list_installed | grep "build-tools;" | head -3

echo -e "\n✅ Setup verification complete!"
EOF

chmod +x "$HOME/verify_android_setup.sh"

# Source bashrc to apply environment variables
source ~/.bashrc 2>/dev/null || true

print_status "Android Build Environment Setup Complete!"
echo ""
echo "🎉 INSTALLATION SUMMARY:"
echo "========================"
echo "✅ OpenJDK 17 installed"
echo "✅ Android SDK installed at: $ANDROID_HOME"
echo "✅ Platform tools installed (adb, etc.)"
echo "✅ Android API 34 platform installed"
echo "✅ Build tools 34.0.0 installed"
echo "✅ Gradle build system ready"
echo ""
echo "📋 NEXT STEPS:"
echo "=============="
echo "1. Extract your ElementFinderBrowser.tar.gz file"
echo "2. Navigate to the project directory: cd ElementFinderBrowser"
echo "3. Make gradlew executable: chmod +x gradlew"
echo "4. Build APK: ./gradlew assembleDebug"
echo ""
echo "🔍 To verify setup anytime, run: ~/verify_android_setup.sh"
echo ""
echo "📱 APK will be generated at: app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "🔄 IMPORTANT: Run 'source ~/.bashrc' or restart terminal to apply environment variables"

# Run verification
print_info "Running setup verification..."
bash "$HOME/verify_android_setup.sh"

echo ""
print_status "🎯 Android Build Environment is ready for APK compilation!"