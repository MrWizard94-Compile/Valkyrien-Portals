# Migration Notes: Astral Sorcery 1.16 → 1.20.1

Sources:
- https://gist.github.com/50ap5ud5/beebcf056cbdd3c922cc8993689428f4 (1.16→1.17 primer)
- https://docs.neoforged.net/primer/docs/1.17/ (NeoForge 1.17 primer)
- Accumulated from porting work on this project

---

## Critical Renames (Mojang Mappings Migration, 1.16→1.17)

| 1.16 Name | 1.20.1 Name |
|---|---|
| `TileEntity` | `BlockEntity` |
| `TileEntityProvider` | `EntityBlock` (interface on Block) |
| `ITickableTileEntity` | `BlockEntityTicker<T>` (returned by `Block#getTicker`) |
| `World` | `Level` |
| `ServerWorld` | `ServerLevel` |
| `ClientWorld` | `ClientLevel` |
| `PlayerEntity` | `Player` |
| `ServerPlayerEntity` | `ServerPlayer` |
| `ItemStack#isEmpty` | (same, but `ItemStack.EMPTY` replaces null stacks) |
| `IItemHandler` | (same, but accessed via `ForgeCapabilities.ITEM_HANDLER`) |
| `@CapabilityInject` | `CapabilityManager.get(new CapabilityToken<>(){})` |
| `IStorage` (capability) | Removed — implement serialize/deserialize yourself |
| `IForgeTileEntity` | Removed — `BlockEntity` is directly extended |
| `INBT` / `NBTBase` | `Tag` |
| `CompoundNBT` | `CompoundTag` |
| `ListNBT` | `ListTag` |
| `StringNBT` | `StringTag` |
| `IntNBT` | `IntTag` |
| `LazyOptional` | (same, Forge) |
| `ModelRenderer` | `ModelPart` |
| `ITextComponent` | `Component` |
| `StringTextComponent` | `Component.literal(...)` |
| `TranslationTextComponent` | `Component.translatable(...)` |
| `RenderType#getSolid()` | `RenderType.solid()` |
| `MatrixStack` | `PoseStack` |
| `Vector3d` | `Vec3` |
| `BlockPos#up/down/north/south/east/west` | `BlockPos#above/below/north/south/east/west` |
| `Direction.Axis.X/Y/Z` | (same) |

---

## Rendering System (1.17+)

The rendering system was **completely overhauled** in 1.17. OpenGL 2.1 → **OpenGL 3.2 Core**.

| 1.16 | 1.20.1 |
|---|---|
| `RenderSystem.bindTexture(id)` | `RenderSystem.setShaderTexture(0, location)` |
| `RenderSystem.color4f(r,g,b,a)` | `RenderSystem.setShaderColor(r,g,b,a)` |
| `RenderSystem.enableAlphaTest()` | Removed — handled by blend state |
| `RenderSystem.enableBlend()` | `RenderSystem.enableBlend()` (same) |
| `Tessellator#getBuffer` | `Tesselator.getInstance().getBuilder()` |
| `DefaultVertexFormats` | `DefaultVertexFormat` |
| `IVertexBuilder` | `VertexConsumer` |
| `IRenderTypeBuffer` | `MultiBufferSource` |
| `ActiveRenderInfo` | `Camera` |
| Raw OpenGL calls (glBegin/glEnd) | Removed — use `VertexConsumer` / `BufferBuilder` |
| `GlStateManager` (most methods) | `RenderSystem.*` equivalents |
| All shaders optional | **All geometry requires a shader** — use `ShaderInstance` |
| `TileEntityRenderer` | `BlockEntityRenderer` |
| `TileEntityRendererDispatcher` | `BlockEntityRendererProvider.Context` (passed to constructor) |

---

## Capability System (1.17+)

```java
// 1.16
@CapabilityInject(IItemHandler.class)
public static Capability<IItemHandler> ITEM_HANDLER_CAPABILITY = null;

// 1.20.1
public static final Capability<IItemHandler> ITEM_HANDLER_CAPABILITY =
    CapabilityManager.get(new CapabilityToken<>(){});
// OR just use ForgeCapabilities.ITEM_HANDLER
```

`@CapabilityInject` is gone. `IStorage` is gone (you handle NBT yourself). `RegisterCapabilitiesEvent` or `@AutoRegisterCapability` is required.

---

## World Generation (1.18+)

World gen was **completely rewritten** in 1.18. The old `IWorldGenerationReader`, `WorldGenFeatureConfiguration`, and `DeferredRegister`-based ore gen are all gone.

| 1.16 | 1.20.1 |
|---|---|
| `Feature.ORE` with `OreFeatureConfig` registered in Java | JSON `ConfiguredFeature` + `PlacedFeature` |
| `BiomeLoadingEvent` to add ores | Forge `BiomeModifier` JSON files |
| `FillWithLiquid` / `IFluidState` | `FluidState` |
| `WorldHeight` hardcoded 0–255 | Dynamic via `LevelHeightAccessor` (-64 to 320 in overworld) |

See [17_worldgen.md](17_worldgen.md) for full details.

---

## Fluid API (1.19.2+)

| 1.16 | 1.20.1 |
|---|---|
| `FluidAttributes` (not a registry) | `FluidType` (registered to `ForgeRegistries.Keys.FLUID_TYPES`) |
| `fluid.getAttributes().getStillTexture()` | `fluidType.getRenderPropertiesOrDefault().getStillTexture()` |
| `FluidAttributes.builder(still, flow)` | `FluidType.Properties.create()` |
| `@CapabilityInject` for `IFluidHandler` | `ForgeCapabilities.FLUID_HANDLER` |

See [20_fluids.md](20_fluids.md) for full details.

---

## Tool System (1.17+)

| 1.16 | 1.20.1 |
|---|---|
| `ToolType` enum | Removed entirely |
| `HarvestLevelProperty` | Block tag-based (`minecraft:mineable/pickaxe` etc.) |
| `Item.Properties#addToolType` | Removed |
| `IForgeBlock#getHarvestTool` | `IForgeBlock#getToolModifiedState` |

Items now declare mining type via `addToTag` in datagen and level tier via `TierSortingRegistry`.

---

## Networking (1.17+)

| 1.16 | 1.20.1 |
|---|---|
| `PacketBuffer` | `FriendlyByteBuf` |
| `IPacket` | `Packet<?>` |
| `SimpleChannel` | (same, `SimpleChannel` still used) |
| `NetworkEvent.Context#getNetworkManager` | `NetworkEvent.Context#getNetworkManager` (same) |

---

## Registry System (1.16 → 1.20)

| 1.16 | 1.20.1 |
|---|---|
| `DeferredRegister` | (same API, still recommended) |
| `ObjectHolder` | (same, but less needed now) |
| `RegistryEvent.Register<T>` | `RegisterEvent` (combined) |
| `ForgeRegistries.TILE_ENTITIES` | `ForgeRegistries.BLOCK_ENTITY_TYPES` |
| `ForgeRegistries.POTIONS` | `ForgeRegistries.MOB_EFFECTS` |

---

## Event Changes

| 1.16 | 1.20.1 |
|---|---|
| `BiomeLoadingEvent` | Forge `BiomeModifier` JSON + `BiomeModifier` Java |
| `RenderWorldLastEvent` | `RenderLevelStageEvent` (multiple stages) |
| `RenderGameOverlayEvent` | `RenderGuiOverlayEvent` |
| `FOVUpdateEvent` | `ComputeFovModifierEvent` |
| `EntityJoinWorldEvent` | `EntityJoinLevelEvent` |
| `WorldEvent.Load` | `LevelEvent.Load` |
| `PlayerLoggedInEvent` | `PlayerEvent.PlayerLoggedInEvent` (same package) |
| `ItemTooltipEvent` | (same) |

---

## Mixin Notes

The 1.16 source has `mixin/client/` mixins. In 1.20.1:

- Forge ships SpongeMixin — it still works. See [15_mixins.md](15_mixins.md).
- Re-evaluate each mixin: can it be replaced by a Forge event? If yes, use the event.
- Sky rendering mixins are the hardest to replace — `RenderLevelStageEvent.AFTER_SKY` covers most cases.
- `LevelRenderer` mixins for star drawing can often be replaced with `RenderLevelStageEvent`.

---

## Data & Resources (1.18+)

| 1.16 | 1.20.1 |
|---|---|
| `RecipeSerializer#read(ResourceLocation, JsonObject)` | `RecipeSerializer#fromJson` |
| `IRecipeSerializer` | `RecipeSerializer<T>` |
| `WorldSavedData` | `SavedData` |
| `DimensionSavedDataManager` | `DimensionDataStorage` |
| `DimensionSavedDataManager#getOrCreate` | `DimensionDataStorage#computeIfAbsent` |
| Loot table JSON conditions | (same structure, some condition names changed) |
