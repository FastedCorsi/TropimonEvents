package fr.tropimon.events;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.entity.Entity;

/** Checks only the entity already selected by Xaero, without a separate HUD or world scan. */
public final class BaronTracker {
  private BaronTracker() {}

  public static boolean isBaron(Entity entity) {
    return entity instanceof PokemonEntity pokemon
        && pokemon.isAlive()
        && !pokemon.isRemoved()
        && pokemon.getPokemon().isAlpha()
        && pokemon.getOwnerUuid() == null
        && pokemon.getPokemon().isWild()
        && !pokemon.isBattleClone();
  }

}
