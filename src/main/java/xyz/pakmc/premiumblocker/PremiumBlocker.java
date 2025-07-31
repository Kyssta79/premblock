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
import java.util.concurrent.CompletableFuture;
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
        String username = event.getPlayer().getUsername();
        
        logger.info("=== CONNECTION ATTEMPT ===");
        logger.info("Username: {}", username);
        logger.info("Connection: {}", event.getConnection().getRemoteAddress());
        
        // Check if this is a premium account trying to connect
        checkPremiumStatusAsync(username).thenAccept(isPremium -> {
            if (isPremium) {
                logger.info("BLOCKING: {} is a premium account", username);
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text("Please join from pakmc.xyz", NamedTextColor.RED)
                ));
            } else {
                logger.info("ALLOWING: {} is not premium or connecting cracked", username);
            }
        });
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
        
        // Additional checks
        boolean isAuthenticated = !playerUUID.equals(crackedUUID);
        logger.info("Is Authenticated Premium: {}", isAuthenticated);
        
        // Double-check with online mode
        if (event.getPlayer().isOnlineMode() || isAuthenticated) {
            logger.info("BLOCKING: Player is authenticated premium");
            event.getPlayer().disconnect(
                Component.text("Please join from pakmc.xyz", NamedTextColor.RED)
            );
        }
    }
    
    private CompletableFuture<Boolean> checkPremiumStatusAsync(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                logger.info("Mojang API response for {}: {}", username, responseCode);
                
                if (responseCode == 200) {
                    // Read the response to get UUID info
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String response = reader.readLine();
                    reader.close();
                    logger.info("Mojang API response body: {}", response);
                    return true; // Premium account exists
                } else if (responseCode == 204 || responseCode == 404) {
                    return false; // Account doesn't exist or is available
                }
                
                connection.disconnect();
                return false;
            } catch (Exception e) {
                logger.warn("Failed to check premium status for {}: {}", username, e.getMessage());
                return false; // Allow on error
            }
        });
    }
    
    private UUID generateCrackedUUID(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}