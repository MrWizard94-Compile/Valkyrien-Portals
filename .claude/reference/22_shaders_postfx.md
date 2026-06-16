# Forge 1.20.1 — Shaders & Post-Processing Effects

---

## Overview

Minecraft 1.20.1 uses two distinct shader systems:

| System | Class | Use |
|--------|-------|-----|
| **Core shaders** | `ShaderInstance` | Geometry rendering (blocks, entities, particles) |
| **Post-process effects** | `PostChain` | Full-screen effects (blur, glow, outline, bloom) |

Astral Sorcery primarily needs `PostChain` for glow effects and `RenderLevelStageEvent` injection for star/constellation overlays.

---

## PostChain — Full-Screen Post-Processing

### Loading

```java
// Client-side only — do in FMLClientSetupEvent or lazily on first use
PostChain chain = new PostChain(
    Minecraft.getInstance().getTextureManager(),
    Minecraft.getInstance().getResourceManager(),
    Minecraft.getInstance().getMainRenderTarget(),
    new ResourceLocation(MODID, "shaders/post/glow_effect.json")
);
chain.resize(Minecraft.getInstance().getWindow().getWidth(),
             Minecraft.getInstance().getWindow().getHeight());
```

### Applying the Effect

Call inside a render event, typically after entities or particles:

```java
chain.process(partialTick);
Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
```

### Re-loading on Window Resize

Subscribe to `ScreenshotEvent` or override `resizeDisplay`; rebuild the PostChain on resize:

```java
@SubscribeEvent
public static void onScreenResize(ScreenEvent.Closing event) {
    if (glowChain != null) {
        glowChain.close();
        glowChain = null;  // will be lazily re-created
    }
}
```

---

## PostChain JSON Format

`assets/<modid>/shaders/post/glow_effect.json`:

```json
{
  "targets": [
    "swap"
  ],
  "passes": [
    {
      "name": "blur",
      "intarget": "minecraft:main",
      "outtarget": "swap",
      "auxtargets": [],
      "uniforms": [
        { "name": "BlurRadius", "values": [ 5.0 ] },
        { "name": "BlurDir", "values": [ 1.0, 0.0 ] }
      ]
    },
    {
      "name": "blur",
      "intarget": "swap",
      "outtarget": "minecraft:main",
      "uniforms": [
        { "name": "BlurRadius", "values": [ 5.0 ] },
        { "name": "BlurDir", "values": [ 0.0, 1.0 ] }
      ]
    }
  ]
}
```

Vanilla post-process shader programs live in `assets/minecraft/shaders/program/`. Reference them by name (e.g. `"blur"`, `"blit"`, `"transparency"`).

---

## Writing a Custom Program Shader

Create `assets/<modid>/shaders/program/my_effect.json`:

```json
{
  "blend": { "func": "add", "srcrgb": "one", "dstrgb": "one" },
  "vertex": "astralsorcery:my_effect",
  "fragment": "astralsorcery:my_effect",
  "attributes": [ "Position" ],
  "samplers": [
    { "name": "DiffuseSampler" }
  ],
  "uniforms": [
    { "name": "ProjMat",    "type": "matrix4x4", "count": 16, "values": [ 1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1 ] },
    { "name": "OutSize",    "type": "float",      "count": 2,  "values": [ 1, 1 ] },
    { "name": "GlowColor",  "type": "float",      "count": 4,  "values": [ 1, 1, 1, 1 ] }
  ]
}
```

Vertex: `assets/<modid>/shaders/program/my_effect.vsh`
Fragment: `assets/<modid>/shaders/program/my_effect.fsh`

```glsl
// my_effect.fsh
#version 150

uniform sampler2D DiffuseSampler;
uniform vec4 GlowColor;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    fragColor = color * GlowColor;
}
```

---

## ShaderInstance — Core Rendering Shader

Used when you need a custom render type for geometry.

### Registering a ShaderInstance

```java
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    public static ShaderInstance starShader;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
            new ShaderInstance(
                event.getResourceProvider(),
                new ResourceLocation(MODID, "star_quad"),
                DefaultVertexFormat.POSITION_COLOR_TEX
            ),
            shader -> starShader = shader
        );
    }
}
```

Shader files: `assets/<modid>/shaders/core/star_quad.json`, `.vsh`, `.fsh`

### Using the Shader in a RenderType

```java
public static RenderType starRenderType() {
    return RenderType.create("star_quad",
        DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(
                () -> ClientModEvents.starShader))
            .setTextureState(new RenderStateShard.TextureStateShard(
                new ResourceLocation(MODID, "textures/effect/star.png"), false, false))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .createCompositeState(false));
}
```

---

## Sky / Constellation Rendering Pattern

The pattern for Astral Sorcery-style celestial overlays:

```java
@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class SkyRenderHandler {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!event.getCamera().getPosition().y > 60) return;  // optional height gate

        PoseStack poseStack = event.getPoseStack();
        float partialTick   = event.getPartialTick();
        Camera camera        = event.getCamera();

        poseStack.pushPose();

        // Align with camera rotation (sky doesn't move with player position)
        Vec3 camPos = camera.getPosition();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        float skyAngle = Minecraft.getInstance().level.getSunAngle(partialTick);
        float starBrightness = Minecraft.getInstance().level.getStarBrightness(partialTick);

        // Rotate for celestial sphere
        poseStack.mulPose(Axis.YP.rotationDegrees(-90f));
        poseStack.mulPose(Axis.XP.rotationDegrees(skyAngle * 360f));

        // Draw constellations, stars, etc.
        renderConstellations(poseStack, event.getProjectionMatrix(), starBrightness);

        poseStack.popPose();
    }
}
```

---

## RenderTarget / Framebuffer

```java
// Get the main framebuffer
RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();

// Custom framebuffer for effects
RenderTarget glowBuffer = new TextureTarget(width, height, true, false);
glowBuffer.setClearColor(0f, 0f, 0f, 0f);
glowBuffer.clear(false);

// Bind for writing
glowBuffer.bindWrite(true);
// ... render glowing objects ...
mainTarget.bindWrite(false);  // restore
```

---

## loadEffect() Convenience Method

For simple cases you can call Minecraft's built-in loader:

```java
// Only loads shaders/post/*.json from minecraft: namespace by default
// For mod-namespaced shaders, use PostChain directly (shown above)
Minecraft.getInstance().gameRenderer.loadEffect(
    new ResourceLocation("minecraft:shaders/post/blur.json")
);
```

This replaces the current active post-effect. Call with `null` to clear:

```java
Minecraft.getInstance().gameRenderer.loadEffect(null);
```
