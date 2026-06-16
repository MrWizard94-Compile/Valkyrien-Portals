# Forge 1.20.1 — Mod Lifecycle & Event System

Sources:
- https://docs.minecraftforge.net/en/1.20.1/concepts/lifecycle/
- https://docs.minecraftforge.net/en/1.20.1/concepts/events/

---

## Event Buses

| Bus | Location | Purpose |
|-----|----------|---------|
| **Forge Event Bus** | `MinecraftForge.EVENT_BUS` | In-game events (player actions, world ticks, block breaks…) |
| **Mod Event Bus** | `FMLJavaModLoadingContext.get().getModEventBus()` | Mod lifecycle, registration, setup |

Use the **wrong bus** = handler never fires. Lifecycle events (`FMLCommonSetupEvent`, `RegisterEvent`, etc.) belong on the **mod bus**. In-game events (`PlayerEvent`, `BlockEvent`, etc.) belong on the **Forge bus**.

---

## Registering Event Handlers

### @Mod.EventBusSubscriber (class-level, static methods)

```java
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEventHandler {
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) { ... }
}

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) { ... }
}
```

### addListener() in constructor (instance methods)

```java
@Mod(MODID)
public class MyMod {
    public MyMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerTick);
    }
}
```

---

## Mod Lifecycle Initialization Order

### 1. Registry Events (synchronous, on mod bus)

Fired first, in sequence:

1. `NewRegistryEvent` — register custom registries via `RegistryBuilder`
2. `DataPackRegistryEvent$NewRegistry` — register datapack registries with `Codec`
3. `RegisterEvent` — register all objects into their registries (one event per registry type)

### 2. Setup Events (parallel-dispatched, on mod bus)

Fired after all registries complete. **All mods run concurrently** — must be thread-safe.

| Event | Physical Side | Purpose |
|-------|--------------|---------|
| `FMLCommonSetupEvent` | Both | Capability registration, cross-mod compat |
| `FMLClientSetupEvent` | Client | Key bindings, screen registration |
| `FMLDedicatedServerSetupEvent` | Server | Server-only setup |

For operations that are not thread-safe, defer to the main thread:

```java
@SubscribeEvent
public void onCommonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(() -> {
        // runs on main thread after parallel phase
        CapabilityManager.get(...);
    });
}
```

### 3. Additional Events (synchronous, on mod bus)

| Event | Purpose |
|-------|---------|
| `InterModEnqueueEvent` | Send cross-mod messages via `InterModComms#sendTo` |
| `InterModProcessEvent` | Retrieve cross-mod messages via `InterModComms#getMessages` |
| `GatherDataEvent` | Register data generators (datagen run only) |
| `FMLLoadCompleteEvent` | All loading finished |

---

## Event Cancellation

Events annotated with `@Cancelable` can be stopped:

```java
@SubscribeEvent
public void onBlockBreak(BlockEvent.BreakEvent event) {
    if (event.isCancelable()) {
        event.setCanceled(true); // prevents the break
    }
}
```

Calling `setCanceled(true)` on a non-cancelable event throws `UnsupportedOperationException`.

---

## Event Results

Events annotated with `@HasResult` support three outcome states:

| Result | Effect |
|--------|--------|
| `Event.Result.DENY` | Stops the action |
| `Event.Result.DEFAULT` | Vanilla behavior |
| `Event.Result.ALLOW` | Forces the action |

```java
event.setResult(Event.Result.DENY);
```

---

## Event Priority

```java
@SubscribeEvent(priority = EventPriority.HIGHEST)
public void myHandler(SomeEvent event) { ... }
```

Execution order: `HIGHEST → HIGH → NORMAL → LOW → LOWEST`

---

## Sided Dist Filtering

For client-only or server-only handlers in a class that's registered on both sides:

```java
@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
```

Or in code:

```java
DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
    // client-only code
});
```

---

## Key Forge Bus Events (In-game)

```
BlockEvent.BreakEvent            — block broken by player
BlockEvent.EntityPlaceEvent      — block placed by entity
LivingDeathEvent                 — living entity dies
LivingHurtEvent                  — living entity takes damage
PlayerEvent.PlayerLoggedInEvent  — player joins
PlayerEvent.Clone                — player respawns/dimension change
TickEvent.LevelTickEvent         — world tick (has Phase.START / Phase.END)
TickEvent.PlayerTickEvent        — per-player tick
RenderLevelStageEvent            — custom rendering at specific render stages
```
