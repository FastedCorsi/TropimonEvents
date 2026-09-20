package fr.tropimon.events;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import io.airlift.compress.zstd.ZstdCompressor;
import io.netty.buffer.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventWireTest {
  private static void varInt(ByteBuf b, int v) {
    do {
      int part = v & 127;
      v >>>= 7;
      b.writeByte(part | (v == 0 ? 0 : 128));
    } while (v != 0);
  }

  private static ByteBuf text(String s) {
    var b = Unpooled.buffer();
    byte[] raw = s.getBytes(StandardCharsets.UTF_8);
    varInt(b, raw.length);
    return b.writeBytes(raw);
  }

  @Test
  void officialDefinitionPreservesBuffer() {
    var b =
        text(
            "{\"name\":\"Festival"
                + " test\",\"description\":\"Synthétique\",\"endTimestamp\":2000,\"eventObjectives\":{\"RAIDS\":10}}");
    try {
      var state = new EventState();
      var update = EventWire.decode(EventWire.OPEN, b);
      assertEquals(0, b.readerIndex());
      update.apply(state, null, 1000);
      assertEquals("Festival test", state.name);
      assertEquals(10, state.objectives.get("RAIDS"));
      assertEquals(2000000, state.visible(1000).getFirst().end());
    } finally {
      b.release();
    }
  }

  @Test
  void compressedProgressAndAccountIsolation() {
    UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
    byte[] raw =
        ("{\"uuid\":\"" + owner + "\",\"eventTotalPoints\":42,\"eventCurrency\":7,\"rank\":2}")
            .getBytes(StandardCharsets.UTF_8);
    var compressor = new ZstdCompressor();
    byte[] compressed = new byte[compressor.maxCompressedLength(raw.length)];
    int size = compressor.compress(raw, 0, raw.length, compressed, 0, compressed.length);
    var b = Unpooled.buffer();
    varInt(b, size + 4);
    b.writeInt(raw.length);
    b.writeBytes(compressed, 0, size);
    try {
      var s = new EventState();
      var update = EventWire.decode(EventWire.PROGRESS, b);
      assertEquals(0, b.readerIndex());
      update.apply(s, new UUID(0, 2), 1000);
      assertNull(s.points);
      update.apply(s, owner, 1000);
      assertEquals(42, s.points);
      assertEquals(7, s.currency);
    } finally {
      b.release();
    }
  }

  @Test
  void oversizedAndTruncatedRejected() {
    var b = Unpooled.buffer();
    varInt(b, EventWire.LIMIT + 1);
    try {
      assertThrows(RuntimeException.class, () -> EventWire.decode(EventWire.PROGRESS, b));
    } finally {
      b.release();
    }
    var t = text("{}");
    t.writerIndex(t.writerIndex() - 1);
    try {
      assertThrows(RuntimeException.class, () -> EventWire.decode(EventWire.OPEN, t));
    } finally {
      t.release();
    }
  }

  @Test
  void malformedNeverPartiallyMutates() {
    var s = new EventState();
    s.definition("Old", "", 100000, java.util.Map.of(), 0);
    var json =
        JsonParser.parseString(
                "{\"name\":\"New\",\"description\":\"test\",\"endTimestamp\":9223372036854775807}")
            .getAsJsonObject();
    new EventWire.Observation(EventWire.OPEN, json).apply(s, null, 0);
    assertEquals("Old", s.name);
  }

  @Test
  void frameAndRegion() {
    var frame = Unpooled.buffer();
    varInt(frame, 25);
    byte[] id = EventWire.REGION.getBytes(StandardCharsets.UTF_8);
    varInt(frame, id.length);
    frame.writeBytes(id);
    try {
      var update = EventWire.inspect(frame);
      assertNotNull(update);
      assertEquals(0, frame.readerIndex());
      var s = new EventState();
      s.progress(1, 1, 1);
      update.apply(s, null, 0);
      assertEquals(1, s.points);
      assertTrue(s.serverRecognized);
    } finally {
      frame.release();
    }
  }
}
