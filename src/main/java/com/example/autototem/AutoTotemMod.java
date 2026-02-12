package com.example.autototem;

import com.example.autototem.config.ModConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class AutoTotemMod implements ModInitializer {
    public static final String MOD_ID = "auto-use-totem";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding toggleKey;
    public static boolean inGameEnabled = true;

    // WebSocket endpoint
    private static final String WEBHOOK_URL = "http://127.0.0.1:8080/pull";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    // Track totem count state (to avoid sending duplicate notifications)
    private static int lastTotemCount = -1;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Auto Totem Mod");

        ModConfig.load();
        inGameEnabled = ModConfig.isGlobalEnabled();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.auto-totem.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "category.auto-totem"
        ));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;

            // Keybind toggle
            if (ModConfig.isGlobalEnabled() && ModConfig.isKeybindEnabled() && toggleKey.wasPressed()) {
                inGameEnabled = !inGameEnabled;
                String status = inGameEnabled ? "§a Active" : "§c Inactive";
                player.sendMessage(Text.literal("§6[Auto Totem] §fMod " + status), true);
                LOGGER.debug("In-game state flipped to: {}", inGameEnabled);
            }

            // Check totem count and send webhook when no totems remain
            if (ModConfig.isGlobalEnabled() && inGameEnabled) {
                int currentTotemCount = countTotemsInInventory(player.getInventory());

                // Send notification only when transitioning to 0 totems
                if (currentTotemCount == 0 && lastTotemCount != 0) {
                    sendWebhookNotification();
                    player.sendMessage(Text.literal("§6[Auto Totem] §c警告：圖騰已用完！"), true);
                    LOGGER.info("No totems remaining. Webhook notification sent.");
                }

                lastTotemCount = currentTotemCount;
            }
        });
    }

    /**
     * Count the number of totems in the player's inventory (hotbar + main inventory)
     */
    private static int countTotemsInInventory(PlayerInventory inventory) {
        int count = 0;

        // Check hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            if (inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                count++;
            }
        }

        // Check main inventory (slots 9-35)
        for (int i = 9; i < 36; i++) {
            if (inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Send a webhook notification to the configured endpoint
     */
    private static void sendWebhookNotification() {
        // Send asynchronously to avoid blocking game thread
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(WEBHOOK_URL))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    LOGGER.debug("Webhook notification sent successfully. Response: {}", response.body());
                } else {
                    LOGGER.warn("Webhook notification failed with status code: {}", response.statusCode());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to send webhook notification: {}", e.getMessage(), e);
            }
        });
    }

    public static boolean isModEnabled() {
        return ModConfig.isGlobalEnabled() && inGameEnabled;
    }
}
