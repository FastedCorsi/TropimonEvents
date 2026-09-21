package fr.tropimon.events;

import fr.tropimon.events.mixin.XaeroIconBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/** Same sprite-space slot as E19's shiny stars, using Cobblemon's installed Alpha icon. */
public final class BaronMarker {
  private static final Identifier ICON =
      Identifier.of("cobblemon", "textures/gui/summary/icon_size_alpha.png");

  public static void draw(
      Matrix4f matrix, float x, float y, float width, float height,
      float red, float green, float blue, float alpha, Object renderer, boolean shiny) {
    if (!(renderer instanceof XaeroIconBuffer icons) || alpha <= 0) return;
    // Xaero draws pixels 1..63 of the original 64px sprite. E19 stars occupy
    // (31,18)..(48,31). Fit the visible 17x16 Alpha glyph in that 17x13 slot.
    // A shiny Alpha keeps its stars: mirror the badge slot to the left.
    float scaleX = width / 62F, scaleY = height / 62F;
    float badgeWidth = 17F * 13F / 16F;
    float left = x + ((shiny ? 24.5F : 39.5F) - badgeWidth / 2F - 1F) * scaleX;
    float top = y + 17F * scaleY;
    float right = left + badgeWidth * scaleX, bottom = top + 13F * scaleY;
    int texture = MinecraftClient.getInstance().getTextureManager().getTexture(ICON).getGlId();
    var buffer = icons.events$begin(texture);
    // This ordinary PNG has top-down UVs; Xaero's framebuffer atlas has inverted UVs.
    buffer.vertex(matrix, left, bottom, 0).color(red, green, blue, alpha).texture(10F / 37F, 1);
    buffer.vertex(matrix, right, bottom, 0).color(red, green, blue, alpha).texture(27F / 37F, 1);
    buffer.vertex(matrix, right, top, 0).color(red, green, blue, alpha).texture(27F / 37F, 0);
    buffer.vertex(matrix, left, top, 0).color(red, green, blue, alpha).texture(10F / 37F, 0);
  }
}
