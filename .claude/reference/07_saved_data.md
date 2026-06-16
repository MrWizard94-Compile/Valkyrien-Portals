# Forge 1.20.1 — Saved Data (SavedData / WorldSavedData)

Source: https://docs.minecraftforge.net/en/1.20.1/datastorage/saveddata/
Forum thread: https://forums.minecraftforge.net/topic/132102-saving-data-per-world-1202-solved/

---

## What SavedData Is

`SavedData` stores persistent per-dimension data that lives in the world's `data/` folder as `.dat` NBT files. It is loaded lazily when first accessed and saved automatically when the level saves.

Contrast with capabilities (per-object attachment) — SavedData is better for global, dimension-scoped data (e.g., the state of the celestial network, discovered constellations per world).

---

## Creating a SavedData

```java
public class AstralWorldData extends SavedData {

    private static final String DATA_NAME = MODID + "_world_data";

    private final Map<UUID, Set<String>> discoveredConstellations = new HashMap<>();

    // --- Static factory methods ---

    public static AstralWorldData create() {
        return new AstralWorldData();
    }

    public static AstralWorldData load(CompoundTag tag) {
        AstralWorldData data = create();
        // deserialize
        ListTag list = tag.getList("Constellations", Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            CompoundTag t = (CompoundTag) entry;
            UUID uuid = t.getUUID("PlayerUUID");
            Set<String> names = new HashSet<>(Arrays.asList(t.getString("Names").split(",")));
            data.discoveredConstellations.put(uuid, names);
        }
        return data;
    }

    // --- Save ---

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        discoveredConstellations.forEach((uuid, names) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("PlayerUUID", uuid);
            t.putString("Names", String.join(",", names));
            list.add(t);
        });
        tag.put("Constellations", list);
        return tag;
    }

    // --- Mutation (always call setDirty) ---

    public void addConstellation(UUID player, String name) {
        discoveredConstellations.computeIfAbsent(player, k -> new HashSet<>()).add(name);
        setDirty();  // REQUIRED — without this, save() is never called
    }

    // --- Access ---

    public Set<String> getConstellations(UUID player) {
        return discoveredConstellations.getOrDefault(player, Collections.emptySet());
    }
}
```

---

## Attaching to a Level

```java
public static AstralWorldData get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(
        AstralWorldData::load,   // factory from NBT
        AstralWorldData::create, // factory for new instance
        DATA_NAME                // filename (no .dat extension needed)
    );
}
```

Call from anywhere you have a `ServerLevel`:

```java
AstralWorldData data = AstralWorldData.get(serverLevel);
data.addConstellation(player.getUUID(), "discidia");
```

---

## Cross-Dimension Data

For data that needs to persist regardless of which dimension is loaded, attach to the **Overworld** — it is the only dimension that is never fully unloaded:

```java
public static AstralWorldData getGlobal(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(
        AstralWorldData::load,
        AstralWorldData::create,
        DATA_NAME
    );
}
```

---

## setDirty() Rules

- Call `setDirty()` every time you mutate the saved data's state.
- Without it, the `save()` method is skipped and old data persists to disk.
- It is safe to call `setDirty()` multiple times per tick — it just sets a flag.

---

## SavedData.Factory (1.20.2+ signature change note)

In Forge 1.20.2+, `computeIfAbsent` was reworked to take a `SavedData.Factory<T>` object instead of two separate lambdas. This port targets **1.20.1** where the two-lambda form is correct.
