package fr.tropimon.events;

import com.google.gson.*;
import io.airlift.compress.zstd.ZstdDecompressor;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded protocol adapter. Does not register, consume, replace or send packets. */
public final class EventWire {
  public static final String OPEN = "tropimon:open_event_packet",
      PROGRESS = "tropimon:update_event_data_packet",
      REGION = "tropimon:set_current_server_packet";
  static final int LIMIT = 1_048_576;

  public record Observation(String id, JsonObject json) {
    public void apply(EventState state, UUID player, long now) {
      try {
        if (id.equals(REGION)) {
          state.reset();
          state.serverRecognized = true;
          return;
        }
        if (id.equals(OPEN)) {
          String name = text(json, "name", 128), description = text(json, "description", 4096);
          long end = Math.multiplyExact(json.get("endTimestamp").getAsLong(), 1000L);
          Map<String, Integer> objectives = new LinkedHashMap<>();
          if (json.has("eventObjectives") && !json.get("eventObjectives").isJsonNull()) {
            var goals = json.getAsJsonObject("eventObjectives");
            if (goals.size() > 32) throw invalid();
            for (var goal : goals.entrySet()) {
              if (goal.getKey().length() > 64) throw invalid();
              objectives.put(goal.getKey(), goal.getValue().getAsInt());
            }
          }
          state.definition(name, description, end, objectives, now);
        } else {
          UUID owner = UUID.fromString(text(json, "uuid", 36));
          if (player == null || !owner.equals(player)) return;
          int points = json.get("eventTotalPoints").getAsInt(),
              currency = json.get("eventCurrency").getAsInt(),
              rank = json.get("rank").getAsInt();
          if (points < 0 || currency < 0 || rank < 0) return;
          state.progress(points, currency, rank);
        }
      } catch (RuntimeException malformed) {
        /* Invalid observations never change state partially. */
      }
    }
  }

  public static Observation inspect(ByteBuf frame) {
    int position = frame.readerIndex(), end = frame.writerIndex(), count = 0, part;
    do {
      if (position >= end || count++ == 5) return null;
      part = frame.getUnsignedByte(position++);
    } while ((part & 128) != 0);
    if (position >= end) return null;
    int length = frame.getUnsignedByte(position++);
    if (length < 8 || length > 64 || end - position < length) return null;
    String prefix = "tropimon:";
    for (int i = 0; i < prefix.length(); i++)
      if (frame.getByte(position + i) != prefix.charAt(i)) return null;
    String id = frame.toString(position, length, StandardCharsets.UTF_8);
    if (!Set.of(OPEN, PROGRESS, REGION).contains(id)) return null;
    try {
      return decode(id, frame.duplicate().readerIndex(position + length));
    } catch (RuntimeException malformed) {
      return null;
    }
  }

  static Observation decode(String id, ByteBuf source) {
    ByteBuf input = source.duplicate();
    if (id.equals(REGION)) return new Observation(id, new JsonObject());
    String json;
    if (id.equals(OPEN)) {
      int size = bounded(varInt(input), 32767 * 3);
      if (size > input.readableBytes()) throw invalid();
      json = input.toString(input.readerIndex(), size, StandardCharsets.UTF_8);
      input.skipBytes(size);
      if (json.length() > 32767) throw invalid();
    } else if (id.equals(PROGRESS)) {
      int size = bounded(varInt(input), LIMIT);
      if (size < 4 || size > input.readableBytes()) throw invalid();
      int rawSize = bounded(input.readInt(), LIMIT);
      byte[] compressed = new byte[size - 4];
      input.readBytes(compressed);
      byte[] raw = new byte[rawSize];
      int actual =
          new ZstdDecompressor().decompress(compressed, 0, compressed.length, raw, 0, rawSize);
      if (actual != rawSize) throw invalid();
      json = new String(raw, StandardCharsets.UTF_8);
    } else throw invalid();
    if (input.isReadable()) throw invalid();
    return new Observation(id, JsonParser.parseString(json).getAsJsonObject());
  }

  static int varInt(ByteBuf input) {
    int value = 0;
    for (int i = 0; i < 5; i++) {
      int part = input.readUnsignedByte();
      if (i == 4 && (part & 240) != 0) throw invalid();
      value |= (part & 127) << (7 * i);
      if ((part & 128) == 0) return value;
    }
    throw invalid();
  }

  private static int bounded(int value, int max) {
    if (value < 0 || value > max) throw invalid();
    return value;
  }

  private static String text(JsonObject json, String key, int max) {
    String value = json.get(key).getAsString();
    if (value.length() > max) throw invalid();
    return value;
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid event payload");
  }
}
