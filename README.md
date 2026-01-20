# Kitywiel's Logger Addon

A Meteor Client addon for Minecraft 1.21.4 that adds logging, auto-respawn, and enemy tracking features.

## Features

- **Logger Module**: Logs game statistics to Discord webhooks
- **AutoLog Module**: Automatically disconnects on low health/totem pops
- **AutoRespawn Module**: Places respawn points along highways
- **AutoUnload Module**: Moves items into shulkers and crafts blocks
- **EnemyAdder Module**: Add/remove enemies using keybinds
- **3 HUD Elements**: Distance Traveled, Logger Info, Respawn Info
- **Enemies System**: Track and mark enemy players

## Requirements

- Minecraft 1.21.4
- Fabric Loader 0.16.9
- Meteor Client 0.5.8-SNAPSHOT
- Java 21

## Building

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/kitywiels-addon-1.0.0.jar`

## Installation

Copy the JAR file to your `.minecraft/mods` folder.

## License

MIT License - See LICENSE file for details.
