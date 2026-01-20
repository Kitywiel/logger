package com.example.addon.mixin;

import com.example.addon.gui.AutoLogDisconnectScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.text.Text;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Mixin(DisconnectedScreen.class)
public class DisconnectScreenMixin {
    
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void replaceWithCustomScreen(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        // Get coordinates if player exists
        String coords = "";
        if (mc.player != null) {
            coords = String.format("X: %.0f, Y: %.0f, Z: %.0f", 
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }
        
        // Get the reason from the screen title
        DisconnectedScreen screen = (DisconnectedScreen) (Object) this;
        Text reason = screen.getTitle();
        
        // Capture screenshot
        Path screenshotPath = captureScreenshot("Connection Lost", coords);
        
        // Replace the regular disconnect screen with our custom one
        mc.setScreen(new AutoLogDisconnectScreen(reason, coords, screenshotPath));
        
        // Cancel the original screen initialization
        ci.cancel();
    }
    
    private Path captureScreenshot(String reason, String coords) {
        Path savedPath = null;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            
            // Capture screenshot using the main framebuffer (full window)
            Framebuffer framebuffer = mc.getFramebuffer();
            
            // Use the actual window size, not just texture size
            int width = mc.getWindow().getFramebufferWidth();
            int height = mc.getWindow().getFramebufferHeight();
            
            // Ensure framebuffer is the right size
            if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
                framebuffer.resize(width, height);
            }
            
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            
            // Read pixels from the full framebuffer
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            
            // Convert buffer to image (flip Y coordinate because OpenGL is bottom-up)
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = (x + (height - 1 - y) * width) * 4;
                    int r = buffer.get(i) & 0xFF;
                    int g = buffer.get(i + 1) & 0xFF;
                    int b = buffer.get(i + 2) & 0xFF;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            
            // Save screenshot to file
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String filename = String.format("disconnect_%s.png", timestamp);
            Path screenshotsDir = Paths.get(mc.runDirectory.getAbsolutePath(), "screenshots", "autolog");
            Files.createDirectories(screenshotsDir);
            savedPath = screenshotsDir.resolve(filename);
            
            Files.write(savedPath, imageBytes);
            System.out.println("Disconnect screenshot saved: " + savedPath + " (size: " + width + "x" + height + ")");
            
        } catch (Exception e) {
            System.err.println("Failed to capture disconnect screenshot: " + e.getMessage());
            e.printStackTrace();
        }
        
        return savedPath;
    }
}
