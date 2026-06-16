# Forge 1.20.1 — Global Loot Modifiers (GLM)

Source: https://docs.minecraftforge.net/en/latest/resources/server/glm/
Forge test: https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/src/test/java/net/minecraftforge/debug/gameplay/loot/GlobalLootModifiersTest.java

---

## What GLMs Are

Global Loot Modifiers let you append, replace, or remove items from any loot table (block drops, mob drops, chest loot) without overriding the loot table JSON. Perfect for "rock crystals drop from stone" style additions.

---

## Step 1 — Register the Codec

```java
public class ModLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ADD_CRYSTAL_DROP =
        LOOT_MODIFIER_SERIALIZERS.register("add_crystal_drop",
            AddCrystalDropModifier.CODEC);
}
```

Register to mod event bus in constructor: `ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modBus)`

---

## Step 2 — Implement LootModifier

```java
public class AddCrystalDropModifier extends LootModifier {

    public static final Supplier<Codec<AddCrystalDropModifier>> CODEC =
        Suppliers.memoize(() -> RecordCodecBuilder.create(inst ->
            LootModifier.codecStart(inst).and(
                inst.group(
                    ForgeRegistries.ITEMS.getCodec()
                        .fieldOf("item")
                        .forGetter(m -> m.item),
                    Codec.floatRange(0f, 1f)
                        .fieldOf("chance")
                        .forGetter(m -> m.chance),
                    Codec.intRange(1, 64)
                        .optionalFieldOf("count", 1)
                        .forGetter(m -> m.count)
                )
            ).apply(inst, AddCrystalDropModifier::new)
        ));

    private final Item item;
    private final float chance;
    private final int count;

    public AddCrystalDropModifier(LootItemCondition[] conditions,
                                   Item item, float chance, int count) {
        super(conditions);
        this.item = item;
        this.chance = chance;
        this.count = count;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                  LootContext context) {
        // conditions already checked by parent before doApply is called
        if (context.getRandom().nextFloat() < chance) {
            generatedLoot.add(new ItemStack(item, count));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return ModLootModifiers.ADD_CRYSTAL_DROP.get();
    }
}
```

`LootModifier.codecStart(inst)` automatically handles the `conditions` field — just chain `.and(inst.group(...))` for your custom fields.

---

## Step 3 — global_loot_modifiers.json

`data/forge/loot_modifiers/global_loot_modifiers.json`:

```json
{
  "replace": false,
  "entries": [
    "astralsorcery:rock_crystal_from_stone",
    "astralsorcery:stardust_from_granite"
  ]
}
```

`"replace": true` wipes all other mods' GLMs — almost never what you want.

---

## Step 4 — Modifier Instance JSON

`data/astralsorcery/loot_modifiers/rock_crystal_from_stone.json`:

```json
{
  "type": "astralsorcery:add_crystal_drop",
  "conditions": [
    {
      "condition": "minecraft:block_state_property",
      "block": "minecraft:stone"
    }
  ],
  "item": "astralsorcery:rock_crystal",
  "chance": 0.04,
  "count": 1
}
```

---

## Common Loot Conditions

```json
// Specific loot table
{ "condition": "forge:loot_table_id", "loot_table_id": "minecraft:blocks/stone" }

// Block state check
{ "condition": "minecraft:block_state_property", "block": "minecraft:stone" }

// Random chance
{ "condition": "minecraft:random_chance", "chance": 0.05 }

// Random chance with looting
{ "condition": "minecraft:random_chance_with_looting", "chance": 0.05, "looting_multiplier": 0.01 }

// Tool has enchantment
{ "condition": "minecraft:match_tool", "predicate": { "enchantments": [{ "enchantment": "minecraft:silk_touch", "levels": { "min": 1 } }] } }

// Tool does NOT have silk touch (inverted)
{ "condition": "minecraft:inverted", "term": { "condition": "minecraft:match_tool", "predicate": { "enchantments": [{ "enchantment": "minecraft:silk_touch" }] } } }
```

---

## Datagen for GLMs

```java
public class ModGLMProvider extends GlobalLootModifierProvider {

    public ModGLMProvider(PackOutput output) {
        super(output, MODID);
    }

    @Override
    protected void start() {
        add("rock_crystal_from_stone",
            new AddCrystalDropModifier(
                new LootItemCondition[] {
                    LootTableIdCondition.builder(
                        new ResourceLocation("minecraft:blocks/stone")).build()
                },
                ModItems.ROCK_CRYSTAL.get(),
                0.04f,
                1
            ));
    }
}
```

Register in `GatherDataEvent`:

```java
gen.addProvider(event.includeServer(), new ModGLMProvider(gen.getPackOutput()));
```
