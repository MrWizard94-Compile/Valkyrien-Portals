# Forge 1.20.1 — Rendering

Sources:
- https://docs.minecraftforge.net/en/1.19.x/blockentities/ber/
- https://docs.minecraftforge.net/en/latest/rendering/modelloaders/bakedmodel/
- https://forums.minecraftforge.net/topic/74316-using-custom-sky-graphics-sunmoon-textures-sizes-positions-etc/

---

## BlockEntityRenderer (BER)

Used for dynamic block rendering that cannot be represented by a static JSON model.

### Registration (client-side only, in FMLClientSetupEvent)

```java
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BlockEntityRenderers.register(MyBlockEntities.MY_BE.get(), MyBER::new);
        });
    }
}
```

### BER Class

```java
public class MyBER implements BlockEntityRenderer<MyBlockEntity> {

    public MyBER(BlockEntityRendererProvider.Context ctx) {
        // ctx gives access to BlockEntityRenderDispatcher, EntityModelSet, etc.
    }

    @Override
    public void render(MyBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();

        // Example: render a rotating item above the block
        poseStack.translate(0.5, 1.0, 0.5);
        float angle = (be.getLevel().getGameTime() + partialTick) * 4f;
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.scale(0.5f, 0.5f, 0.5f);

        // Render item model
        Minecraft.getInstance().getItemRenderer().renderStatic(
            be.getDisplayItem(), ItemDisplayContext.GROUND,
            packedLight, packedOverlay, poseStack, bufferSource, be.getLevel(), 0);

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(MyBlockEntity be) {
        return true; // don't cull even when outside frustum
    }

    @Override
    public int getViewDistance() {
        return 256; // default is 64; increase for large or important renderers
    }
}
```

---

## PoseStack & Matrix Operations

```java
poseStack.pushPose();              // save transform state
poseStack.popPose();               // restore transform state

poseStack.translate(x, y, z);
poseStack.scale(x, y, z);
poseStack.mulPose(quaternion);     // apply rotation

// Rotation helpers (Axis is net.minecraft.client.renderer.Axis)
Axis.XP.rotationDegrees(angle)
Axis.YP.rotationDegrees(angle)
Axis.ZP.rotationDegrees(angle)
```

---

## Render Types & VertexConsumer

```java
// Get vertex consumer for a render type
VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());

// Common render types
RenderType.solid()
RenderType.cutout()
RenderType.cutoutMipped()
RenderType.translucent()
RenderType.entityTranslucent(textureLocation)
RenderType.entitySolid(textureLocation)
RenderType.lines()
```

---

## BakedModel

The output of baking a model from JSON/OBJ/custom loaders. Used by BERs that delegate to the block model system.

```java
// Get the baked model for a BlockState
BakedModel model = Minecraft.getInstance().getBlockRenderer()
    .getBlockModelShaper().getBlockModel(blockState);

// Get quads (for manual geometry)
List<BakedQuad> quads = model.getQuads(blockState, direction, random, modelData, renderType);
```

### Rendering a baked model in a BER

```java
Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
    blockState, poseStack, bufferSource, packedLight, packedOverlay, modelData, renderType);
```

---

## Sky / Custom Sky Rendering

Sky rendering in 1.20.1 is performed inside `LevelRenderer` and is not cleanly hooked. Options for Astral Sorcery-style celestial overlays:

### RenderLevelStageEvent (Forge hook)

Fires at specific pipeline stages. Use `Stage.AFTER_SKY` or `Stage.AFTER_PARTICLES` to inject custom rendering:

```java
@SubscribeEvent
public static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        // draw star overlays, constellation lines, etc.
    }
}
```

### Available Stages

```
Stage.AFTER_SKY
Stage.AFTER_SOLID_BLOCKS
Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS
Stage.AFTER_CUTOUT_BLOCKS
Stage.AFTER_ENTITIES
Stage.AFTER_BLOCK_ENTITIES
Stage.AFTER_TRANSLUCENT_BLOCKS
Stage.AFTER_TRIPWIRE_BLOCKS
Stage.AFTER_PARTICLES
Stage.AFTER_WEATHER
Stage.AFTER_LEVEL
```

### FogRenderer / Custom Fog Color

Use the `EntityViewRenderEvent$FogColors` and `EntityViewRenderEvent$RenderFogEvent` events on the Forge bus to intercept and replace fog color/density.

---

## Item Rendering (IItemGeometryBakeable / ISTER)

For items needing per-frame rendering (e.g., animated crystals):

```java
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(MyItems.MY_ITEM.get(), new MyItemDecorator());
    }
}
```

---

## Shader / PostFX

Minecraft 1.20.1 exposes `PostChain` for applying post-process shader passes. Astral Sorcery uses custom shaders for its glow effects. Load via:

```java
PostChain chain = new PostChain(
    Minecraft.getInstance().getTextureManager(),
    Minecraft.getInstance().getResourceManager(),
    Minecraft.getInstance().getMainRenderTarget(),
    new ResourceLocation(MODID, "shaders/post/effect.json"));
chain.resize(width, height);
```

Apply in a render event after the appropriate stage.
