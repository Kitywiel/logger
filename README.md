# kitywiel's Logger Addon

A comprehensive Meteor Client addon that provides advanced logging, respawn automation, enemy tracking, and distance monitoring features for Minecraft 1.21.4.

## Features

### 📊 Modules

#### Logger
Periodically sends status updates to Discord with:
- Player coordinates (formatted with commas: `12,345.6`)
- Health and hunger levels
- Current dimension
- Customizable logging interval and title
- Discord webhook integration

#### AutoLog
Emergency disconnect system based on Meteor's official AutoLog with enhanced features:
- Smart health prediction and totem pop detection
- Automatic screenshots on disconnect
- Discord webhook notifications with coordinates
- 32K weapon detection
- Entity monitoring (crystals, players, TNT)

#### AutoRespawn
Automated respawn point management:
- Highway mode: Places respawn anchors along highways
- Baritone integration for automatic pathfinding
- Returns home after placing respawn point
- Configurable distance intervals (default: 5,000 blocks)

#### EnemyAdder
Quick enemy marking system:
- Keybind-based enemy adding (default: R)
- Remove enemies with keybind (default: U)
- Uses crosshair targeting
- Prevents self-targeting

### 👥 Enemy System
Complete enemy tracking and management:
- Red nametag rendering for marked enemies
- GUI tab next to Friends for manual management
- Add enemies by username or UUID
- NBT persistence across sessions
- Mojang API integration for username lookup

### 📈 HUD Elements

#### LoggerInfo
Real-time countdown to next log update:
- Green: More than 60 seconds remaining
- Orange: 30-60 seconds remaining
- Red: Less than 30 seconds remaining

#### RespawnInfo
Distance tracking for next respawn point:
- Shows distance from last respawn anchor
- Color-coded by proximity to next placement
- Integrates with AutoRespawn module

#### DistanceTraveled
Session distance tracker:
- Tracks total distance traveled in current session
- Automatically converts to kilometers (1km+)
- Teleport protection (ignores jumps >100 blocks)
- Comma-formatted for readability: `12,345.6m`

## Installation

1. Download the latest release from the releases page
2. Place the `.jar` file in your Minecraft `mods` folder
3. Ensure you have:
   - Minecraft 1.21.4
   - Fabric Loader 0.16.9+
   - Meteor Client 0.5.8-SNAPSHOT or later
   - Baritone API (for AutoRespawn)

## Configuration

### Discord Webhooks
1. Create a Discord webhook in your server settings
2. Copy the webhook URL
3. In-game, open the module settings (Logger or AutoLog)
4. Paste the webhook URL in the `webhook` setting

### Keybinds
- **Add Enemy**: Default `R` (configurable in EnemyAdder settings)
- **Remove Enemy**: Default `U` (configurable in EnemyAdder settings)

## Usage

### Setting up Logger
1. Enable the Logger module from the "kitywiel's" category
2. Configure your Discord webhook URL
3. Set your desired log interval (default: 300 seconds)
4. Coordinates will be logged with comma formatting

### Using AutoRespawn
1. Enable AutoRespawn from the "kitywiel's" category
2. Configure respawn distance (default: 5,000 blocks)
3. Module will automatically:
   - Detect when you're far enough from last respawn
   - Navigate to highway with Baritone
   - Place respawn anchor
   - Return to starting position

### Managing Enemies
1. **Quick Add**: Look at a player and press `R`
2. **Manual Add**: Click the "Enemies" tab and enter username
3. **Remove**: Press `U` while looking at an enemy, or use GUI
4. Enemies appear with red nametags in-game

### HUD Setup
1. Open HUD Editor (Right Shift by default in Meteor)
2. Find elements in the "kitywiel's" category:
   - LoggerInfo
   - RespawnInfo
   - DistanceTraveled
3. Drag and position as desired

## Technical Details

- **Minecraft Version**: 1.21.4
- **Fabric Loader**: 0.16.9
- **Meteor Client**: 0.5.8-SNAPSHOT
- **Java**: 21
- **Gradle**: 8.12

### Dependencies
- Meteor Client API
- Baritone API
- Fabric API
- Minecraft 1.21.4

## Documentation

See [MODULES_DOCUMENTATION.md](MODULES_DOCUMENTATION.md) for detailed technical documentation of all modules, systems, and components.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- Based on Meteor Client addon template
- AutoLog implementation inspired by Meteor's official AutoLog module
- Developed by kitywiel

## Support

For bug reports or feature requests, please open an issue on GitHub.
