# Forge 1.20.1 — Codec System

Sources:
- https://docs.minecraftforge.net/en/1.20.x/datastorage/codecs/
- https://forge.gemwire.uk/wiki/Codecs
- https://docs.fabricmc.net/develop/codecs (Fabric docs have better Codec coverage)

---

## What Codecs Are

Codecs (from Mojang's DataFixerUpper library) describe how to convert objects between two representations — typically `JsonElement` (for JSON files) and `Tag` (for NBT). They replace manual `fromJson`/`toNbt` boilerplate and are required for all datapack registry objects in 1.18+.

---

## Core Operations

```java
// Encode (object → data format)
DataResult<JsonElement> encoded = MyObject.CODEC.encodeStart(JsonOps.INSTANCE, myObject);

// Decode (data format → object)
DataResult<Pair<MyObject, JsonElement>> decoded = MyObject.CODEC.decode(JsonOps.INSTANCE, jsonElement);

// Simpler parse
DataResult<MyObject> parsed = MyObject.CODEC.parse(JsonOps.INSTANCE, jsonElement);
```

### DynamicOps

| DynamicOps | Format |
|---|---|
| `JsonOps.INSTANCE` | Standard JSON (`JsonElement`) |
| `JsonOps.COMPRESSED` | Compressed JSON (single string) |
| `NbtOps.INSTANCE` | NBT (`Tag`) |
| `RegistryOps.create(ops, registryAccess)` | For datapack registries (adds registry context) |

---

## DataResult

```java
DataResult<MyObject> result = MyObject.CODEC.parse(...);

// Get value or throw
MyObject obj = result.getOrThrow(false, e -> LOGGER.error("Failed: " + e));

// Safe optional
Optional<MyObject> opt = result.result();

// Error
Optional<DataResult.PartialResult<MyObject>> err = result.error();

// Get result or run consumer on error
MyObject obj = result.resultOrPartial(e -> LOGGER.warn(e)).orElse(DEFAULT);
```

---

## Built-in Codecs

```java
Codec.BOOL         // Boolean
Codec.BYTE         // Byte
Codec.SHORT        // Short
Codec.INT          // Integer
Codec.LONG         // Long
Codec.FLOAT        // Float
Codec.DOUBLE       // Double
Codec.STRING       // String

// Range-validated
Codec.intRange(0, 100)
Codec.floatRange(0f, 1f)
Codec.doubleRange(0.0, 1.0)

// Collections
SomeCodec.listOf()          // List<T> (immutable)
Codec.unboundedMap(keyCodec, valueCodec)  // Map<K,V>

// Minecraft / Forge built-ins
ResourceLocation.CODEC
BlockState.CODEC
ItemStack.CODEC
CompoundTag.CODEC
Biome.CODEC
Block.CODEC   // = BuiltInRegistries.BLOCK.byNameCodec()
```

---

## RecordCodecBuilder — The Main Pattern

Used to build a codec for a class with multiple fields:

```java
public class ConstellationData {

    public static final Codec<ConstellationData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC
                .fieldOf("id")
                .forGetter(ConstellationData::getId),
            Codec.STRING
                .fieldOf("name")
                .forGetter(ConstellationData::getName),
            Codec.INT
                .optionalFieldOf("phase", 0)
                .forGetter(ConstellationData::getPhase),
            Codec.FLOAT
                .fieldOf("brightness")
                .forGetter(ConstellationData::getBrightness),
            Codec.BOOL
                .optionalFieldOf("minor", false)
                .forGetter(ConstellationData::isMinor)
        ).apply(instance, ConstellationData::new)
    );

    // Constructor matching the group order
    public ConstellationData(ResourceLocation id, String name, int phase,
                              float brightness, boolean minor) { ... }
}
```

**Limits:** `instance.group(...)` supports up to **16 fields**. Split into sub-codecs or use `Codec.pair()` to compose more.

### Field methods

```java
.fieldOf("key")                    // required field
.optionalFieldOf("key")            // returns Optional<T>
.optionalFieldOf("key", default)   // returns T with default
.forGetter(obj -> obj.getField())  // encode getter
```

---

## Transformers (xmap, flatXMap)

Convert between compatible types:

```java
// Fully reversible
Codec<Color> COLOR_CODEC = Codec.INT.xmap(Color::new, Color::getValue);

// Partially fallible decode (e.g. string → int)
Codec<Integer> SAFE_INT = Codec.STRING.comapFlatMap(
    s -> {
        try { return DataResult.success(Integer.parseInt(s)); }
        catch (NumberFormatException e) { return DataResult.error(() -> s + " is not an int"); }
    },
    Object::toString
);
```

| Scenario | Method |
|---|---|
| Both directions always succeed | `xmap(A→B, B→A)` |
| Decode may fail | `comapFlatMap(decode, encode)` |
| Encode may fail | `flatComapMap(decode, encode)` |
| Both may fail | `flatXMap(decode, encode)` |

---

## Dispatch Codecs (for polymorphism / type registries)

Used extensively in worldgen and recipe serializers. A type field selects which sub-codec to use:

```json
{
  "type": "astralsorcery:discidia",
  "brightness": 0.8,
  "phase": 2
}
```

```java
// Registry mapping type → codec
Map<ResourceLocation, Codec<? extends IConstellation>> REGISTRY = new HashMap<>();

// Dispatch codec
public static final Codec<IConstellation> CODEC = 
    ResourceLocation.CODEC.dispatch(
        "type",
        IConstellation::getType,     // get codec key from object
        REGISTRY::get                // look up codec from key
    );
```

---

## Codec for Datapack Registry Objects

When your object needs to reference other datapack registry objects (e.g. a biome, placed feature), use a `HolderSet` or `Holder`:

```java
public static final Codec<StarfieldModifier> CODEC = RecordCodecBuilder.create(inst ->
    inst.group(
        // Reference to a Biome via HolderSet (tag or list)
        RegistryCodecs.homogeneousList(Registries.BIOME)
            .fieldOf("biomes")
            .forGetter(StarfieldModifier::biomes),
        // Reference to a PlacedFeature Holder
        PlacedFeature.LIST_CODEC
            .fieldOf("features")
            .forGetter(StarfieldModifier::features)
    ).apply(inst, StarfieldModifier::new)
);
```

These codecs require `RegistryOps` to resolve the holder references — they'll be provided automatically during data loading.

---

## StreamCodec (1.20.4+ Network Serialization)

In Forge 1.20.1, network serialization still uses `FriendlyByteBuf` manually. `StreamCodec` was introduced later. Stick to the manual `toNetwork`/`fromNetwork` pattern for 1.20.1.

---

## Practical: Registering a Custom Datapack Registry

```java
@SubscribeEvent
public static void onNewDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
    event.dataPackRegistry(
        ModRegistries.CONSTELLATION_KEY,   // ResourceKey<Registry<Constellation>>
        Constellation.CODEC,               // Codec for JSON loading
        Constellation.CODEC                // optional: network sync codec
    );
}
```

Access at runtime:

```java
RegistryAccess registryAccess = level.registryAccess();
Registry<Constellation> registry = registryAccess.registryOrThrow(ModRegistries.CONSTELLATION_KEY);
Optional<Constellation> discidia = registry.getOptional(new ResourceLocation(MODID, "discidia"));
```
