# Forge 1.20.1 — World Generation

Sources:
- https://forge.gemwire.uk/wiki/Biome_Modifiers
- https://forge.gemwire.uk/wiki/Datapack_Registries
- https://docs.minecraftforge.net/en/1.20.1/concepts/registries/

---

## How It Works in 1.20.1

World generation is **entirely data-driven**. ConfiguredFeatures, PlacedFeatures, and BiomeModifiers are all datapack registry objects defined as JSON files. You don't register them in Java code — you ship JSON files in your mod's resources and optionally generate them with datagen.

```
data/<modid>/worldgen/configured_feature/<name>.json   ← WHAT to generate
data/<modid>/worldgen/placed_feature/<name>.json        ← WHERE/HOW OFTEN to place it  
data/<modid>/forge/biome_modifier/<name>.json           ← WHICH biomes to add it to
```

---

## ConfiguredFeature JSON

Describes *what* to generate. For ores:

`data/astralsorcery/worldgen/configured_feature/aquamarine_shale_ore.json`

```json
{
  "type": "minecraft:ore",
  "config": {
    "size": 7,
    "discard_chance_on_air_exposure": 0.0,
    "targets": [
      {
        "target": { "predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables" },
        "state": { "Name": "astralsorcery:aquamarine_shale" }
      }
    ]
  }
}
```

For scatter/cluster crystals (`minecraft:scattered_ore` works like ore but more spread out):

```json
{
  "type": "minecraft:scattered_ore",
  "config": {
    "size": 3,
    "discard_chance_on_air_exposure": 0.5,
    "targets": [
      {
        "target": { "predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables" },
        "state": { "Name": "astralsorcery:rock_crystal_ore" }
      }
    ]
  }
}
```

### Common ConfiguredFeature types

| Type | Use |
|------|-----|
| `minecraft:ore` | Standard ore vein |
| `minecraft:scattered_ore` | Smaller, more scattered vein |
| `minecraft:disk` | Disk on surface (gravel, clay, sand) |
| `minecraft:simple_block` | Place a single block |
| `minecraft:random_patch` | Random patch of blocks/plants |
| `minecraft:tree` | Tree structure |
| `minecraft:flower` | Flower patches |

---

## PlacedFeature JSON

Describes *where and how often* to place the ConfiguredFeature.

`data/astralsorcery/worldgen/placed_feature/aquamarine_shale_ore.json`

```json
{
  "feature": "astralsorcery:aquamarine_shale_ore",
  "placement": [
    { "type": "minecraft:count", "count": 4 },
    { "type": "minecraft:in_square" },
    { "type": "minecraft:height_range", "height": { "type": "minecraft:uniform", "min_inclusive": { "absolute": -64 }, "max_inclusive": { "absolute": 40 } } },
    { "type": "minecraft:biome" }
  ]
}
```

### Common Placement Modifiers

| Modifier | Purpose |
|----------|---------|
| `minecraft:count` | How many attempts per chunk |
| `minecraft:count_on_every_layer` | Per-layer attempts |
| `minecraft:in_square` | Randomize within chunk XZ |
| `minecraft:height_range` | Y-level range |
| `minecraft:biome` | Only in biomes that include this feature |
| `minecraft:rarity_filter` | 1-in-N chance per chunk |
| `minecraft:surface_relative_threshold_filter` | Check floor surface Y |
| `forge:biome_tag_filter` | Only in biomes with a given tag |

---

## BiomeModifier JSON

Adds your PlacedFeature to biomes.

`data/astralsorcery/forge/biome_modifier/aquamarine_shale_ore.json`

```json
{
  "type": "forge:add_features",
  "biomes": "#minecraft:is_overworld",
  "features": "astralsorcery:aquamarine_shale_ore",
  "step": "underground_ores"
}
```

### Decoration Steps

```
raw_generation, lakes, local_modifications, underground_structures, 
surface_structures, strongholds, underground_ores, underground_decoration,
fluid_springs, vegetal_decoration, top_layer_modification
```

Use `underground_ores` for stone-level ores and `vegetal_decoration` for surface plants/structures.

---

## Feature Cycle Violations

A **feature cycle violation** crashes world generation. It occurs when two biomes contain the same two PlacedFeatures in different relative orders within the same decoration step.

**Rules:**
- Never use the same PlacedFeature in more than one BiomeModifier.
- Never add vanilla PlacedFeatures to biomes directly — reference them by creating a new PlacedFeature that wraps the same ConfiguredFeature.
- If adding a feature to multiple biomes, use a **single** BiomeModifier with a biome tag or list.

---

## Built-in BiomeModifier Types

```json
// Add features
{ "type": "forge:add_features", "biomes": "#forge:is_overworld", 
  "features": "modid:my_feature", "step": "underground_ores" }

// Remove features  
{ "type": "forge:remove_features", "biomes": "#minecraft:is_ocean",
  "features": "minecraft:disk_gravel", "steps": ["underground_decoration"] }

// Add mob spawns
{ "type": "forge:add_spawns", "biomes": "#minecraft:is_forest",
  "spawners": [{ "type": "minecraft:wolf", "weight": 8, "minCount": 4, "maxCount": 4 }] }

// Remove mob spawns
{ "type": "forge:remove_spawns", "biomes": "#minecraft:is_ocean",
  "entity_types": "#minecraft:skeletons" }

// No-op (use to disable another mod's modifier)
{ "type": "forge:none" }
```

---

## Custom BiomeModifier (Java)

For logic that can't be expressed in JSON:

```java
public record StarfieldBiomeModifier(HolderSet<Biome> biomes)
        implements BiomeModifier {

    public static final RegistryObject<Codec<? extends BiomeModifier>> CODEC =
        BIOME_MODIFIER_SERIALIZERS.register("starfield",
            () -> RecordCodecBuilder.create(inst -> inst.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(StarfieldBiomeModifier::biomes)
            ).apply(inst, StarfieldBiomeModifier::new)));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, Builder builder) {
        if (phase == Phase.MODIFY && biomes.contains(biome)) {
            builder.getSpawnSettings().addSpawn(
                MobCategory.CREATURE,
                new MobSpawnSettings.SpawnerData(ModEntities.STAR_BEAST.get(), 5, 1, 3));
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return CODEC.get();
    }
}
```

Register the `DeferredRegister<Codec<? extends BiomeModifier>>`:

```java
public static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
    DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, MODID);
```

---

## Datagen for WorldGen (Optional)

If you prefer Java over hand-writing JSON, use `RegistrySetBuilder` in `GatherDataEvent`:

```java
@SubscribeEvent
public static void gatherData(GatherDataEvent event) {
    DataGenerator gen = event.getGenerator();
    gen.addProvider(event.includeServer(),
        new DatapackBuiltinEntriesProvider(
            gen.getPackOutput(),
            event.getLookupProvider(),
            new RegistrySetBuilder()
                .add(Registries.CONFIGURED_FEATURE, ModWorldgenFeatures::bootstrapConfigured)
                .add(Registries.PLACED_FEATURE, ModWorldgenFeatures::bootstrapPlaced),
            Set.of(MODID)));
}
```
