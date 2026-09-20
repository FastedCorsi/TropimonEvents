package fr.tropimon.events;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.PokemonItem;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

/** Only entities already sent by the server. Load events avoid scanning the world every tick. */
public final class BaronTracker {
  public record Entry(PokemonEntity entity, ItemStack portrait) {}

  private final Set<PokemonEntity> loaded = new HashSet<>();
  private final Map<UUID, Entry> active = new HashMap<>();

  private List<Entry> nearest = List.of();
  private Object world;
  private int ticks;

  public static boolean isBaron(Entity entity) {
    return entity instanceof PokemonEntity pokemon
        && pokemon.isAlive()
        && !pokemon.isRemoved()
        && pokemon.getPokemon().isAlpha()
        && pokemon.getOwnerUuid() == null
        && pokemon.getPokemon().isWild()
        && !pokemon.isBattleClone();
  }

  void register() {
    ClientEntityEvents.ENTITY_LOAD.register(
        (entity, level) -> {
          if (entity instanceof PokemonEntity pokemon) loaded.add(pokemon);
        });
    ClientEntityEvents.ENTITY_UNLOAD.register(
        (entity, level) -> {
          if (entity instanceof PokemonEntity pokemon) {
            loaded.remove(pokemon);
            active.remove(pokemon.getUuid());
          }
        });
  }

  void reset() {
    loaded.clear();
    active.clear();
    nearest = List.of();

    world = null;
    ticks = 0;
  }

  void tick(MinecraftClient client) {
    if (client.world == null || client.player == null) return;
    if (world != client.world) {
      // Entity load callbacks can precede the first tick of a new dimension.
      world = client.world;
      loaded.clear();
      for (Entity entity : client.world.getEntities())
        if (entity instanceof PokemonEntity pokemon) loaded.add(pokemon);
      active.clear();
      nearest = List.of();
    }
    if (++ticks % 10 != 0) return;
    active
        .values()
        .removeIf(entry -> !isBaron(entry.entity()) || entry.entity().getWorld() != client.world);

    for (PokemonEntity pokemon : loaded) {
      if (pokemon.getWorld() != client.world || !isBaron(pokemon)) continue;
      active.computeIfAbsent(
          pokemon.getUuid(), id -> new Entry(pokemon, PokemonItem.from(pokemon.getPokemon())));
    }
    nearest =
        active.values().stream()
            .sorted(
                Comparator.comparingDouble(
                    entry -> entry.entity().squaredDistanceTo(client.player)))
            .limit(4)
            .toList();
  }

  List<Entry> visible() {
    return nearest.stream().filter(entry -> isBaron(entry.entity())).toList();
  }

  int count() {
    return active.size();
  }
}
