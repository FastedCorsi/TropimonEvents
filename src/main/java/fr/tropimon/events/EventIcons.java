package fr.tropimon.events;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import java.text.Normalizer;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Original atlas; Pokemon models come from the installed Cobblemon. */
final class EventIcons {
  private static final Identifier ATLAS =
      Identifier.of("tropimon_events", "textures/gui/events.png");
  private static final Identifier SHINY =
      Identifier.of("cobblemon", "textures/gui/summary/icon_shiny.png");

  private record Portrait(String name, String language, ItemStack item) {}

  private static final EnumMap<EventState.Kind, Portrait> PORTRAITS =
      new EnumMap<>(EventState.Kind.class);
  private static final Map<Identifier, Integer> BADGE_HEIGHTS = new HashMap<>();

  static void reset() {
    PORTRAITS.clear();
    BADGE_HEIGHTS.clear();
  }

  static void gym(DrawContext c, GymObservation gym, int x, int y, int size) {
    Identifier badge =
        Identifier.of(
            "tropimodclient",
            "guis/gyms/badge/badge_" + gym.type().toLowerCase(Locale.ROOT) + ".png");
    int height =
        BADGE_HEIGHTS.computeIfAbsent(
            badge,
            id -> {
              try (var input = MinecraftClient.getInstance().getResourceManager().open(id);
                  var image = net.minecraft.client.texture.NativeImage.read(input)) {
                return image.getWidth() == 16 && image.getHeight() >= 16 ? image.getHeight() : 0;
              } catch (java.io.IOException unavailable) {
                return 0;
              }
            });
    if (height > 0) c.drawTexture(badge, x, y, size, size, 0, 0, 16, 16, 16, height);
    else
      c.drawCenteredTextWithShadow(
          MinecraftClient.getInstance().textRenderer,
          gym.label().substring(0, 2),
          x + size / 2,
          y + size / 2 - 4,
          0xFFFFFFFF);
  }

  private static void texture(
      DrawContext c, Identifier texture, int x, int y, int width, int height) {
    c.drawTexture(texture, x, y, width, height, 0, 0, 1, 1, 1, 1);
  }

  static void tile(DrawContext c, int tile, int x, int y, int size) {
    c.drawTexture(
        ATLAS, x, y, size, size, (tile % 4) * 313.5F, (tile / 4) * 313.5F, 314, 314, 1254, 1254);
  }

  static void baron(DrawContext c, ItemStack portrait, int x, int y, int size) {
    c.fill(x, y, x + size, y + size, 0x801E0707);
    c.drawBorder(x, y, size, size, 0xFFFF3535);
    c.getMatrices().push();
    try {
      float inner = size * .82F;
      c.getMatrices().translate(x + (size - inner) / 2, y + 1, 0);
      c.getMatrices().scale(inner / 16, inner / 16, 1);
      c.drawItem(portrait, 0, 0);
    } finally {
      c.getMatrices().pop();
    }
  }

  static void draw(DrawContext c, EventState.Kind kind, int x, int y, int size) {
    Identifier texture =
        switch (kind) {
          case XP -> Identifier.of("cobblemon", "textures/item/experience_candy/exp_candy_xl.png");
          case IV -> Identifier.of("cobblemon", "textures/item/iv_candy/mighty_candy.png");
          case ABILITY -> Identifier.of("tropifurnitures", "textures/item/great_enigma_berry.png");
          case SHINY -> SHINY;
          default -> null;
        };
    if (texture != null) {
      texture(c, texture, x, y, size, size);
      return;
    }
    tile(c, kind == EventState.Kind.NEXT_RAID ? 0 : kind.ordinal(), x, y, size);
    if (kind == EventState.Kind.NEXT_RAID) return;
    if (kind != EventState.Kind.RAID && kind != EventState.Kind.MEGA) return;
    var boss = EventsClient.STATE.raidBoss(kind);
    ItemStack item = boss == null ? ItemStack.EMPTY : portrait(kind, boss.pokemon());
    if (item.isEmpty()) {
      c.drawCenteredTextWithShadow(
          MinecraftClient.getInstance().textRenderer,
          "?",
          x + size / 2,
          y + size / 2 - 4,
          0xFFFFFFFF);
      return;
    }
    c.getMatrices().push();
    try {
      float inner = size * .60F;
      c.getMatrices().translate(x + (size - inner) / 2, y + (size - inner) / 2, 0);
      c.getMatrices().scale(inner / 16, inner / 16, 1);
      c.drawItem(item, 0, 0);
    } finally {
      c.getMatrices().pop();
    }
  }

  private static ItemStack portrait(EventState.Kind kind, String name) {
    String language = MinecraftClient.getInstance().options.language;
    Portrait old = PORTRAITS.get(kind);
    if (old != null && old.name.equals(name) && old.language.equals(language)) return old.item;
    String normalized = normalize(name), aspect = "";
    if (normalized.startsWith("mega ")) {
      normalized = normalized.substring(5);
      aspect = "mega";
      if (normalized.endsWith(" x") || normalized.endsWith(" y")) {
        aspect += "-" + normalized.charAt(normalized.length() - 1);
        normalized = normalized.substring(0, normalized.length() - 2);
      }
    }
    ItemStack item = ItemStack.EMPTY;
    for (var species : PokemonSpecies.getSpecies()) {
      if (normalize(species.getName()).equals(normalized)
          || normalize(species.getTranslatedName().getString()).equals(normalized)) {
        if (!aspect.isEmpty()) {
          String wanted = aspect;
          if (species.getForms().stream().noneMatch(f -> f.getAspects().contains(wanted))) break;
        }
        item =
            PokemonItem.Companion.from(
                species, aspect.isEmpty() ? new String[0] : new String[] {aspect});
        break;
      }
    }
    PORTRAITS.put(kind, new Portrait(name, language, item));
    return item;
  }

  private static String normalize(String name) {
    return Normalizer.normalize(name, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replace('’', '\'')
        .strip();
  }
}
