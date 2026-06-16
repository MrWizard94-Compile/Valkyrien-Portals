# Forge 1.20.1 — Custom Fluids (FluidType API)

Sources:
- https://forge.gemwire.uk/wiki/User:ChampionAsh5357/Sandbox/Fluids_API
- https://github.com/MinecraftForge/MinecraftForge/issues/8608 (fluid API overhaul notes)

---

## Overview

Since Forge 1.19.2 the `FluidAttributes` system was replaced with `FluidType` — a proper registry object. Every custom fluid now requires:

1. `FluidType` (Forge registry) — behavior, render properties
2. Two vanilla `Fluid` objects — `Source` and `Flowing`
3. `LiquidBlock` — the block form
4. `BucketItem` — the bucket

---

## Full Registration Example

```java
public class ModFluids {

    // --- DeferredRegisters ---
    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(ForgeRegistries.FLUIDS, MODID);
    public static final DeferredRegister<Block> BLOCKS = ...; // shared
    public static final DeferredRegister<Item> ITEMS = ...;   // shared

    // --- FluidType ---
    public static final RegistryObject<FluidType> LIQUID_STARLIGHT_TYPE =
        FLUID_TYPES.register("liquid_starlight", () ->
            new FluidType(FluidType.Properties.create()
                .descriptionId("fluid.astralsorcery.liquid_starlight")
                .fallDistanceModifier(0f)       // no fall damage
                .canExtinguish(true)
                .canSwim(true)
                .supportsBoating(false)
                .pathType(BlockPathTypes.WATER)
                .adjacentPathType(null)
                .sound(SoundActions.BUCKET_FILL,  SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)
            ) {
                @Override
                public void initializeClient(Consumer<IFluidTypeRenderProperties> consumer) {
                    consumer.accept(new IFluidTypeRenderProperties() {
                        private static final ResourceLocation STILL =
                            new ResourceLocation(MODID, "block/liquid_starlight_still");
                        private static final ResourceLocation FLOWING =
                            new ResourceLocation(MODID, "block/liquid_starlight_flowing");
                        private static final ResourceLocation OVERLAY =
                            new ResourceLocation(MODID, "block/liquid_starlight_overlay");

                        @Override
                        public ResourceLocation getStillTexture() { return STILL; }

                        @Override
                        public ResourceLocation getFlowingTexture() { return FLOWING; }

                        @Override
                        @Nullable
                        public ResourceLocation getOverlayTexture() { return OVERLAY; }

                        @Override
                        public int getTintColor() {
                            return 0xBBAADDFF; // ARGB — semi-transparent blue-white
                        }
                    });
                }
            });

    // --- Source & Flowing Fluids ---
    public static final RegistryObject<FlowingFluid> LIQUID_STARLIGHT_SOURCE =
        FLUIDS.register("liquid_starlight", LiquidStarlightFluid.Source::new);

    public static final RegistryObject<FlowingFluid> LIQUID_STARLIGHT_FLOWING =
        FLUIDS.register("liquid_starlight_flowing", LiquidStarlightFluid.Flowing::new);

    // --- Block ---
    public static final RegistryObject<LiquidBlock> LIQUID_STARLIGHT_BLOCK =
        BLOCKS.register("liquid_starlight",
            () -> new LiquidBlock(LIQUID_STARLIGHT_SOURCE, BlockBehaviour.Properties
                .of().noCollission().strength(100f).noLootTable()));

    // --- Bucket ---
    public static final RegistryObject<Item> LIQUID_STARLIGHT_BUCKET =
        ITEMS.register("liquid_starlight_bucket",
            () -> new BucketItem(LIQUID_STARLIGHT_SOURCE,
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
}
```

---

## Fluid Class

```java
public abstract class LiquidStarlightFluid extends ForgeFlowingFluid {

    public LiquidStarlightFluid(Properties props) {
        super(props);
    }

    @Override
    public FluidType getFluidType() {
        return ModFluids.LIQUID_STARLIGHT_TYPE.get();
    }

    // Source block (does not flow, fills bucket)
    public static class Source extends LiquidStarlightFluid {
        public Source() {
            super(new Properties()
                .block(ModFluids.LIQUID_STARLIGHT_BLOCK)
                .bucket(ModFluids.LIQUID_STARLIGHT_BUCKET)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .source(ModFluids.LIQUID_STARLIGHT_SOURCE)
                .flowing(ModFluids.LIQUID_STARLIGHT_FLOWING));
        }

        @Override
        public boolean isSource(FluidState state) { return true; }

        @Override
        public int getAmount(FluidState state) { return 8; }
    }

    // Flowing variant
    public static class Flowing extends LiquidStarlightFluid {
        public Flowing() {
            super(new Properties()
                .block(ModFluids.LIQUID_STARLIGHT_BLOCK)
                .bucket(ModFluids.LIQUID_STARLIGHT_BUCKET)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .source(ModFluids.LIQUID_STARLIGHT_SOURCE)
                .flowing(ModFluids.LIQUID_STARLIGHT_FLOWING));
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) { return false; }

        @Override
        public int getAmount(FluidState state) { return state.getValue(LEVEL); }
    }
}
```

---

## Fluid Interactions (FMLCommonSetupEvent)

Register what happens when this fluid contacts another (e.g. lava + water = stone):

```java
@SubscribeEvent
public void onCommonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(() -> {
        FluidInteractionRegistry.addInteraction(
            ModFluids.LIQUID_STARLIGHT_TYPE.get(),
            new FluidInteractionRegistry.InteractionInformation(
                ForgeMod.LAVA_TYPE.get(),
                Blocks.OBSIDIAN.defaultBlockState()));  // result block
    });
}
```

---

## FluidType.Properties Reference

```java
FluidType.Properties.create()
    .descriptionId("fluid.modid.name")     // translation key
    .fallDistanceModifier(0f)              // subtract from fall distance per tick
    .canExtinguish(true)                   // puts out fire
    .canSwim(true)                         // entity can swim
    .canPushEntity(true)                   // pushes entities
    .supportsBoating(false)
    .density(1000)                         // relative density (water = 1000)
    .viscosity(1000)                       // flow resistance
    .temperature(300)                      // kelvin (lava ~1300)
    .isLighterThanAir(false)
    .pathType(BlockPathTypes.WATER)        // mob pathfinding
    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
```

---

## API Change Summary (vs 1.16)

| 1.16 | 1.20.1 |
|---|---|
| `FluidAttributes` | `FluidType` (registered) |
| `FluidAttributes.Builder` | `FluidType.Properties.create()` |
| `fluid.getAttributes().getStillTexture()` | `fluidType.getRenderPropertiesOrDefault().getStillTexture()` |
| `FluidAttributes#getColor` | `IFluidTypeRenderProperties#getTintColor` |
| `IForgeFluid#getAttributes` | `IForgeFluid#getFluidType` |
| No separate registry | `ForgeRegistries.Keys.FLUID_TYPES` |
