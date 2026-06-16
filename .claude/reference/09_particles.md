# Forge 1.20.1 — Custom Particles

Sources:
- https://docs.minecraftforge.net/en/1.18.x/gameeffects/particles/
- https://forge.gemwire.uk/wiki/Particles/1.18

---

## Overview

Two sides to every particle:

| Side | Class | Role |
|------|-------|------|
| **Server/Both** | `ParticleType<O>` | Holds `ParticleOptions` (data synced to client) |
| **Client** | `ParticleProvider<O>` | Creates the visual `Particle` from options |

---

## Step 1 — Register ParticleType

```java
public class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
        DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MODID);

    public static final RegistryObject<SimpleParticleType> SPARK =
        PARTICLE_TYPES.register("spark", () -> new SimpleParticleType(false));
        // false = particle not always shown regardless of particle settings
}
```

Register the DeferredRegister to the mod event bus in your mod constructor.

---

## Step 2 — Particle Options (for data-carrying particles)

`SimpleParticleType` already includes its own `ParticleOptions`. For custom data (e.g., color), extend `ParticleOptions`:

```java
public class ColoredParticleOptions implements ParticleOptions {

    public static final Deserializer<ColoredParticleOptions> DESERIALIZER = ...;

    private final int color;

    public ColoredParticleOptions(ParticleType<ColoredParticleOptions> type, int color) {
        this.color = color;
    }

    @Override
    public ParticleType<ColoredParticleOptions> getType() {
        return ModParticles.COLORED.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(color);
    }

    @Override
    public String writeToString() {
        return ForgeRegistries.PARTICLE_TYPES.getKey(getType()) + " " + color;
    }
}
```

---

## Step 3 — Particle Visual (client only)

```java
public class SparkParticle extends TextureSheetParticle {

    protected SparkParticle(ClientLevel level, double x, double y, double z,
                            double dx, double dy, double dz) {
        super(level, x, y, z, dx, dy, dz);
        this.lifetime = 20;
        this.gravity = 0.0f;
        this.alpha = 1.0f;
        this.scale = 0.1f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
```

---

## Step 4 — ParticleProvider (client only)

```java
public class SparkParticleProvider implements ParticleProvider<SimpleParticleType> {

    private final SpriteSet sprites;

    public SparkParticleProvider(SpriteSet sprites) {
        this.sprites = sprites;
    }

    @Override
    @Nullable
    public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                   double x, double y, double z,
                                   double dx, double dy, double dz) {
        SparkParticle p = new SparkParticle(level, x, y, z, dx, dy, dz);
        p.pickSprite(sprites);  // assigns a texture from the registered sprite set
        return p;
    }
}
```

---

## Step 5 — Register Provider (ParticleFactoryRegisterEvent)

**Client-side only.** Register in a `@Mod.EventBusSubscriber` with `value = Dist.CLIENT`:

```java
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPARK.get(), SparkParticleProvider::new);
    }
}
```

`RegisterParticleProvidersEvent` replaced `ParticleFactoryRegisterEvent` in 1.19.4+. For **1.20.1** use `RegisterParticleProvidersEvent`.

Methods:
- `event.registerSpriteSet(type, provider)` — provider uses a `SpriteSet`
- `event.registerSprite(type, provider)` — provider uses a single sprite
- `event.registerSpecial(type, provider)` — for custom/programmatic providers

---

## Step 6 — Particle Texture JSON

Create `assets/<modid>/particles/spark.json`:

```json
{
  "textures": [
    "astralsorcery:spark_0",
    "astralsorcery:spark_1",
    "astralsorcery:spark_2"
  ]
}
```

Textures live in `assets/<modid>/textures/particle/spark_0.png` etc.

---

## Spawning Particles

### Server-side (synced to clients automatically)

```java
level.sendParticles(
    ModParticles.SPARK.get(),  // particle type
    x, y, z,                   // position
    count,                     // number of particles
    dx, dy, dz,                // spread
    speed);                    // speed modifier
```

### Client-side (local only, for visual effects)

```java
// In client code
Minecraft.getInstance().level.addParticle(
    ModParticles.SPARK.get(),
    x, y, z,
    dx, dy, dz);
```

---

## Common ParticleRenderType Values

```java
ParticleRenderType.PARTICLE_SHEET_OPAQUE       // opaque, batched
ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT  // translucent, sorted
ParticleRenderType.PARTICLE_SHEET_LIT          // affected by world light
ParticleRenderType.TERRAIN_SHEET               // uses terrain texture atlas
ParticleRenderType.NO_RENDER                   // invisible (logic-only)
```
