# Forge 1.20.1 — Data Generation

Sources:
- https://docs.minecraftforge.net/en/1.20.1/datagen/
- https://docs.minecraftforge.net/en/latest/datagen/server/loottables/
- https://docs.minecraftforge.net/en/1.20.1/datagen/server/tags/

---

## Overview

Data generation produces JSON resource files at build time rather than hand-writing them. Triggered by running `runData` gradle task.

All providers are registered in `GatherDataEvent` on the mod event bus.

---

## Registering Providers

```java
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput output = gen.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // Server data
        gen.addProvider(event.includeServer(), new ModRecipes(output));
        gen.addProvider(event.includeServer(), new ModLootTables(output));
        gen.addProvider(event.includeServer(), new ModBlockTags(output, lookupProvider, existingFileHelper));
        gen.addProvider(event.includeServer(), new ModItemTags(output, lookupProvider, existingFileHelper, blockTags));

        // Client data
        gen.addProvider(event.includeClient(), new ModBlockStates(output, existingFileHelper));
        gen.addProvider(event.includeClient(), new ModItemModels(output, existingFileHelper));
        gen.addProvider(event.includeClient(), new ModLanguage(output, MODID, "en_us"));
    }
}
```

---

## Recipe Provider

```java
public class ModRecipes extends RecipeProvider {

    public ModRecipes(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        // Shaped crafting
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CRYSTAL.get())
            .pattern("SLS")
            .pattern("LCL")
            .pattern("SLS")
            .define('S', Tags.Items.STONE)
            .define('L', Tags.Items.GEMS_LAPIS)
            .define('C', Items.DIAMOND)
            .unlockedBy("has_diamond", has(Items.DIAMOND))
            .save(writer);

        // Shapeless
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.DUST.get(), 4)
            .requires(ModItems.CRYSTAL.get())
            .unlockedBy("has_crystal", has(ModItems.CRYSTAL.get()))
            .save(writer);

        // Smelting
        SimpleCookingRecipeBuilder.smelting(
            Ingredient.of(ModItems.RAW_CRYSTAL.get()),
            RecipeCategory.MISC,
            ModItems.CRYSTAL.get(),
            0.7f, 200)
            .unlockedBy("has_raw", has(ModItems.RAW_CRYSTAL.get()))
            .save(writer, new ResourceLocation(MODID, "crystal_from_smelting"));
    }
}
```

---

## Loot Table Provider

```java
public class ModLootTables extends LootTableProvider {

    public ModLootTables(PackOutput output) {
        super(output, Collections.emptySet(), List.of(
            new SubProviderEntry(ModBlockLoot::new, LootContextParamSets.BLOCK)
        ));
    }

    public static class ModBlockLoot extends BlockLoot {

        @Override
        protected void generate() {
            // Drop self
            dropSelf(MyBlocks.ALTAR.get());

            // Drop with fortune
            add(MyBlocks.CRYSTAL_ORE.get(),
                createOreDrop(MyBlocks.CRYSTAL_ORE.get(), ModItems.RAW_CRYSTAL.get()));

            // Custom loot pool
            add(MyBlocks.INFUSED_STONE.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(ModItems.STARDUST.get())
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 3)))
                        .when(LootItemRandomChanceCondition.randomChance(0.5f)))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModBlocks.BLOCKS.getEntries().stream()
                .map(RegistryObject::get)::iterator;
        }
    }
}
```

---

## Tag Provider

```java
public class ModBlockTags extends BlockTagsProvider {

    public ModBlockTags(PackOutput output,
                        CompletableFuture<HolderLookup.Provider> lookupProvider,
                        ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Add to Forge tag
        tag(Tags.Blocks.ORES)
            .add(ModBlocks.CRYSTAL_ORE.get());

        // Create custom tag
        tag(ModTags.Blocks.CELESTIAL_STONE)
            .add(ModBlocks.MARBLE.get())
            .add(ModBlocks.INFUSED_MARBLE.get());

        // Include another tag
        tag(ModTags.Blocks.CELESTIAL_STONE)
            .addTag(Tags.Blocks.STONE);
    }
}
```

### Defining TagKey constants

```java
public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> CELESTIAL_STONE =
            BlockTags.create(new ResourceLocation(MODID, "celestial_stone"));
    }
    public static class Items {
        public static final TagKey<Item> CRYSTAL_VARIANTS =
            ItemTags.create(new ResourceLocation(MODID, "crystal_variants"));
    }
}
```

---

## Block State & Model Providers

```java
public class ModBlockStates extends BlockStateProvider {

    public ModBlockStates(PackOutput output, ExistingFileHelper efh) {
        super(output, MODID, efh);
    }

    @Override
    protected void registerStatesAndModels() {
        // Simple cube
        simpleBlock(ModBlocks.MARBLE.get());

        // Directional block
        directionalBlock(ModBlocks.ALTAR.get(),
            models().withExistingParent("altar", modLoc("block/altar")));
    }
}

public class ModItemModels extends ItemModelProvider {

    public ModItemModels(PackOutput output, ExistingFileHelper efh) {
        super(output, MODID, efh);
    }

    @Override
    protected void registerModels() {
        // Generated flat item
        basicItem(ModItems.STARDUST.get());

        // Item uses block model
        withExistingParent(ModItems.MARBLE.getId().getPath(),
            modLoc("block/marble"));
    }
}
```

---

## Language Provider

```java
public class ModLanguage extends LanguageProvider {

    public ModLanguage(PackOutput output, String modid, String locale) {
        super(output, modid, locale);
    }

    @Override
    protected void addTranslations() {
        add(ModBlocks.ALTAR.get(), "Starlight Altar");
        add(ModItems.STARDUST.get(), "Stardust");
        add("itemGroup.astralsorcery", "Astral Sorcery");
    }
}
```

---

## Running Data Generation

```bash
./gradlew runData
```

Output goes to `src/generated/resources/`. Add this path to your `sourceSets` in `build.gradle`:

```groovy
sourceSets.main.resources {
    srcDir 'src/generated/resources'
}
```
