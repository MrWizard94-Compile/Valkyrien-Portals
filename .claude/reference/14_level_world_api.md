# Forge 1.20.1 — Level / World API

Sources:
- https://lexxie.dev/forge/1.20.1/net/minecraft/server/level/ServerLevel.html
- https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.19.3/net/minecraft/server/level/ServerLevel.html

---

## Level Hierarchy

```
Level  (abstract, both sides)
├── ServerLevel  (server only)
└── ClientLevel  (client only)
```

Always check `level.isClientSide` before doing server logic. Never cast directly without checking.

---

## Commonly Used Level Methods

### Block Access

```java
BlockState state = level.getBlockState(pos);
BlockEntity be   = level.getBlockEntity(pos);
FluidState fluid = level.getFluidState(pos);

boolean isLoaded = level.isLoaded(pos);
boolean hasChunk = level.hasChunkAt(pos);

// Set a block (flags control update behavior)
level.setBlock(pos, newState, Block.UPDATE_ALL);
level.setBlock(pos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
level.removeBlock(pos, false);  // false = don't drop items

// Notify clients of block entity data change
level.sendBlockUpdated(pos, oldState, newState, Block.UPDATE_ALL);
```

### Update Flags

```java
Block.UPDATE_NEIGHBORS   // 1 — notify adjacent blocks
Block.UPDATE_CLIENTS     // 2 — send to clients
Block.UPDATE_INVISIBLE   // 4 — no visible update
Block.UPDATE_IMMEDIATE   // 8 — sync update on client
Block.UPDATE_KNOWN_SHAPE // 16 — skip neighbor shape updates
Block.UPDATE_ALL         // UPDATE_NEIGHBORS | UPDATE_CLIENTS (0x3)
Block.UPDATE_ALL_IMMEDIATE // 0xB — neighbors + clients + immediate
```

### Lighting

```java
int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
int skyLight   = level.getBrightness(LightLayer.SKY, pos);
int combined   = LevelLightEngine.getPackedFullBrightness(pos); // for rendering
```

### Random Access

```java
// Grab level's RNG (server)
RandomSource rand = level.random;

// For seeded per-position randomness
level.getRandom()
```

---

## ServerLevel-Specific

```java
// Get the MinecraftServer
MinecraftServer server = serverLevel.getServer();

// Get dimension key
ResourceKey<Level> dimKey = serverLevel.dimension();
// e.g. Level.OVERWORLD, Level.NETHER, Level.END

// All loaded players in this dimension
List<ServerPlayer> players = serverLevel.players();

// Find players near a point
List<ServerPlayer> nearby = serverLevel.getPlayers(
    p -> p.distanceToSqr(x, y, z) < 256.0);

// Spawn entity
serverLevel.addFreshEntity(entity);

// Get SavedData (see 07_saved_data.md)
serverLevel.getDataStorage().computeIfAbsent(...);

// Explosion
serverLevel.explode(null, x, y, z, radius, Level.ExplosionInteraction.BLOCK);

// Find nearest structure
BlockPos structPos = serverLevel.findNearestMapStructure(
    ModStructureTags.MY_STRUCTURE, origin, 100, false);
```

---

## Accessing Other Dimensions

```java
// From a ServerLevel or anywhere with MinecraftServer
MinecraftServer server = serverLevel.getServer();

ServerLevel overworld = server.overworld();
ServerLevel nether    = server.getLevel(Level.NETHER);
ServerLevel end       = server.getLevel(Level.END);
ServerLevel custom    = server.getLevel(MY_DIMENSION_KEY);
```

---

## Level/Chunk Load Checks

```java
// Safe block access — will not force-load chunks
if (level.isLoaded(pos)) {
    BlockState state = level.getBlockState(pos);
}

// Check if a chunk is actually loaded (not just accessible)
ChunkAccess chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4,
    ChunkStatus.FULL, false);  // false = don't generate if missing
if (chunk != null) { ... }
```

---

## Tick Scheduling

```java
// Schedule a block tick (fires once after N ticks)
level.scheduleTick(pos, block, delayTicks);
level.scheduleTick(pos, fluid, delayTicks);

// Schedule with priority
level.scheduleTick(pos, block, delayTicks, TickPriority.NORMAL);
```

---

## Time / Day Cycle

```java
long gameTime    = level.getGameTime();    // total ticks since world creation
long dayTime     = level.getDayTime();     // ticks within the current day (0–24000)
float skyAngle   = level.getSunAngle(partialTick); // 0–2π
float starBright = level.getStarBrightness(partialTick); // 0.0–1.0
boolean isDay    = level.isDay();
boolean isNight  = level.isNight();

// Set time (server only)
((ServerLevel) level).setDayTime(6000L); // noon
```

Astral Sorcery uses `getSunAngle`, `getStarBrightness`, and `dayTime` extensively for starlight calculation.

---

## Explosions

```java
level.explode(
    causingEntity,      // null for environmental
    damageSource,       // can be null  
    damageCalculator,   // null for default
    x, y, z,           // center
    radius,
    causeFire,          // boolean
    Level.ExplosionInteraction.BLOCK  // or TNT, MOB, NONE
);
```

---

## Raycasting / Ray Tracing

```java
// Ray from eye to look direction
ClipContext ctx = new ClipContext(
    eyePos, targetPos,
    ClipContext.Block.COLLIDER,
    ClipContext.Fluid.NONE,
    player);

BlockHitResult hit = level.clip(ctx);

// Entity ray trace
EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
    level, player, eyePos, targetPos,
    new AABB(eyePos, targetPos).inflate(1.0),
    e -> !e.isSpectator());
```

---

## Biome Access

```java
Holder<Biome> biome = level.getBiome(pos);
ResourceLocation biomeId = level.getBiome(pos).unwrapKey()
    .map(ResourceKey::location).orElse(null);

// Check tag membership
boolean isStarfield = biome.is(ModTags.Biomes.HAS_STARFIELD);
```

---

## Forge Level Events

```java
// Fired on Forge event bus
BlockEvent.BreakEvent         — block broken
BlockEvent.EntityPlaceEvent   — block placed
ChunkEvent.Load               — chunk loaded
ChunkEvent.Unload             — chunk unloaded  
LevelEvent.Load               — dimension loaded
LevelEvent.Save               — dimension saving
LevelEvent.Unload             — dimension unloaded
```
