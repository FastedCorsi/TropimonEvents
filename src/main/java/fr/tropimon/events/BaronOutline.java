package fr.tropimon.events;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/** Draw only a red silhouette behind Xaero's unchanged, currently selected icon. */
public final class BaronOutline {
  private static ShaderProgram shader;

  static void register() {
    CoreShaderRegistrationCallback.EVENT.register(
        context ->
            context.register(
                Identifier.of("tropimon_events", "baron_outline"),
                VertexFormats.POSITION_TEXTURE,
                loaded -> shader = loaded));
  }

  public static void draw(
      Matrix4f matrix,
      float x,
      float y,
      int u,
      int v,
      float width,
      float height,
      float atlasSize,
      int texture,
      float alpha) {
    if (shader == null || alpha <= 0) return;
    var previousShader = RenderSystem.getShader();
    int previousTexture = RenderSystem.getShaderTexture(0);
    float[] color = RenderSystem.getShaderColor().clone();
    try {
      RenderSystem.setShader(() -> shader);
      RenderSystem.setShaderTexture(0, texture);
      RenderSystem.setShaderColor(1, .12F, .12F, alpha);
      var buffer =
          Tessellator.getInstance()
              .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
      for (int dx = -1; dx <= 1; dx++)
        for (int dy = -1; dy <= 1; dy++) {
          if (dx == 0 && dy == 0) continue;
          float left = x + dx * 1.5F, top = y + dy * 1.5F;
          // Xaero's framebuffer atlas uses inverted V, matching its native icon vertices.
          buffer
              .vertex(matrix, left, top + height, 0)
              .texture(u / atlasSize, v / atlasSize);
          buffer
              .vertex(matrix, left + width, top + height, 0)
              .texture((u + width) / atlasSize, v / atlasSize);
          buffer
              .vertex(matrix, left + width, top, 0)
              .texture((u + width) / atlasSize, (v + height) / atlasSize);
          buffer.vertex(matrix, left, top, 0).texture(u / atlasSize, (v + height) / atlasSize);
        }
      BufferRenderer.drawWithGlobalProgram(buffer.end());
    } finally {
      RenderSystem.setShader(() -> previousShader);
      RenderSystem.setShaderTexture(0, previousTexture);
      RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
    }
  }
}
