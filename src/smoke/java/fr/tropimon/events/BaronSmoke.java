package fr.tropimon.events;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Real local entity synchronization; never runs in a normal player session. */
public final class BaronSmoke {
  public static int chimes;
  public static int outlines;
  private static int minimapOutlines;
  private static int ticks, stage, scale = 1;
  private static UUID wild, ordinary, owned;
  private static long started;

  static void start(MinecraftClient client) throws Exception {
    started = System.nanoTime();
    client.setScreen(null);
    client
        .getServer()
        .submit(
            () -> {
              var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
              var world = player.getServerWorld();
              world
                  .getGameRules()
                  .get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE)
                  .set(false, client.getServer());
              world.setTimeOfDay(6000);
              var alpha =
                  PokemonProperties.Companion.parse("pikachu level=40", " ", "=")
                      .createEntity(world);
              alpha.getPokemon().setAlpha(true);
              alpha.setAiDisabled(true);
              alpha.refreshPositionAndAngles(
                  player.getX() + 12, player.getY(), player.getZ(), 0, 0);
              world.spawnEntity(alpha);
              wild = alpha.getUuid();
              var normal =
                  PokemonProperties.Companion.parse("pikachu level=20", " ", "=")
                      .createEntity(world);
              normal.setAiDisabled(true);
              normal.refreshPositionAndAngles(
                  player.getX() - 12, player.getY(), player.getZ(), 0, 0);
              world.spawnEntity(normal);
              ordinary = normal.getUuid();
              var petPokemon =
                  PokemonProperties.Companion.parse("squirtle level=30", " ", "=").create();
              petPokemon.setAlpha(true);
              com.cobblemon.mod.common.Cobblemon.INSTANCE
                  .getStorage()
                  .getParty(player)
                  .add(petPokemon);
              var pet =
                  petPokemon.sendOut(
                      world,
                      player.getPos().add(0, 0, 12),
                      null,
                      entity -> {
                        entity.setAiDisabled(true);
                        return kotlin.Unit.INSTANCE;
                      });
              owned = pet.getUuid();
            })
        .get();
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (stage == 99) return;
          try {
            if (System.nanoTime() - started > 180_000_000_000L)
              throw new AssertionError("Baron smoke timeout " + stage);
            if (++ticks < 50) return;
            ticks = 0;
            switch (stage) {
              case 0 -> {
                SmokeClient.require(chimes == 1, "exactly one Baron chime");
                SmokeClient.require(
                    EventsClient.BARONS.count() == 1,
                    "only wild Alpha is detected; normal and owned Alpha excluded");
                SmokeClient.require(
                    BaronTracker.isBaron(entity(c, wild)), "Alpha synchronized from server");
                SmokeClient.require(
                    !BaronTracker.isBaron(entity(c, ordinary)), "normal Pokemon untouched");
                SmokeClient.require(
                    !BaronTracker.isBaron(entity(c, owned)),
                    "owned Alpha excluded by synchronized owner UUID");
                if (net.fabricmc.loader.api.FabricLoader.getInstance()
                    .isModLoaded("xaerominimap")) {
                  SmokeClient.require(
                      outlines > 0, "Xaero minimap draws Baron outline around native icon");
                  var helperType = Class.forName("xaero.hud.minimap.radar.color.RadarColorHelper");
                  var colorType = Class.forName("xaero.hud.minimap.radar.color.RadarColor");
                  var method =
                      helperType.getDeclaredMethod(
                          "getRadarColorHex",
                          net.minecraft.entity.Entity.class,
                          colorType,
                          colorType);
                  method.setAccessible(true);
                  var yellow = colorType.getField("YELLOW").get(null);
                  var helper = helperType.getConstructor().newInstance();
                  SmokeClient.require(
                      (int) method.invoke(helper, entity(c, wild), yellow, yellow) == 0xFFFF3535,
                      "opaque red fallback for wild Baron");
                  SmokeClient.require(
                      (int) method.invoke(helper, entity(c, ordinary), yellow, yellow)
                          != 0xFFFF3535,
                      "normal radar color unchanged");
                }
                SmokeClient.shot(c, "baron-hud-minimap");
                c.options.getGuiScale().setValue(scale);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1400, 1000);
                c.onResolutionChanged();
                stage = 1;
              }
              case 1 -> {
                SmokeClient.require(
                    c.getWindow().getScaleFactor() == scale, "Baron HUD effective GUI " + scale);
                SmokeClient.shot(c, "baron-gui-" + scale);
                if (++scale <= 4) {
                  c.options.getGuiScale().setValue(scale);
                  c.onResolutionChanged();
                } else {
                  c.getNetworkHandler().sendChatCommand("tropimonevents sound off");
                  SmokeClient.require(
                      !EventsClient.BARONS.soundEnabled,
                      "Baron sound can be muted by local command");
                  if (net.fabricmc.loader.api.FabricLoader.getInstance()
                      .isModLoaded("xaeroworldmap")) {
                    var session =
                        Class.forName("xaero.map.WorldMapSession")
                            .getMethod("getCurrentSession")
                            .invoke(null);
                    var processor = session.getClass().getMethod("getMapProcessor").invoke(session);
                    var constructor = Class.forName("xaero.map.gui.GuiMap").getConstructors()[0];
                    c.setScreen((Screen) constructor.newInstance(null, null, processor, c.player));
                    minimapOutlines = outlines;
                  }
                  stage = 2;
                }
              }
              case 2 -> {
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaeroworldmap"))
                  SmokeClient.require(
                      outlines > minimapOutlines, "Xaero world map also draws native icon outline");
                SmokeClient.shot(c, "baron-worldmap");
                c.setScreen(null);
                c.getServer()
                    .submit(() -> c.getServer().getOverworld().getEntity(wild).discard())
                    .get();
                stage = 3;
              }
              case 3 -> {
                SmokeClient.require(chimes == 1, "no repeated chime for the same Baron");
                SmokeClient.require(
                    EventsClient.BARONS.count() == 0 && EventsClient.BARONS.visible().isEmpty(),
                    "unloaded Baron disappears without stale marker");
                SmokeClient.shot(c, "baron-gone");
                EventsClient.BARONS.reset();
                SmokeClient.require(EventsClient.BARONS.visible().isEmpty(), "Baron session reset");
                System.out.println("TROPIMON_SMOKE_OK");
                c.scheduleStop();
                stage = 99;
              }
            }
          } catch (Throwable error) {
            error.printStackTrace();
            System.err.println("TROPIMON_SMOKE_FAILED");
            c.scheduleStop();
            stage = 99;
          }
        });
  }

  private static PokemonEntity entity(MinecraftClient client, UUID id) {
    for (var entity : client.world.getEntities())
      if (entity.getUuid().equals(id)) return (PokemonEntity) entity;
    throw new AssertionError("Synthetic Pokemon missing");
  }
}
