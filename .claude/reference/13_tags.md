# Forge 1.20.1 — Tags

Sources:
- https://docs.minecraftforge.net/en/1.20.1/datagen/server/tags/
- https://docs.minecraftforge.net/en/1.18.x/resources/server/tags/
- https://gist.github.com/TelepathicGrunt/b768ce904baa4598b21c3ca42f137f23 (Biome Tags)

---

## What Tags Are

Tags group registry objects under a named key. Recipes, loot tables, and code can test membership instead of checking individual objects. They're composed via JSON files and support hierarchical includes.

---

## TagKey Declaration

```java
public class ModTags {

    public static class Blocks {
        private static TagKey<Block> tag(String name) {
            return BlockTags.create(new ResourceLocation(MODID, name));
        }
        public static final TagKey<Block> CELESTIAL_STONE = tag("celestial_stone");
        public static final TagKey<Block> MARBLE_BLOCKS   = tag("marble");
    }

    public static class Items {
        private static TagKey<Item> tag(String name) {
            return ItemTags.create(new ResourceLocation(MODID, name));
        }
        public static final TagKey<Item> CRYSTALS      = tag("crystals");
        public static final TagKey<Item> STARDUST_ITEMS = tag("stardust");
    }

    public static class Biomes {
        private static TagKey<Biome> tag(String name) {
            return TagKey.create(Registries.BIOME, new ResourceLocation(MODID, name));
        }
        public static final TagKey<Biome> HAS_STARFIELD = tag("has_starfield");
    }
}
```

---

## JSON Tag Files

### Location pattern

```
data/<namespace>/tags/<registry_plural>/<path>.json
```

Registry folder names (1.20.1):
- `blocks`
- `items`
- `entity_types`
- `fluids`
- `game_events`
- `biomes`
- `damage_type`

Example: `ModTags.Blocks.CELESTIAL_STONE` → `data/astralsorcery/tags/blocks/celestial_stone.json`

### Tag JSON format

```json
{
  "replace": false,
  "values": [
    "astralsorcery:marble",
    "astralsorcery:infused_marble",
    "#forge:stone"
  ]
}
```

- `"replace": true` — overwrites the tag instead of appending to it
- `#namespace:path` — include another tag by reference
- `{ "id": "...", "required": false }` — optional entry (won't fail if the object is missing)

---

## Forge Convention Tags (forge: namespace)

In Forge 1.18.2–1.20.x the convention namespace is `forge:`. (Switches to `c:` in 1.21+)

### Common forge: Block Tags
```
forge:ores
forge:ores/diamond
forge:storage_blocks
forge:stone
forge:dirt
forge:sand
forge:gravel
forge:glass
forge:glass_panes
forge:wooden_planks
```

### Common forge: Item Tags
```
forge:gems/diamond
forge:gems/lapis
forge:ingots/iron
forge:ingots/gold
forge:nuggets/iron
forge:ores/iron
forge:seeds
forge:crops
forge:dyes
forge:string
forge:rods/wooden
```

### Common forge: Biome Tags
```
forge:is_overworld
forge:is_nether
forge:is_end
forge:is_hot
forge:is_cold
forge:is_dry
forge:is_wet
forge:is_sparse
forge:is_dense
forge:is_plains
forge:is_swamp
forge:is_forest
forge:is_mountain
```

---

## Checking Tag Membership in Code

```java
// Block/Item — via holder
boolean isCelestial = blockState.is(ModTags.Blocks.CELESTIAL_STONE);
boolean isCrystal   = itemStack.is(ModTags.Items.CRYSTALS);

// Using a Holder (for biomes and other registry types)
Holder<Biome> biomeHolder = level.getBiome(pos);
boolean isStarfield = biomeHolder.is(ModTags.Biomes.HAS_STARFIELD);

// Entity type check
boolean isStarBeast = entity.getType().is(ModTags.EntityTypes.STAR_BEASTS);
```

---

## Datagen — Tag Providers

See `11_data_generation.md` for full provider setup. Quick recap:

```java
@Override
protected void addTags(HolderLookup.Provider provider) {
    // Add to existing Forge tag
    tag(Tags.Blocks.ORES).add(ModBlocks.CRYSTAL_ORE.get());

    // Create a new custom tag
    tag(ModTags.Blocks.CELESTIAL_STONE)
        .add(ModBlocks.MARBLE.get(), ModBlocks.INFUSED_MARBLE.get())
        .addTag(Tags.Blocks.STONE);  // include another tag
}
```

For `ItemTags`, use `ItemTagsProvider` which takes the `BlockTagsProvider` as a constructor argument so it can mirror block tags to item tags via `copy(blockTag, itemTag)`:

```java
public class ModItemTags extends ItemTagsProvider {

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        copy(ModTags.Blocks.CELESTIAL_STONE, ModTags.Items.CELESTIAL_STONE_ITEMS);
        tag(ModTags.Items.CRYSTALS)
            .add(ModItems.ROCK_CRYSTAL.get())
            .add(ModItems.CELESTIAL_CRYSTAL.get());
    }
}
```

---

## Recipe / Loot Condition Tag Checks

```json
// Recipe ingredient from tag
{ "tag": "forge:gems/diamond" }

// Loot condition: block must be in tag
{
  "condition": "forge:tag_match",
  "tag": "astralsorcery:celestial_stone",
  "type": "block"
}
```
