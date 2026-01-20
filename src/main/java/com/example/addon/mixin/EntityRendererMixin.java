package com.example.addon.mixin;

import com.example.addon.systems.Enemies;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    
    @ModifyVariable(
        method = "renderLabelIfPresent",
        at = @At(value = "HEAD"),
        argsOnly = true
    )
    private Text modifyLabel(Text text, T entity, Text text2, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (entity instanceof PlayerEntity player) {
            if (Enemies.get().isEnemy(player)) {
                // Return red colored text
                return Text.literal(text.getString()).styled(style -> style.withColor(0xFF0000));
            }
        }
        return text;
    }
}
