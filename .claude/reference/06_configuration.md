# Forge 1.20.1 — Configuration (ForgeConfigSpec)

Source: https://docs.minecraftforge.net/en/1.20.1/misc/config/
Community reference: https://forums.minecraftforge.net/topic/125772-the-ultimate-guide-to-configs/

---

## Config Types

| Type | Loaded on | Synced to client | Location |
|------|-----------|-----------------|----------|
| `CLIENT` | Physical client only | No | `.minecraft/config/<modid>-client.toml` |
| `COMMON` | Both sides | No | `.minecraft/config/<modid>-common.toml` (client) / `server/config/` (server) |
| `SERVER` | Server only | **Yes** (pushed to clients) | `saves/<world>/serverconfig/<modid>-server.toml` |

Use `SERVER` for game-balance values that need to be authoritative. Use `COMMON` for feature toggles. Use `CLIENT` for visual/audio preferences.

---

## Defining a Config Class

Pattern: pass `ForgeConfigSpec.Builder` into constructor; build with `configure()`.

```java
public class ModConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ModConfig COMMON;

    static {
        Pair<ModConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder()
            .configure(ModConfig::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    // Config value fields
    public final ForgeConfigSpec.IntValue starBrightnessBonus;
    public final ForgeConfigSpec.BooleanValue enableConstellations;
    public final ForgeConfigSpec.DoubleValue starfallChance;
    public final ForgeConfigSpec.EnumValue<SomeEnum> renderMode;

    public ModConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Astral Sorcery Settings").push("general");

        starBrightnessBonus = builder
            .comment("Bonus to star brightness (0-100)")
            .translation(MODID + ".config.starBrightnessBonus")
            .defineInRange("starBrightnessBonus", 10, 0, 100);

        enableConstellations = builder
            .comment("Enable constellation rendering")
            .define("enableConstellations", true);

        starfallChance = builder
            .defineInRange("starfallChance", 0.05, 0.0, 1.0);

        renderMode = builder
            .defineEnum("renderMode", SomeEnum.DEFAULT);

        builder.pop();
    }
}
```

---

## Registration (in @Mod constructor)

```java
@Mod(MODID)
public class AstralSorcery {
    public AstralSorcery() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
```

Custom file name variant: `registerConfig(Type.COMMON, spec, "mymod-custom.toml")`

---

## Reading Config Values

```java
int bonus = ModConfig.COMMON.starBrightnessBonus.get();
boolean enabled = ModConfig.COMMON.enableConstellations.get();
double chance = ModConfig.COMMON.starfallChance.get();
```

Values are cached internally; `.get()` does not re-read the file every call.

---

## Reacting to Config Changes

```java
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ConfigEvents {

    @SubscribeEvent
    public static void onModConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ModConfig.COMMON_SPEC) {
            // re-cache computed values
        }
    }

    @SubscribeEvent
    public static void onModConfigReloading(ModConfigEvent.Reloading event) {
        // same as above but for /reload
    }
}
```

---

## Config Builder Methods Reference

```java
builder.comment("text")                       // description in .toml file
builder.translation("key")                    // i18n key for config GUIs
builder.worldRestart()                        // flag — needs world restart
builder.push("category")                      // open a sub-section
builder.pop()                                 // close the sub-section

builder.define("key", defaultValue)                        // boolean or generic
builder.defineInRange("key", default, min, max)            // number with bounds
builder.defineInList("key", default, allowedValues)        // whitelist
builder.defineList("key", defaultList, validator)          // list
builder.defineListAllowEmpty("key", defaultList, validator)
builder.defineEnum("key", defaultEnum)                     // enum
```
