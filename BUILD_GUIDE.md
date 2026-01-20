# Quick Build Guide

## Prerequisites
- Java 21 LTS installed
- Internet connection (for Gradle to download dependencies)

## Build Steps

### 1. Set Java Environment
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"
```

### 2. Build the Project
```powershell
cd "c:\Users\ldwij\Desktop\Kitywiels"
.\gradlew.bat build
```

### 3. Find the Output
The compiled JAR will be in:
```
build/libs/kitywiels-addon-1.0.0.jar
```

### 4. Install to Minecraft
Copy the JAR to your Minecraft mods folder:
```powershell
Copy-Item ".\build\libs\kitywiels-addon-1.0.0.jar" "$env:APPDATA\.minecraft\mods\" -Force
```

Or for PrismLauncher:
```powershell
Copy-Item ".\build\libs\kitywiels-addon-1.0.0.jar" "$env:APPDATA\PrismLauncher\instances\<your-instance>\minecraft\mods\" -Force
```

## Requirements
- Minecraft 1.21.4
- Fabric Loader 0.16.9
- Meteor Client 0.5.8-SNAPSHOT

## What's Included
✅ 5 Modules: Logger, AutoLog, AutoRespawn, AutoUnload, EnemyAdder
✅ 3 HUD Elements: Distance Traveled, Logger Info, Respawn Info
✅ Enemies System with GUI
✅ Discord webhook integration
✅ All configuration files

## Troubleshooting
- If build fails, ensure Java 21 is set correctly
- Delete old `logger-addon-*.jar` files from mods folder
- Check logs for initialization message: "Kitywiel's Logger Addon initialized successfully"

## Documentation
See FULL_PROJECT_REBUILD_DOCUMENTATION.md for complete details.
