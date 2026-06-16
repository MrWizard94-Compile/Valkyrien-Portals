# Forge 1.20.1 — Parchment Mappings & Dev Tools

Sources:
- https://parchmentmc.org/docs/getting-started.html
- https://github.com/McJtyMods/ModTutorials
- https://github.com/Kaupenjoe/Forge-Course-1.20.X

---

## Parchment Mappings

Parchment adds **parameter names and javadocs** on top of official Mojang mappings. Without it, every decompiled method parameter is `p_12345_` — nearly unreadable. This is one of the most impactful QoL upgrades for this port.

### setup (settings.gradle)

```groovy
pluginManagement {
    repositories {
        maven { url = 'https://maven.parchmentmc.org' }
        maven { url = 'https://maven.minecraftforge.net' }
        gradlePluginPortal()
    }
}
```

### setup (build.gradle)

```groovy
plugins {
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
    id 'org.parchmentmc.librarian.forgegradle' version '1.+'  // MUST be below ForgeGradle
    // ...
}

minecraft {
    mappings channel: 'parchment', version: '2023.09.03-1.20.1'
    // ...
}
```

### Version Format

```
YYYY.MM.DD-MCVersion
```

Same MC version: `2023.09.03-1.20.1`

Older mappings on newer environment: `1.19.4-2023.06.26-1.20.1`

Find latest version: https://parchmentmc.org/docs/getting-started.html

**All parameters are prefixed with `p`** in the generated sources (e.g. `pLevel`, `pPos`, `pState`).

---

## McJty Tutorial Series (1.20)

Website: https://mcjty.eu/docs/1.20
GitHub (all episodes): https://github.com/McJtyMods/ModTutorials

| Episode | Topic | GitHub |
|---------|-------|--------|
| 1 | Basics, mod setup, first block/item | https://github.com/McJty/Tut4_1Basics |
| 2 | Block entities, datagen, BER | https://github.com/McJty/Tut4_2Block |
| 3 | Block properties, networking, GUI | https://github.com/McJty/Tut4_2Block |
| 4 | Power generation, IEnergyStorage | https://github.com/McJty/Tut4_3Power |
| 5 | Cable system, BakedModels | https://github.com/McJty/Tut4_3Power |

All episode source branches are in the McJtyMods GitHub. These are the most directly applicable real-world 1.20.1 Forge examples.

---

## Kaupenjoe Forge 1.20.x Course

GitHub: https://github.com/Kaupenjoe/Forge-Course-1.20.X

Covers:
- Block/Item registration, properties, datagen
- Custom tools and armor
- Custom food and potions
- Ore generation and worldgen
- Custom GUI/Container
- Fluid registration
- Custom enchantments
- Custom dimensions

Each feature has its own commit — easy to browse to exactly what you need.

---

## TheGreyGhost MinecraftByExample

GitHub: https://github.com/TheGreyGhost/MinecraftByExample

A broad example mod with one class per concept. Includes rendering, models, particles, capabilities — very useful as a lookup when you need a working isolated example.

---

## Forge Test Mods (Official Examples in Forge Source)

The Forge repo itself contains test mods for every system. Browse at:
`src/test/java/net/minecraftforge/debug/` in the [MinecraftForge repo](https://github.com/MinecraftForge/MinecraftForge/tree/1.20.x/src/test/java/net/minecraftforge/debug)

Key test files:
```
gameplay/loot/GlobalLootModifiersTest.java
rendering/RenderLevelStageEventTest.java
block/CustomShapeBlockTest.java
entity/EntityAttributeTest.java
fluid/FluidTypeTest.java
```

These are the canonical, always-working examples written by the Forge team.

---

## ForgeJavaDocs (1.20.1)

Full Javadoc for the Forge 1.20.1 API:
https://lexxie.dev/forge/1.20.1/

Good for looking up method signatures when you know the class but not the exact method name.

---

## Useful Utilities

### Checking current Forge version at runtime

```java
String forgeVersion = net.minecraftforge.versions.forge.ForgeVersion.getVersion();
// e.g. "47.2.32"
```

### Getting mod list

```java
Map<String, IModInfo> mods = ModList.get().getMods().stream()
    .collect(Collectors.toMap(IModInfo::getModId, m -> m));
boolean hasJEI = ModList.get().isLoaded("jei");
```

### Checking physical side

```java
FMLEnvironment.dist == Dist.CLIENT  // physical client
FMLEnvironment.dist == Dist.DEDICATED_SERVER
```

### Deferred registration helper shortcut pattern

Many mods use a helper to reduce boilerplate:

```java
// Instead of always specifying ForgeRegistries.BLOCKS...
public static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
    RegistryObject<T> obj = BLOCKS.register(name, block);
    ITEMS.register(name, () -> new BlockItem(obj.get(), new Item.Properties()));
    return obj;
}
```
