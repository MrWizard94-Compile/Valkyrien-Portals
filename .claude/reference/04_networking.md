# Forge 1.20.1 — Networking (SimpleChannel)

Source: https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/
Community wiki: https://forge.gemwire.uk/wiki/SimpleChannel

---

## Creating a SimpleChannel

Create once, in a dedicated handler class:

```java
public class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,   // client acceptsServer predicate
        PROTOCOL_VERSION::equals    // server acceptsClient predicate
    );

    private static int id = 0;

    public static void register() {
        INSTANCE.registerMessage(id++, MyPacket.class,
            MyPacket::encode,
            MyPacket::decode,
            MyPacket::handle);
        // register additional packets here...
    }
}
```

Call `PacketHandler.register()` during `FMLCommonSetupEvent`:

```java
@SubscribeEvent
public void onCommonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(PacketHandler::register);
}
```

---

## Version Compatibility

The predicates receive either a version string or one of two special meta-versions:

| Meta-version | Meaning |
|---|---|
| `NetworkRegistry.ABSENT` | Channel not present on remote |
| `NetworkRegistry.ACCEPTVANILLA` | Remote is vanilla / non-Forge |

Returning `false` for these blocks connection. If your mod is client-optional, accept `ABSENT` on the server predicate.

---

## Defining a Packet

```java
public class MyPacket {

    private final int someValue;

    public MyPacket(int someValue) {
        this.someValue = someValue;
    }

    // Serialization
    public static void encode(MyPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.someValue);
    }

    // Deserialization
    public static MyPacket decode(FriendlyByteBuf buf) {
        return new MyPacket(buf.readInt());
    }

    // Handler — called on network thread; enqueueWork for game logic
    public static void handle(MyPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Now on main thread — safe to touch world/player
            ServerPlayer sender = ctx.getSender(); // null if sent TO client
            // ... do something with msg.someValue ...
        });
        ctx.setPacketHandled(true);  // MUST be set or Forge logs a warning
    }
}
```

---

## Client-Side Handlers

Handlers run on the network thread. Any client-side access (Minecraft.getInstance(), level, player) must be isolated via `DistExecutor`:

```java
public static void handle(MyPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
    NetworkEvent.Context ctx = ctxSupplier.get();
    ctx.enqueueWork(() ->
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> ClientPacketHandler.handle(msg))
    );
    ctx.setPacketHandled(true);
}

// In a separate client-only class
public class ClientPacketHandler {
    public static void handle(MyPacket msg) {
        // safe — main client thread
        Minecraft.getInstance().level.doSomething();
    }
}
```

---

## Sending Packets

### Client → Server

```java
PacketHandler.INSTANCE.sendToServer(new MyPacket(42));
```

### Server → Specific Player

```java
PacketHandler.INSTANCE.send(
    PacketDistributor.PLAYER.with(() -> serverPlayer),
    new MyPacket(42));
```

### Server → All Players Tracking a Chunk

```java
PacketHandler.INSTANCE.send(
    PacketDistributor.TRACKING_CHUNK.with(() -> levelChunk),
    new MyPacket(42));
```

### Server → All Players Tracking an Entity

```java
PacketHandler.INSTANCE.send(
    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
    new MyPacket(42));
```

### Server → All Connected Players

```java
PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new MyPacket(42));
```

### Server → Specific Dimension

```java
PacketHandler.INSTANCE.send(
    PacketDistributor.DIMENSION.with(() -> levelKey),
    new MyPacket(42));
```

---

## Security Rules (Server-Side)

1. **Validate block positions**: Use `level.hasChunkAt(pos)` before loading chunks from client-supplied positions. Never trust client data blindly.
2. **Validate player permissions** before performing privileged actions.
3. **Check entity ownership** — a client can spoof entity IDs.
4. **Always call `ctx.setPacketHandled(true)`** — even on rejected/invalid packets.

---

## FriendlyByteBuf Helpers

```java
buf.writeBlockPos(pos) / buf.readBlockPos()
buf.writeResourceLocation(rl) / buf.readResourceLocation()
buf.writeComponent(component) / buf.readComponent()
buf.writeNbt(tag) / buf.readNbt()
buf.writeItem(stack) / buf.readItem()
buf.writeEnum(enumVal) / buf.readEnum(EnumClass.class)
buf.writeVarInt(n) / buf.readVarInt()
buf.writeUtf(str) / buf.readUtf()
```
