package com.example.addon.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoLogDisconnectScreen extends Screen {
    private final Text reason;
    private final String coordinates;
    private Identifier screenshotTexture;
    private int imageWidth;
    private int imageHeight;
    private final Path screenshotPath;
    private boolean backgroundRendered = false;
    private boolean screenshotLoaded = false;
    private float screenshotScale = 0.5f; // Scale from 0.1 (1) to 1.0 (10) - start at medium size

    public AutoLogDisconnectScreen(Text reason, String coordinates, Path screenshotPath) {
        super(Text.literal("Disconnected"));
        this.reason = reason;
        this.coordinates = coordinates;
        this.screenshotPath = screenshotPath;
    }



    private void loadScreenshot() {
        if (screenshotLoaded || screenshotPath == null || !Files.exists(screenshotPath)) {
            return;
        }
        
        try (FileInputStream fis = new FileInputStream(screenshotPath.toFile())) {
            NativeImage image = NativeImage.read(fis);
            if (image != null) {
                // Clean up previous texture if exists
                if (screenshotTexture != null) {
                    MinecraftClient.getInstance().getTextureManager().destroyTexture(screenshotTexture);
                }
                
                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                Identifier textureId = Identifier.of("autolog", "screenshot_" + System.currentTimeMillis());
                MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
                this.screenshotTexture = textureId;
                imageWidth = image.getWidth();
                imageHeight = image.getHeight();
                screenshotLoaded = true;
                System.out.println("Screenshot loaded: " + imageWidth + "x" + imageHeight + " at scale: " + screenshotScale);
            } else {
                System.err.println("Screenshot image was null");
            }
        } catch (Exception e) {
            System.err.println("Failed to load screenshot:");
            e.printStackTrace();
        }
    }

    @Override
    protected void init() {
        super.init();
        
        // Load screenshot immediately
        loadScreenshot();
        
        // Add relog button above back button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reconnect"), button -> {
            this.client.disconnect();
            // Go back to multiplayer screen for reconnection
            this.client.setScreen(new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(new net.minecraft.client.gui.screen.TitleScreen()));
        }).dimensions(this.width / 2 - 100, this.height - 65, 200, 20).build());
        
        // Add back to title screen button
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> {
            this.client.disconnect();
            this.client.setScreen(null);
        }).dimensions(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Always render default dark background first
        super.renderBackground(context, mouseX, mouseY, delta);
        backgroundRendered = true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render background first
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // Render screenshot if available (render AFTER background, BEFORE UI)
        if (screenshotTexture != null && imageWidth > 0 && imageHeight > 0) {
            // Use most of the screen space, leaving room for buttons
            int maxWidth = this.width - 60;
            int maxHeight = this.height - 120;
            
            // Calculate scale that fits the ENTIRE image on screen
            double scaleX = (double) maxWidth / imageWidth;
            double scaleY = (double) maxHeight / imageHeight;
            
            // Use the smaller scale to ensure ENTIRE image fits
            double baseScale = Math.min(scaleX, scaleY);
            double finalScale = baseScale * screenshotScale;
            
            // Calculate final display size
            int displayWidth = (int) Math.round(imageWidth * finalScale);
            int displayHeight = (int) Math.round(imageHeight * finalScale);
            
            // Center the image perfectly
            int x = (this.width - displayWidth) / 2;
            int y = (this.height - displayHeight) / 2;
            
            // Ensure coordinates are valid
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            if (x + displayWidth > this.width) x = this.width - displayWidth;
            if (y + displayHeight > this.height) y = this.height - displayHeight;
            
            // Render with proper OpenGL settings
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            
            // Draw the COMPLETE texture
            context.drawTexture(
                (identifier) -> RenderLayer.getGuiTextured(identifier),
                screenshotTexture,
                x, y, 0, 0,
                displayWidth, displayHeight,
                imageWidth, imageHeight
            );
            
            RenderSystem.disableBlend();
        }
        
        // Render widgets manually (buttons)
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }
        
        // Draw title with background for visibility
        context.fill(this.width / 2 - 150, 15, this.width / 2 + 150, 35, 0xAA000000);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // Draw reason with background
        context.fill(this.width / 2 - 200, 45, this.width / 2 + 200, 65, 0xAA000000);
        context.drawCenteredTextWithShadow(this.textRenderer, reason, this.width / 2, 50, 0xFF5555);
        
        // Draw coordinates with background
        if (coordinates != null && !coordinates.isEmpty()) {
            context.fill(this.width / 2 - 150, 70, this.width / 2 + 150, 85, 0xAA000000);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(coordinates), this.width / 2, 75, 0xFFFF55);
        }
        
        // Draw scale info
        int scaleLevel = Math.round(screenshotScale * 10);
        if (scaleLevel < 1) scaleLevel = 1;
        if (scaleLevel > 10) scaleLevel = 10;
        context.fill(this.width / 2 - 100, 90, this.width / 2 + 100, 105, 0xAA000000);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Scale: " + scaleLevel + "/10 (1-0 keys)"), this.width / 2, 95, 0xAAFFAA);
        
        // Show message if no screenshot available
        if (screenshotTexture == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No screenshot available"), this.width / 2, this.height / 2, 0xFF5555);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle number keys 1-9 for scale adjustment
        if (keyCode >= 49 && keyCode <= 57) { // Keys 1-9
            int scaleLevel = keyCode - 48; // Convert keycode to number (1-9)
            screenshotScale = scaleLevel / 10.0f;
            if (screenshotScale < 0.1f) screenshotScale = 0.1f;
            if (screenshotScale > 1.0f) screenshotScale = 1.0f;
            backgroundRendered = false; // Force re-render
            return true;
        }
        // Handle 0 key for scale 10
        if (keyCode == 48) { // Key 0
            screenshotScale = 1.0f;
            backgroundRendered = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (screenshotTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(screenshotTexture);
            screenshotTexture = null;
        }
        super.close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Changed to prevent ESC closing
    }
}
