package xyz.pakmc.premiumblocker;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
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
        logger.info("PremiumBlocker has been enabled!");
    }
    
    @Subscribe
    public void onLogin(LoginEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        String username = event.getPlayer().getUsername();
        
        // Check if this is a premium (online-mode) UUID
        if (isPremiumUUID(playerUUID, username)) {
            // Disconnect premium players
            event.getPlayer().disconnect(
                Component.text("Please join from pakmc.xyz", NamedTextColor.RED)
            );
            
            logger.info("Blocked premium player: {} (UUID: {})", username, playerUUID);
        } else {
            // Allow cracked/offline mode players
            logger.debug("Allowing cracked player: {} (UUID: {})", username, playerUUID);
        }
    }
    
    /**
     * Check if a UUID belongs to a premium (online-mode) player
     * Premium UUIDs are generated using UUID.nameUUIDFromBytes() with the player's name
     * Cracked UUIDs are generated differently by the server
     */
    private boolean isPremiumUUID(UUID playerUUID, String username) {
        // Generate what the UUID would be if this was a premium player
        UUID premiumUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
        
        // If the UUIDs don't match, this is likely a premium player
        // (Premium players have UUIDs assigned by Mojang, not generated from username)
        return !playerUUID.equals(premiumUUID);
    }
}