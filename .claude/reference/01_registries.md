# Forge 1.20.1 — Registry System

Source: https://docs.minecraftforge.net/en/1.20.1/concepts/registries/

## Core Concept

Registries are map-like structures that assign objects to `ResourceLocation` keys. Every registrable type (Block, Item, SoundEvent, etc.) has its own registry accessible through `ForgeRegistries`.

Registry names must be unique within a registry, but can collide across different registries without conflict.

## Method 1 — DeferredRegister (Recommended)

Maintains a supplier list and defers registration until `RegisterEvent` fires. Safe for static initialization.

```java
// Declare the register (usually in a Blocks/Items/etc. class)
public static final DeferredRegister<Block> BLOCKS =
    DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

// Register an entry
public static final RegistryObject<Block> ROCK_BLOCK =
    BLOCKS.register("rock", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));

// In your @Mod constructor — attach to mod event bus
public ExampleMod() {
    BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
}
```

`RegistryObject<T>` is a lazy wrapper. Do not call `.get()` until after registration has completed (i.e., not in static initializers that run at class-load time before `RegisterEvent`).

## Method 2 — RegisterEvent (Manual)

Fires after mod constructors, before config loading.

```java
@SubscribeEvent
public void register(RegisterEvent event) {
    event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
        helper.register(new ResourceLocation(MODID, "example_block"), new Block(...));
    });
}
```

## Referencing Registered Objects

**RegistryObject.create()** — lookup by ResourceLocation:

```java
public static final RegistryObject<Item> BOW =
    RegistryObject.create(new ResourceLocation("minecraft:bow"), ForgeRegistries.ITEMS);
```

**@ObjectHolder** — field injection:
- Class-level annotation sets the default namespace.
- Fields must be at minimum `public static`.
- Explicit registry name + value required per field.

## Custom Registries

### Via NewRegistryEvent

```java
registry = event.create(new RegistryBuilder<CustomType>()
    .setName(new ResourceLocation(MODID, "custom"))
    .setType(CustomType.class));
```

### Via DeferredRegister

```java
DeferredRegister<CustomType> REGISTER =
    DeferredRegister.create("custom_registry", MODID);

// Call makeRegistry() BEFORE registering to the mod event bus
Supplier<IForgeRegistry<CustomType>> registry =
    REGISTER.makeRegistry(() -> new RegistryBuilder<>());
```

## Missing Entry Handling (MissingMappingsEvent)

Fired on the Forge event bus when a previously registered entry is missing (e.g., removed from a mod).

| Action | Behavior |
|--------|----------|
| `IGNORE` | Silently drops the mapping |
| `WARN` | Logs a warning |
| `FAIL` | Blocks world loading |
| `REMAP` | Redirects to another registered object |

Default: prompts user confirmation before world loading.

## Common ForgeRegistries Keys

```java
ForgeRegistries.BLOCKS
ForgeRegistries.ITEMS
ForgeRegistries.BLOCK_ENTITY_TYPES
ForgeRegistries.MENU_TYPES
ForgeRegistries.ENTITY_TYPES
ForgeRegistries.SOUND_EVENTS
ForgeRegistries.PARTICLE_TYPES
ForgeRegistries.RECIPE_TYPES
ForgeRegistries.RECIPE_SERIALIZERS
ForgeRegistries.BIOMES
```
