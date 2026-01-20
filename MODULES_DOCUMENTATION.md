# Kitywiel's Addon - Module Documentation

This document explains all the modules in Kitywiel's addon for Meteor Client.

---

## 📊 Logger Module

**Purpose:** Periodically logs your game information and sends it to a Discord webhook.

### Features:
- **Automatic Logging**: Logs information at configurable intervals (default every minute)
- **Discord Integration**: Sends formatted embeds to your Discord webhook
- **Customizable Data**: Choose exactly what information to log

### Settings:

#### Webhook Group:
- **webhook-url**: Your Discord webhook URL where logs will be sent

#### General Group:
- **log-interval**: How often to send logs (in minutes, default: 1.0, range: 0.1-10.0)
- **log-coordinates**: Logs your current X, Y, Z position
- **log-distance**: Logs how far you've moved since the last update
- **log-health**: Logs your current health and max health
- **log-food**: Logs your hunger/food level
- **log-gear-health**: Logs durability of all your armor pieces and held item
- **log-tps**: Logs the server's TPS (ticks per second)
- **log-ping**: Logs your connection latency to the server

#### Items Group:
- **tracked-items**: Select which items to track the count of
- **log-item-count**: Enable/disable logging of tracked item counts

### How It Works:
1. Every X minutes (configurable), the module collects enabled information
2. Formats it into a nice Discord embed with emoji icons
3. Sends it to your webhook URL
4. You get a notification in Discord with all your stats

### Example Output:
```
⚡ Status Update - [12345, 64, -9876]
📍 Coordinates: X: 12345.0, Y: 64.0, Z: -9876.0
🚶 Distance Moved: 523.2 blocks
❤️ Health: 20.0/20.0
🍖 Food: 20/20
⚙️ TPS: 20.0
📡 Ping: 45 ms
🛡️ Gear Durability:
  Helmet: 363/363 (100%)
  Chestplate: 528/528 (100%)
```

---

## 🚨 Auto Log Module

**Purpose:** Automatically disconnects you from the server when you're in danger and sends a screenshot to Discord.

**Based on:** Meteor Client's official AutoLog module with added Discord webhook and screenshot features.

### Features:
- **Smart Health Monitoring**: Predicts incoming damage before it hits you
- **Totem Pop Detection**: Tracks how many times your totem pops
- **32K Detection**: Detects instant-kill weapons (32k enchanted items)
- **Entity Detection**: Monitors nearby crystals and other dangerous entities
- **Only Trusted Mode**: Auto-logs when non-friends appear
- **Smart Toggle**: Re-enables automatically when health recovers
- **Screenshot Capture**: Takes a screenshot before disconnecting
- **Discord Notification**: Sends screenshot + stats to webhook

### Settings:

#### Webhook Group:
- **webhook-url**: Discord webhook URL for emergency notifications
- **enable-screenshot**: Toggle screenshot capture on/off (default: ON)

#### General Group:
- **health**: Disconnect when health drops to or below this value (0-19, default: 6)
  - Set to 0 to disable health checking
  - Does NOT include absorption hearts
- **predict-incoming-damage**: Smart damage prediction (default: ON)
  - Calculates potential incoming damage from nearby threats
  - Disconnects BEFORE your health actually drops
  - Uses Meteor's `PlayerUtils.possibleHealthReductions()` algorithm
- **totem-pops**: Disconnect after this many totem pops (0-27, default: 0)
  - Set to 0 to disable totem pop checking
- **only-trusted**: Disconnect when non-friend players appear (default: OFF)
  - Uses Meteor's friends system
  - Triggers on ANY player not in your friends list
- **32K**: Anti-32k protection (default: OFF)
  - Detects players within 8 blocks who can instantly kill you
  - Uses `DamageUtils.getAttackDamage()` to calculate threat level
- **smart-toggle**: Auto re-enable when health recovers (default: OFF)
  - Disables module on logout
  - Subscribes health listener to event bus
  - Re-enables when health > threshold
- **toggle-off**: Disable module after logout (default: ON)
- **toggle-auto-reconnect**: Disable Auto Reconnect after logout (default: ON)
  - Prevents accidental reconnection after emergency logout

#### Entities Group:
- **entities**: Select entity types to monitor (default: End Crystal)
  - Tracks specified entities within range
  - Triggers disconnect when thresholds exceeded
- **use-total-count**: Count all entities together vs individually (default: ON)
  - ON: Counts total number of ALL selected entity types
  - OFF: Counts each entity type separately
- **combined-entity-threshold**: Total entity count limit (1-32, default: 10)
  - Only visible when use-total-count is ON
  - Disconnects when total entities >= this value
- **individual-entity-threshold**: Per-entity count limit (1-16, default: 2)
  - Only visible when use-total-count is OFF
  - Disconnects when any single entity type >= this value
- **range**: Entity detection range in blocks (1-16, default: 5)

### How It Works:

#### Smart Health Monitoring:
1. Every tick, checks current health: `playerHealth <= health.get()`
2. If smart prediction enabled:
   - Calculates: `health + absorption - possibleHealthReductions()`
   - `possibleHealthReductions()` scans for:
     - Nearby crystals that could explode
     - Players with weapons in range
     - TNT, anchors, and other explosives
   - Disconnects if predicted health < threshold

#### Totem Pop Detection:
1. Listens for `EntityStatusS2CPacket` (status 35 = totem use)
2. Verifies entity is you
3. Increments counter
4. If counter >= threshold, triggers disconnect

#### 32K Detection:
1. Scans all players within 8 blocks every tick
2. Uses `DamageUtils.getAttackDamage(attacker, you)`
3. If damage > (health + absorption), triggers disconnect
4. Bypasses need to actually take damage

#### Only Trusted Mode:
1. Scans all players in render distance
2. Checks each against `Friends.get().isFriend(player)`
3. If non-friend found, triggers disconnect with player name

#### Entity Detection:
1. Scans world for selected entity types within range
2. If `use-total-count` enabled:
   - Counts total entities of ALL selected types
   - Compares to `combined-entity-threshold`
3. If `use-total-count` disabled:
   - Counts each entity type separately using `Object2IntMap`
   - Triggers if ANY type exceeds `individual-entity-threshold`

#### Smart Toggle System:
1. On low-health logout:
   - Disables module
   - Subscribes `StaticListener` to event bus
2. Health listener runs every tick:
   - Checks if health > threshold
   - If recovered, re-enables module
   - Unsubscribes itself from event bus

#### Disconnect Process:
1. Sets `isLoggingOut` flag to prevent duplicate triggers
2. Builds disconnect message with reason
3. If Auto Reconnect active, disables it
4. If screenshot enabled:
   - Spawns thread to capture screenshot
   - Uses `mc.execute()` to run on render thread
   - Captures framebuffer from OpenGL (RGBA format)
   - Converts pixels to BufferedImage (flips Y-axis)
   - Collects player coordinates (X, Y, Z)
   - Collects current dimension name
   - Sends to Discord as multipart form-data with coordinates and dimension
5. After 100ms delay:
   - Sends `DisconnectS2CPacket` with formatted text
   - Clean server-side disconnect

### Example Discord Message:
```
**AutoLog Triggered**
Reason: Health was lower than 6.
Totem Pops: 2
Health: 4.5
📍 Location: 12345, 64, -9876
🌍 Dimension: the_nether
[Screenshot attached showing what you saw before disconnect]
```

### Advanced Examples:

**Crystal PvP Protection:**
- Set `entities` to End Crystal
- `combined-entity-threshold` = 5
- `range` = 6
- Result: Logs when 5+ crystals within 6 blocks

**32K Server:**
- Enable `32K`
- Enable `only-trusted`
- Result: Logs on any non-friend with lethal weapon

**Smart Prediction:**
- `health` = 10
- `predict-incoming-damage` = ON
- Result: Logs when predicted damage would drop you below 10 HP

---

## 🛏️ Auto Respawn Module

**Purpose:** Automatically places respawn points (beds/anchors) along highways at set intervals.

### Features:
- **Automatic Placement**: Places respawn every X thousand blocks
- **Mode Selection**: Overworld (bed) or Nether (respawn anchor)
- **Highway Integration**: Automatically moves off highway to place
- **Baritone Support**: Uses Baritone for automated travel
- **Ender Chest Placement**: Places ender chest next to respawn
- **Discord Notifications**: Sends updates to webhook

### Settings:

#### General Group:
- **mode**: Choose Overworld (bed) or Nether (respawn anchor)
- **respawn-distance**: Distance in thousands of blocks before placing new respawn
  - Range: 1-1000 (multiplied by 1000 in code)
  - Example: Setting = 5 → places respawn every 5,000 blocks
- **range**: How far from highway to place respawn point (0-1000 blocks)

#### Webhook Group:
- **webhook-url**: Discord webhook URL for notifications
- **notify-on-place**: Send notification when respawn is successfully set (default: ON)
- **notify-on-start**: Send notification when starting placement (default: OFF)

#### Highway Group:
- **highway-mode**: Enable Baritone automation
- **disable-modules**: Select modules to disable during travel

### How It Works:

#### Distance Checking:
1. Continuously monitors your position
2. Calculates distance from last respawn point
3. When distance >= (respawnDistance × 1000), triggers placement

#### Manual Mode (highway-mode OFF):
1. Tries to place at current location
2. You must have items in hotbar
3. Places → Interacts → Waits for chat confirmation

#### Highway Mode (highway-mode ON):
1. Saves current position as `startPos`
2. Disables selected modules
3. Calculates target position:
   - If on X highway (Z ≈ 0): moves `range` blocks in Z direction
   - If on Z highway (X ≈ 0): moves `range` blocks in X direction
4. **Uses Baritone to walk to target position**
5. Arrives → Places respawn → Places ender chest
6. **Uses Baritone to walk back to `startPos`**
7. **Re-enables disabled modules** (AFTER arriving back at start position)

**Note:** Baritone is ONLY used for traveling to and from the respawn placement location. All block placement, interaction, and decision-making is handled by the module itself. Modules remain disabled during the entire journey and are only re-enabled once you've safely returned to your starting position.

#### Overworld Mode (Bed):
1. Finds bed in hotbar (any color)
2. Finds suitable placement location (solid block below, air above)
3. Places bed
4. Right-clicks bed to set spawn
5. Waits for "respawn point set" chat message
6. Places ender chest nearby

#### Nether Mode (Respawn Anchor):
1. Finds respawn anchor in hotbar
2. Finds glowstone in hotbar
3. Places respawn anchor
4. Switches to glowstone
5. Right-clicks anchor twice to charge (adds 2 glowstone)
6. Switches to any non-glowstone item
7. Right-clicks anchor to set spawn
8. Waits for "respawn point set" chat message
9. Places ender chest nearby

#### Message Detection:
- Listens for chat messages containing:
  - "respawn point set"
  - "spawn point set"
  - "bed" + "set"
- Retries up to 3 times if message not received within 3 seconds

### Example Discord Notifications:

**Start Notification (if enabled):**
```
Starting Respawn Placement
Mode: Nether
Current Position: 12000, 120, 45
Target Range: 10 blocks from highway
```

**Success Notification:**
```
✅ Respawn Point Set Successfully
Mode: Nether
Location: 12010, 120, 55
Dimension: the_nether
Distance from last: 5234.0 blocks
```

---

## ⚔️ Enemy Adder Module

**Purpose:** Quickly add or remove enemies using keybinds while looking at players.

### Features:
- **Quick Add**: Add the player you're looking at to enemies with a keybind
- **Quick Remove**: Remove the player you're looking at from enemies with a keybind
- **No GUI Required**: Instantly mark enemies during combat or exploration
- **Visual Feedback**: Chat messages confirm additions/removals

### Settings:

#### General Group:
- **add-enemy-keybind**: Press this key while looking at a player to add them as an enemy (default: none)
  - Set your preferred key in module settings
  - Works when crosshair is on a player
- **remove-enemy-keybind**: Press this key while looking at a player to remove them from enemies (default: none)
  - Useful for quickly unmarking players
  - Instant removal without opening GUI

### How to Use:

#### Adding Enemies:
1. Enable the Enemy Adder module
2. Set your **add-enemy-keybind** in settings (choose any key you prefer)
3. Look at a player (crosshair over them)
4. Press your add enemy key
5. Player is instantly added to enemies list
6. Their name turns red immediately

#### Removing Enemies:
1. Set your **remove-enemy-keybind** in settings (choose any key you prefer)
2. Look at an enemy player
3. Press your remove enemy key
4. Player is removed from enemies list
5. Their name returns to normal color

### How It Works:

1. **Crosshair Detection**: Uses `mc.crosshairTarget` to detect entity
2. **Player Validation**: Checks if entity is a player (not mob)
3. **Self-Check**: Prevents you from adding yourself
4. **Enemy System**: Directly adds/removes from Enemies system
5. **Instant Feedback**: Shows success/error message in chat

### Chat Messages:

- **Success Add**: `Added §cPlayerName§r to enemies!` (red name)
- **Already Added**: `PlayerName is already in your enemies list!`
- **Success Remove**: `Removed PlayerName from enemies!`
- **Not Enemy**: `PlayerName is not in your enemies list!`
- **Not Looking**: `You must be looking at a player!`
- **Self-Add**: `You cannot add yourself as an enemy!`

### Example Use Cases:

**Combat Situations:**
- Someone attacks you → Look at them → Press key → Marked
- Quick marking during PvP without opening menus
- Mark multiple enemies rapidly in group fights

**Exploration:**
- Spot suspicious player → Mark them instantly
- See clan tag you recognize → Quick mark
- Mark and continue moving without stopping

**Convenience:**
- No need to remember names and type them
- Works even if you can't see their full name
- Instant marking while riding, flying, or moving

---

## 🎯 Enemies System

**Purpose:** Track enemy players and make them appear with red nametags in-game.

### Features:
- **Enemy List Management**: Add/remove players from your enemies list
- **Red Nametags**: Enemy players appear with bright red names in-game
- **UUID-Based Tracking**: Tracks by UUID, works even if players change names
- **Persistent Storage**: Enemy list saved across game sessions
- **GUI Integration**: Dedicated tab in Meteor Client GUI

### How to Use:

#### Adding Enemies:
1. Open Meteor Client GUI (Right Shift by default)
2. Click on **"Enemies"** tab (iron sword icon)
3. Type the player's username in the text box
4. Click **"Add"** button
5. The addon will fetch the player's UUID from Mojang API
6. Enemy will be added to your list

#### Removing Enemies:
1. Open the Enemies tab
2. Find the enemy in the list
3. Click the **minus (-)** button next to their name
4. Enemy will be removed immediately

### How It Works:

#### UUID Fetching:
1. When you add an enemy by name, the addon queries Mojang's API
2. Retrieves the player's UUID (unique identifier)
3. Stores both name and UUID in the system

#### Red Nametag Rendering:
1. Mixin intercepts entity label rendering
2. Checks if the player entity is in your enemies list
3. If yes, applies red color (0xFF0000) to their nametag
4. Works in real-time for all enemy players in render distance

#### Persistent Storage:
1. Enemy list saved to NBT file in Meteor's data directory
2. Automatically loads on game start
3. Survives game restarts and client updates

### Features:

- **Real-time Updates**: Names turn red immediately after adding
- **Visual Identification**: Instantly spot enemies in crowded areas
- **No Performance Impact**: Efficient UUID-based lookups
- **Mojang API Integration**: Automatic username → UUID conversion
- **Clean GUI**: Matches Meteor Client's design language

### Example Use Cases:

**PvP Servers:**
- Mark known threats with red names
- Quickly identify enemies in combat
- Track rival faction members

**Anarchy Servers:**
- Mark players who attacked you
- Identify members of enemy groups
- Coordinate with team using shared enemy lists

**General Multiplayer:**
- Track players you want to avoid
- Mark troublemakers or griefers
- Keep tabs on suspicious players

---

## 📊 HUD Elements

### Logger Info HUD

**Purpose:** Shows countdown until next Logger update.

#### Features:
- **Real-time Countdown**: Shows minutes and seconds until next log
- **Color-Coded**: Changes color based on time remaining
  - 🟢 Green: More than 60 seconds
  - 🟠 Orange: 30-60 seconds
  - 🔴 Red: Less than 30 seconds
- **Status Display**: Shows "Disabled" when Logger module is off

#### How to Add:
1. Open Meteor Client GUI
2. Go to **HUD** tab
3. Find **"logger-info"** in the list
4. Click to add it to your HUD
5. Drag to position it where you want
6. Customize colors and position as needed

#### Display Format:
- Active: `Next Logger: 03:45` (with color)
- Disabled: `Logger: Disabled` (gray)

---

### Respawn Info HUD

**Purpose:** Shows distance remaining until next respawn placement.

#### Features:
- **Real-time Distance**: Shows blocks until next respawn point
- **Color-Coded**: Changes color based on distance remaining
  - 🟢 Green: More than 1000 blocks
  - 🟠 Orange: 500-1000 blocks
  - 🔴 Red: Less than 500 blocks
- **Ready Alert**: Shows "Ready" when respawn should be placed
- **Status Display**: Shows "Disabled" when AutoRespawn is off

#### How to Add:
1. Open Meteor Client GUI
2. Go to **HUD** tab
3. Find **"respawn-info"** in the list
4. Click to add it to your HUD
5. Drag to position it where you want
6. Customize colors and position as needed

#### Display Format:
- Active: `Next Respawn: 2340 blocks` (with color)
- Ready: `Next Respawn: Ready` (green)
- Disabled: `AutoRespawn: Disabled` (gray)
- No Last Respawn: `Next Respawn: Ready` (green)

---

### Distance Traveled HUD

**Purpose:** Shows total distance traveled in the current session.

#### Features:
- **Real-time Tracking**: Continuously tracks distance as you move
- **Smart Display**: Automatically switches between meters and kilometers
- **Color-Coded**: Changes color based on distance traveled
  - ⚪ White: 0-999 meters
  - 🟢 Green: 1,000-9,999 meters
  - 🔵 Blue: 10-999 kilometers
  - 🟣 Magenta: 1,000+ kilometers
- **Teleport Protection**: Ignores unrealistic jumps (prevents teleport from inflating count)
- **Session-Based**: Resets when you join a new world or restart game

#### How to Add:
1. Open Meteor Client GUI
2. Go to **HUD** tab
3. Find **"distance-traveled"** in the kitywiel's category
4. Click to add it to your HUD
5. Drag to position it where you want

#### Display Format:
- Meters: `Distance: 523.4m`
- Kilometers: `Distance: 15.3km`

#### Use Cases:
- **Highway Travel**: Track how far you've traveled on highways
- **Exploration**: See total exploration distance
- **Elytra Flights**: Track flight distances
- **Mining Sessions**: See how far you've mined
- **AFK Sessions**: Track distance moved while AFK walking

---

## 🎯 All Modules & Systems

- **Modules**: All four modules are grouped under the **"kitywiel's"** category in Meteor Client
  - Logger
  - Auto Log
  - Auto Respawn
  - Enemy Adder
- **Systems**: Enemies system accessible via dedicated **"Enemies"** tab (iron sword icon)
- **HUD Elements**: Three HUD elements available in the HUD editor (kitywiel's category)
  - Logger Info - Countdown to next log
  - Respawn Info - Distance to next respawn
  - Distance Traveled - Total distance in current session

---

## 💡 Usage Tips

### Logger:
- Set a unique webhook per player if running multiple accounts
- Adjust log-interval based on how active you are
- Disable log-distance if you're AFK to reduce spam

### Auto Log:
- **Based on Meteor's official AutoLog** - Battle-tested and reliable
- Test health threshold in safe environment first (recommend 6-10)
- Enable **predict-incoming-damage** for proactive protection
- Keep enable-screenshot ON for proof of what killed you
- **Coordinates are automatically sent** with every autolog notification for easy recovery
- **Smart toggle** is useful if you frequently heal and want module to auto-enable
- **32K protection** essential on anarchy servers
- **Only trusted** useful in populated areas
- **Entity detection** perfect for crystal PvP:
  - Set entities to End Crystal
  - Use combined-entity-threshold = 5-10 for mass crystal scenarios
  - Adjust range based on explosion distance
- **Toggle Auto Reconnect** prevents accidental reconnection after emergency logout

### Auto Respawn:
- **Highway mode**: Baritone is ONLY used for walking to/from the placement location, not for any placement logic
- Modules stay disabled for the ENTIRE trip and are **re-enabled only after arriving back** at start position
- This ensures your protective modules stay off during both the outbound and return journey
- In highway mode, disable modules that might interfere with Baritone's pathfinding (flight, speed, etc.)
- Keep beds/anchors + glowstone + ender chests stocked in hotbar
- Use respawn-distance wisely: too small = spam, too large = long travel if you die
- range should be enough to be off highway but not too far (5-20 blocks recommended)
- Test in singleplayer first to understand the placement logic

### Enemies:
- **Quick identification**: Red names make enemies instantly recognizable
- **Use Enemy Adder module** for instant marking with keybinds during combat
- Set custom keybinds that work best for your playstyle (avoid conflicts with other modules)
- Add enemies immediately after encounters to remember them
- Works best when combined with Friends system for clear ally/enemy distinction
- Enemy list syncs across all your clients if you share the Meteor config folder
- Use descriptive usernames when adding to avoid confusion
- Regularly review and clean up your enemy list to remove inactive players

### Enemy Adder:
- **Must be enabled** for keybinds to work
- **Choose your own keybinds** - set keys that feel natural and don't conflict
- Works while moving, flying, or in any situation
- Player must be visible and in crosshair range
- Combines perfectly with Enemies tab for manual additions
- Use during combat to mark threats without stopping

### HUD Elements:
- **Logger Info**: Perfect for tracking when to expect next webhook notification
- **Respawn Info**: Helps plan when to stop for respawn placement
- **Distance Traveled**: Great for tracking exploration and travel sessions
- Color changes warn you when events are approaching
- Position HUD elements anywhere on screen for optimal visibility
- Disable modules to hide corresponding HUD elements
- Distance resets when joining new worlds or restarting game

---

## ⚠️ Important Notes

1. **Webhook Security**: Never share your webhook URLs - they give full posting access to that channel
2. **Baritone Dependency**: Auto Respawn highway mode requires Baritone ONLY for walking to/from the respawn location - all placement logic is independent
3. **Hotbar Management**: Auto Respawn expects items in hotbar (slots 0-8), not main inventory
4. **Chat Detection**: Auto Respawn relies on server chat messages - may not work on all servers
5. **Screenshot Performance**: Taking screenshots may cause brief lag spike
6. **Enemies System**: Tracks by UUID, so enemies remain marked even if they change usernames
7. **Mojang API**: Enemy adding requires internet connection to fetch UUIDs from Mojang
8. **Testing**: Always test in safe environments before using on important servers

---

## 🐛 Troubleshooting

### Logger not sending:
- Check webhook URL is correct and active
- Verify at least one log option is enabled
- Check Discord webhook rate limits

### Auto Log not triggering:
- Verify you actually took damage / popped totem
- Check health threshold is set correctly (0 = disabled)
- Make sure module is toggled ON
- If using predict-incoming-damage, it may log before visible damage
- Check totem-pops is > 0 if you want totem pop detection
- For 32K protection, ensure it's enabled and threats are within 8 blocks
- Entity detection requires entities to be within configured range

### Auto Respawn not placing:
- Ensure correct items are in HOTBAR (not inventory)
- Check that suitable placement position exists
- Verify chat messages aren't being filtered
- In highway mode, confirm Baritone is installed and working

### Baritone not moving:
- Check Baritone is installed
- Verify highway-mode is enabled
- Make sure path is possible (not blocked)

### Enemies not showing red:
- Verify mixin is loaded (check logs for mixin errors)
- Make sure player is actually in render distance
- Confirm player UUID hasn't changed (rare)
- Try removing and re-adding the enemy
- Check that enemy is properly added in the Enemies tab

### Can't add enemy:
- Verify username is spelled correctly
- Check internet connection (needs to reach Mojang API)
- Ensure player account exists and isn't migrated incorrectly
- Try again after a few seconds (API rate limiting)

---

## 📝 Technical Details

### Systems:
- **Enemies**: UUID-based player tracking system with NBT persistence
  - Storage: `Systems.get(Enemies.class)`
  - Data structure: `Map<UUID, Enemy>`
  - File format: NBT compound with enemy list

### Event Handlers Used:
- **Logger**: `TickEvent.Post` - Runs every game tick to count intervals
- **Auto Log**: `PacketEvent.Receive` - Detects totem pops via EntityStatusS2CPacket, `TickEvent.Post` - Health/threat monitoring, smart damage prediction
- **Auto Respawn**: `TickEvent.Post` - Distance checking, `ReceiveMessageEvent` - Chat detection

### Mixins:
- **EntityRendererMixin**: Modifies `renderLabelIfPresent` to change enemy name colors
  - Target: `net.minecraft.client.render.entity.EntityRenderer`
  - Injection: `@ModifyVariable` on label parameter
  - Color: 0xFF0000 (bright red)

### HTTP Communication:
- All modules use Java's `HttpClient` with 10-second timeout
- Requests run in separate threads to avoid blocking game
- Discord embeds use standard webhook JSON format
- Mojang API: `https://api.mojang.com/users/profiles/minecraft/{username}`

### Minecraft APIs Used:
- `mc.player` - Player data access
- `mc.world` - World/entity queries
- `mc.interactionManager` - Block placement/interaction
- Baritone API - **Only for pathfinding and walking** (to/from respawn location in highway mode)

### GUI Components:
- **EnemiesScreen**: Extends `WindowScreen`, uses `WTable` for layout
- **EnemiesTab**: Registered in `Tabs.add()` with iron sword icon
- Widgets: `WTextBox` (input), `WButton` (add), `WMinus` (remove), `WLabel` (display)

---

## 📜 License

This addon is licensed under the MIT License. See LICENSE file for details.

---

**Created by kitywiel for Minecraft 1.21.4 with Meteor Client**
