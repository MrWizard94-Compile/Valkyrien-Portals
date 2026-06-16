# Forge 1.20.1 — Capability System

Source: https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/

---

## What Capabilities Are

Capabilities allow attaching interfaces to objects (BlockEntities, Entities, ItemStacks, Levels, LevelChunks) without those objects implementing the interface directly. They support optional/sided access and late attachment from other mods.

---

## Built-in Capabilities

Access via `ForgeCapabilities`:

| Capability | Interface | Use |
|---|---|---|
| `ForgeCapabilities.ITEM_HANDLER` | `IItemHandler` | Slot-based item inventory |
| `ForgeCapabilities.FLUID_HANDLER` | `IFluidHandler` | Fluid tanks |
| `ForgeCapabilities.ENERGY` | `IEnergyStorage` | Forge Energy (RF/FE) |

---

## LazyOptional

The return type for `getCapability`. It is:
- Empty when the capability is unavailable
- Non-empty when present, wrapping the capability instance
- Lazy: the underlying object is not created until `.orElse()` / `.ifPresent()` is called

```java
LazyOptional<IItemHandler> lazyHandler = LazyOptional.of(() -> new ItemStackHandler(9));

// Consuming code
someBlockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH)
    .ifPresent(handler -> handler.insertItem(0, stack, false));
```

---

## Exposing Capabilities from a BlockEntity

```java
public class MyBlockEntity extends BlockEntity {

    private final ItemStackHandler itemHandler = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();  // mark chunk dirty when items change
        }
    };

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();  // MUST invalidate to prevent memory leaks
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        lazyItemHandler = LazyOptional.of(() -> itemHandler);
    }
}
```

---

## Sided Access

`getCapability(cap, side)` — `side` is a `Direction` or `null`.

- `null` = omnidirectional / no specific face
- `Direction.UP`, `Direction.NORTH`, etc. = face-specific

You can return different handlers per side:

```java
@Override
public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.ITEM_HANDLER) {
        if (side == Direction.UP) return topSlotHandler.cast();
        if (side == Direction.DOWN) return outputSlotHandler.cast();
        return sideSlotHandler.cast();
    }
    return super.getCapability(cap, side);
}
```

---

## Attaching Capabilities to External Objects (AttachCapabilitiesEvent)

Used to attach capabilities to objects you don't own (vanilla blocks, entities, etc.):

```java
@Mod.EventBusSubscriber(modid = MODID)
public class CapabilityEvents {

    @SubscribeEvent
    public static void onAttachEntityCaps(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(
                new ResourceLocation(MODID, "player_data"),
                new PlayerDataProvider());
        }
    }
}
```

Five generic variants: `Entity`, `BlockEntity`, `ItemStack`, `Level`, `LevelChunk`

For persistent capability data, implement `ICapabilitySerializable<CompoundTag>` in your provider:

```java
public class MyCapProvider implements ICapabilityProvider, ICapabilitySerializable<CompoundTag> {
    // getCapability(), serializeNBT(), deserializeNBT()
}
```

---

## Creating Custom Capabilities

### Option A — RegisterCapabilitiesEvent

```java
@SubscribeEvent
public void registerCaps(RegisterCapabilitiesEvent event) {
    event.register(IMyCapability.class);
}
```

### Option B — @AutoRegisterCapability (simpler)

```java
@AutoRegisterCapability
public interface IMyCapability {
    void doSomething();
    int getValue();
}
```

---

## Player Death Persistence

Capabilities on a player entity are discarded on death. Copy them in `PlayerEvent$Clone`:

```java
@SubscribeEvent
public static void onPlayerClone(PlayerEvent.Clone event) {
    if (event.isWasDeath()) {
        // Copy cap data from old player to new player
        event.getOriginal().reviveCaps();  // needed to read from dead entity
        event.getOriginal().getCapability(MY_CAP).ifPresent(oldCap -> {
            event.getEntity().getCapability(MY_CAP).ifPresent(newCap -> {
                newCap.copyFrom(oldCap);
            });
        });
        event.getOriginal().invalidateCaps();
    }
}
```

---

## Syncing Capabilities to Client

Capabilities do NOT sync automatically. Use custom packets (see `04_networking.md`) to:
1. Send initial state on player login / chunk load
2. Push updates whenever state changes
3. Handle reconnection
