# PremiumBlocker

A lightweight Velocity plugin that blocks premium (online-mode) players from joining cracked Velocity proxies.

## Features

- **Blocks Premium Players**: Automatically denies connection attempts from players using authenticated Mojang accounts
- **Custom Kick Message**: Shows "Please join from pakmc.xyz" to blocked players
- **Allows Cracked Players**: Permits offline-mode players to join, even if they're using premium usernames (as long as they're not authenticated through Mojang)
- **Lightweight**: Minimal resource usage with simple event-based detection

## How It Works

The plugin listens to the `PreLoginEvent` and checks if the connecting player is in online mode (authenticated through Mojang). If they are premium, the connection is denied with a custom message. Cracked players can join normally.

## Best Logic Approaches

### 1. Current Approach: PreLoginEvent + isOnlineMode() ✅ **RECOMMENDED**
**UPDATED**: Now using Mojang API verification since offline-mode proxies don't authenticate players.

### 1. Current Approach: Mojang API Verification ✅ **RECOMMENDED**
```java
private boolean isPremiumPlayer(String username) {
    URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
    // HTTP 200 = Premium, HTTP 204/404 = Available (cracked)
}
```
**Pros**: Works with offline-mode proxies, accurate detection, cached results
**Cons**: Requires internet connection, small delay for API calls

### 2. Alternative: UUID Pattern Analysis
```java
private boolean isPremiumUUID(UUID playerUUID, String username) {
    UUID crackedUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
    return !playerUUID.equals(crackedUUID);
}
```
**Pros**: No external calls, instant
**Cons**: May not work reliably with offline-mode proxies

### 3. Alternative: Username Blacklist + Manual Management
```java
private final Set<String> premiumUsernames = new ConcurrentHashMap<>();
```

## Compilation Guide (Maven)

### Prerequisites

- Java 11 or higher
- Maven 3.6+ (or use included wrapper)
- Git (optional, for cloning)

### Steps

1. **Clone or download the plugin source code**
   ```bash
   git clone <repository-url>
   cd PremiumBlocker
   ```

2. **Build the plugin using Maven**
   ```bash
   # Using system Maven
   mvn clean package
   
   # Or using Maven wrapper (if available)
   ./mvnw clean package
   ```

3. **Locate the compiled plugin**
   
   The compiled `.jar` file will be located at:
   ```
   target/PremiumBlocker-1.0.0.jar
   ```

### Alternative: Manual Compilation

If you prefer to compile without Maven:

1. **Download dependencies**
   - Download Velocity API from https://nexus.velocitypowered.com/repository/maven-public/
   
2. **Compile manually**
   ```bash
   javac -cp "velocity-api-3.2.0-SNAPSHOT.jar" -d target/classes src/main/java/xyz/pakmc/premiumblocker/PremiumBlocker.java
   
   jar cf PremiumBlocker.jar -C target/classes . -C src/main/resources .
   ```

## Installation

1. Download or compile the `PremiumBlocker-1.0.0.jar` file
2. Place it in your Velocity proxy's `plugins/` folder
3. Restart your Velocity proxy
4. The plugin will automatically start blocking premium players

## Configuration

This plugin requires no configuration - it works out of the box. Premium players will be automatically blocked with the message "Please join from pakmc.xyz".

## Why This Logic is Best

The current implementation using `PreLoginEvent` + `isOnlineMode()` is the most efficient because:

1. **Early Detection**: Catches premium players before they consume server resources
2. **Reliable**: Uses Velocity's built-in authentication detection
3. **Lightweight**: No external API calls or complex logic
4. **Fast**: Immediate response, no waiting for async operations
5. **Accurate**: Directly checks Mojang authentication status

## Other Approaches You Could Consider

### For More Advanced Blocking:
- **Whitelist System**: Allow specific premium players
- **Time-based Blocking**: Block premium players during certain hours
- **Geographic Blocking**: Block based on IP location
- **Launcher Detection**: Detect and block specific Minecraft launchers

### For Analytics:
- **Connection Logging**: Log all connection attempts to database
- **Statistics**: Track blocked vs allowed connections
- **Alerts**: Send Discord/webhook notifications for blocked attempts

## Requirements

- Velocity 3.2.0 or higher
- Java 11 or higher

## License

This plugin is provided as-is for educational and server management purposes.

## GitHub Repository

To push to GitHub:
```bash
git init
git add .
git commit -m "Initial commit: PremiumBlocker Velocity plugin"
git branch -M main
git remote add origin https://github.com/yourusername/PremiumBlocker.git
git push -u origin main
```