package xyz.pakmc.premiumblocker;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Plugin(
    id = "premiumblocker",
    name = "PremiumBlocker",
    version = "1.0.0",
    description = "Blocks premium players from joining cracked Velocity proxy",
    authors = {"PakMC"}
)
public class PremiumBlocker {
    
    private final ProxyServer server;
    private final Logger logger;
    private final ConcurrentHashMap<String, Boolean> premiumCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = TimeUnit.MINUTES.toMillis(5); // 5 minutes cache
    
    @Inject
    public PremiumBlocker(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }
    
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("PremiumBlocker has been enabled! Now blocking authenticated premium players...");
        logger.info("Debug mode is ON - check console for detailed connection info");
    }
    
    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        
        logger.info("=== CONNECTION ATTEMPT ===");
        logger.info("Username: {}", username);
        logger.info("Connection IP: {}", event.getConnection().getRemoteAddress());
        
        // For offline-mode proxies, we need to check if the player is actually premium
        // by checking if they have a valid premium session
        boolean isPremium = checkIfPremiumAuthenticated(username);
        
        logger.info("Premium check result for {}: {}", username, isPremium);
        
        if (isPremium) {
            logger.info("BLOCKING: {} is connecting with premium authentication", username);
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                Component.text("Please join from pakmc.xyz", NamedTextColor.RED)
            ));
        } else {
            logger.info("ALLOWING: {} is connecting without premium authentication", username);
        }
    }
    
    @Subscribe
    public void onLogin(LoginEvent event) {
        String username = event.getPlayer().getUsername();
        UUID playerUUID = event.getPlayer().getUniqueId();
        
        logger.info("=== LOGIN EVENT DEBUG ===");
        logger.info("Username: {}", username);
        logger.info("Player UUID: {}", playerUUID);
        logger.info("Is Online Mode: {}", event.getPlayer().isOnlineMode());
        
        // Generate what a cracked UUID would look like
        UUID crackedUUID = generateCrackedUUID(username);
        logger.info("Expected Cracked UUID: {}", crackedUUID);
        logger.info("UUIDs match (cracked): {}", playerUUID.equals(crackedUUID));
        
        // Check if this is a premium account with valid session
        boolean isPremiumWithSession = checkIfPremiumAuthenticated(username);
        logger.info("Has Premium Session: {}", isPremiumWithSession);
        
        // Block if they have premium authentication
        if (isPremiumWithSession) {
            logger.info("BLOCKING: Player has premium session - disconnecting");
            event.getPlayer().disconnect(
                Component.text("Please join from pakmc.xyz", NamedTextColor.RED)
            );
        } else {
            logger.info("ALLOWING: Player does not have premium session");
        }
    }
    
    private boolean checkIfPremiumAuthenticated(String username) {
        // Check cache first
        String cacheKey = username.toLowerCase();
        Long timestamp = cacheTimestamps.get(cacheKey);
        if (timestamp != null && (System.currentTimeMillis() - timestamp) < CACHE_DURATION) {
            Boolean cached = premiumCache.get(cacheKey);
            if (cached != null) {
                logger.info("Using cached result for {}: {}", username, cached);
                return cached;
            }
        }
        
        try {
            // Method 1: Check if account exists in Mojang database
            URL profileUrl = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection profileConnection = (HttpURLConnection) profileUrl.openConnection();
            profileConnection.setRequestMethod("GET");
            profileConnection.setConnectTimeout(3000);
            profileConnection.setReadTimeout(3000);
            
            int profileResponse = profileConnection.getResponseCode();
            logger.info("Mojang profile API response for {}: {}", username, profileResponse);
            
            if (profileResponse != 200) {
                // Account doesn't exist, definitely not premium
                cacheResult(cacheKey, false);
                return false;
            }
            
            // Read the UUID from the response
            BufferedReader reader = new BufferedReader(new InputStreamReader(profileConnection.getInputStream()));
            String response = reader.readLine();
            reader.close();
            logger.info("Mojang profile response: {}", response);
            
            // Extract UUID from JSON response (simple parsing)
            if (response != null && response.contains("\"id\"")) {
                String uuidStr = response.split("\"id\":\"")[1].split("\"")[0];
                logger.info("Premium UUID from Mojang: {}", uuidStr);
                
                // Method 2: Check session server (this is the key!)
                // If they're connecting through premium launcher, they should have a valid session
                boolean hasValidSession = checkSessionServer(username, uuidStr);
                logger.info("Has valid session for {}: {}", username, hasValidSession);
                
                cacheResult(cacheKey, hasValidSession);
                return hasValidSession;
            }
            
            profileConnection.disconnect();
            
        } catch (Exception e) {
            logger.warn("Failed to check premium authentication for {}: {}", username, e.getMessage());
        }
        
        // Default to allow on error
        cacheResult(cacheKey, false);
        return false;
    }
    
    private boolean checkSessionServer(String username, String uuid) {
        try {
            // This is a more advanced check - we can try to verify if they have an active session
            // For now, let's use a simpler approach: check if they're using the premium UUID format
            
            // Premium UUIDs from Mojang are version 4 UUIDs (random)
            // Cracked UUIDs are version 3 UUIDs (name-based)
            UUID premiumUUID = UUID.fromString(uuid.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            UUID crackedUUID = generateCrackedUUID(username);
            
            logger.info("Premium UUID: {}", premiumUUID);
            logger.info("Cracked UUID: {}", crackedUUID);
            logger.info("UUIDs are different: {}", !premiumUUID.equals(crackedUUID));
            
            // If the UUIDs are different, they're likely using premium authentication
            // But since we're in offline mode, we need another way to detect this
            
            // For now, let's assume if they have a premium account AND the proxy is offline,
            // we need to check connection patterns or other indicators
            
            // Simple heuristic: if account exists in Mojang DB, assume premium connection
            // This might need refinement based on your specific setup
            return true; // Premium account exists, likely premium connection
            
        } catch (Exception e) {
            logger.warn("Failed to check session for {}: {}", username, e.getMessage());
            return false;
        }
    }
    
    private void cacheResult(String username, boolean isPremium) {
        premiumCache.put(username, isPremium);
        cacheTimestamps.put(username, System.currentTimeMillis());
    }
    
    private UUID generateCrackedUUID(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}