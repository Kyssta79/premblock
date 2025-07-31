package xyz.pakmc.premiumblocker;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
    
    // Cache to avoid spamming Mojang API
    private final ConcurrentHashMap<String, Boolean> premiumCache = new ConcurrentHashMap<>();
    
    @Inject
    public PremiumBlocker(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        logger.info("PremiumBlocker is loading...");
    }
    
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("PremiumBlocker has been enabled! Now checking against Mojang API...");
        
        // Clear cache every 10 minutes to allow for account changes
        server.getScheduler().buildTask(this, () -> {
            premiumCache.clear();
            logger.debug("Cleared premium player cache");
        }).repeat(10, TimeUnit.MINUTES).schedule();
    }
    
    @Subscribe
    public void onLogin(LoginEvent event) {
        String username = event.getPlayer().getUsername();
        
        // Check cache first
        Boolean cachedResult = premiumCache.get(username.toLowerCase());
        if (cachedResult != null) {
            if (cachedResult) {
                blockPlayer(event, username, "cached");
            } else {
                logger.debug("Allowing cached cracked player: {}", username);
            }
            return;
        }
        
        // Check Mojang API asynchronously
        CompletableFuture.supplyAsync(() -> isPremiumPlayer(username))
            .thenAccept(isPremium -> {
                // Cache the result
                premiumCache.put(username.toLowerCase(), isPremium);
                
                if (isPremium) {
                    blockPlayer(event, username, "API check");
                } else {
                    logger.debug("Allowing cracked player: {}", username);
                }
            })
            .exceptionally(throwable -> {
                logger.warn("Failed to check premium status for {}: {}", username, throwable.getMessage());
                // On API failure, allow the player (fail-open approach)
                logger.info("Allowing {} due to API failure (fail-open)", username);
                return null;
            });
    }
    
    private void blockPlayer(LoginEvent event, String username, String reason) {
        event.getPlayer().disconnect(
            Component.text("Please join from pakmc.xyz", NamedTextColor.RED)
        );
        logger.info("Blocked premium player: {} ({})", username, reason);
    }
    
    /**
     * Check if a player is premium by querying Mojang's API
     * Returns true if the player has a premium Minecraft account
     */
    private boolean isPremiumPlayer(String username) {
        try {
            // Mojang API endpoint to check if username exists
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5 second timeout
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            
            // If response is 200, the player exists in Mojang's database (premium)
            // If response is 204/404, the player doesn't exist (cracked username available)
            boolean isPremium = responseCode == 200;
            
            logger.debug("Mojang API check for {}: {} (response: {})", 
                username, isPremium ? "PREMIUM" : "CRACKED", responseCode);
            
            return isPremium;
            
        } catch (IOException e) {
            logger.warn("Failed to check Mojang API for {}: {}", username, e.getMessage());
            // On error, assume cracked (fail-open)
            return false;
        }
    }
}