package xyz.pakmc.premiumblocker;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
    private final Path dataDirectory;
    private final ConcurrentHashMap<String, Boolean> premiumCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    
    // Configuration variables
    private String kickMessage = "&cPlease join from pakmc.xyz";
    private boolean enabled = true;
    private long cacheDuration = TimeUnit.MINUTES.toMillis(5);
    private boolean debug = true;
    private boolean mojangApiEnabled = true;
    private int apiTimeout = 3000;
    
    @Inject
    public PremiumBlocker(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }
    
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfiguration();
        logger.info("PremiumBlocker has been enabled!");
        logger.info("Kick message: {}", kickMessage);
        logger.info("Mojang API enabled: {}", mojangApiEnabled);
        logger.info("Debug mode: {}", debug);
    }
    
    private void loadConfiguration() {
        try {
            // Create data directory if it doesn't exist
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            
            Path configFile = dataDirectory.resolve("config.yml");
            
            // Copy default config if it doesn't exist
            if (!Files.exists(configFile)) {
                try (InputStream defaultConfig = getClass().getResourceAsStream("/config.yml")) {
                    if (defaultConfig != null) {
                        Files.copy(defaultConfig, configFile);
                        logger.info("Created default configuration file");
                    }
                }
            }
            
            // Load configuration
            if (Files.exists(configFile)) {
                Yaml yaml = new Yaml();
                try (FileInputStream fis = new FileInputStream(configFile.toFile())) {
                    Map<String, Object> config = yaml.load(fis);
                    
                    if (config != null) {
                        kickMessage = (String) config.getOrDefault("kick-message", "&cPlease join from pakmc.xyz");
                        enabled = (Boolean) config.getOrDefault("enabled", true);
                        cacheDuration = TimeUnit.MINUTES.toMillis(((Number) config.getOrDefault("cache-duration", 5)).longValue());
                        debug = (Boolean) config.getOrDefault("debug", true);
                        
                        // Mojang API settings
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mojangApi = (Map<String, Object>) config.get("mojang-api");
                        if (mojangApi != null) {
                            mojangApiEnabled = (Boolean) mojangApi.getOrDefault("enabled", true);
                            apiTimeout = ((Number) mojangApi.getOrDefault("timeout", 3000)).intValue();
                        }
                        
                        logger.info("Configuration loaded successfully");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load configuration: {}", e.getMessage());
            logger.info("Using default configuration values");
        }
    }
    
    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (!enabled) {
            return;
        }
        
        String username = event.getUsername();
        
        if (debug) {
            logger.info("=== CONNECTION ATTEMPT ===");
            logger.info("Username: {}", username);
            logger.info("Connection IP: {}", event.getConnection().getRemoteAddress());
        }
        
        boolean isPremium = checkIfPremiumAuthenticated(username);
        
        if (debug) {
            logger.info("Premium check result for {}: {}", username, isPremium);
        }
        
        if (isPremium) {
            if (debug) {
                logger.info("BLOCKING: {} is connecting with premium authentication", username);
            }
            
            // Convert color codes and create component
            Component kickComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(kickMessage);
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickComponent));
        } else {
            if (debug) {
                logger.info("ALLOWING: {} is connecting without premium authentication", username);
            }
        }
    }
    
    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!enabled || !debug) {
            return;
        }
        
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
        
        // Additional premium checks
        boolean isPremiumWithSession = checkIfPremiumAuthenticated(username);
        logger.info("Has Premium Session: {}", isPremiumWithSession);
    }
    
    private boolean checkIfPremiumAuthenticated(String username) {
        if (!mojangApiEnabled) {
            if (debug) {
                logger.info("Mojang API disabled, allowing connection for {}", username);
            }
            return false;
        }
        
        // Check cache first
        String cacheKey = username.toLowerCase();
        Long timestamp = cacheTimestamps.get(cacheKey);
        if (timestamp != null && (System.currentTimeMillis() - timestamp) < cacheDuration) {
            Boolean cached = premiumCache.get(cacheKey);
            if (cached != null) {
                if (debug) {
                    logger.info("Using cached result for {}: {}", username, cached);
                }
                return cached;
            }
        }
        
        try {
            // Check if account exists in Mojang database
            URL profileUrl = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection profileConnection = (HttpURLConnection) profileUrl.openConnection();
            profileConnection.setRequestMethod("GET");
            profileConnection.setConnectTimeout(apiTimeout);
            profileConnection.setReadTimeout(apiTimeout);
            profileConnection.setRequestProperty("User-Agent", "PremiumBlocker/1.0.0");
            
            int profileResponse = profileConnection.getResponseCode();
            
            if (debug) {
                logger.info("Mojang profile API response for {}: {}", username, profileResponse);
            }
            
            boolean isPremium = (profileResponse == 200);
            
            // Cache the result
            cacheResult(cacheKey, isPremium);
            
            profileConnection.disconnect();
            return isPremium;
            
        } catch (Exception e) {
            if (debug) {
                logger.warn("Failed to check premium authentication for {}: {}", username, e.getMessage());
            }
            // Default to allow on error
            cacheResult(cacheKey, false);
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