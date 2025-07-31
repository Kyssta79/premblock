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
    }
    
    @Subscribe
    public void onLogin(LoginEvent event) {
        String username = event.getPlayer().getUsername();
        UUID playerUUID = event.getPlayer().getUniqueId();
        
        // Check if the player is actually authenticated (premium connection)
        if (isAuthenticatedPremium(playerUUID, username)) {
            blockPlayer(event, username, "authenticated premium");
        } else {
            logger.debug("Allowing cracked connection for username: {}", username);
        }
    }
    
    private void blockPlayer(LoginEvent event, String username, String reason) {
        event.getPlayer().disconnect(
            Component.text("Please join from pakmc.xyz", NamedTextColor.RED)
        );
        logger.info("Blocked premium player: {} ({})", username, reason);
    }
    
    /**
     * Check if a player is connecting with premium authentication
     * This checks if the UUID matches what a premium (authenticated) player would have
     * vs what a cracked player would have for the same username
     */
    private boolean isAuthenticatedPremium(UUID playerUUID, String username) {
        // Generate what the UUID would be for a cracked player with this username
        UUID crackedUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        
        // If the UUIDs don't match, this is likely a premium authenticated connection
        // If they match, this is a cracked connection (even if the username is premium)
        boolean isAuthenticated = !playerUUID.equals(crackedUUID);
        
        logger.debug("Player {}: UUID={}, CrackedUUID={}, Authenticated={}", 
            username, playerUUID, crackedUUID, isAuthenticated);
        
        return isAuthenticated;
    }
}