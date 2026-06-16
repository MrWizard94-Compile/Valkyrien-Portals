# Forge 1.20.1 — Access Transformers

Source: https://docs.minecraftforge.net/en/latest/advanced/accesstransformers/
Community wiki: https://forge.gemwire.uk/wiki/Access_Transformers

---

## What They Are

Access Transformers (ATs) widen the visibility of or remove the `final` modifier from vanilla Minecraft classes, methods, and fields that are otherwise inaccessible to mods.

---

## Setup

### 1. Create the AT file

Create `src/main/resources/META-INF/accesstransformer.cfg`

### 2. Reference it in build.gradle

```groovy
minecraft {
    accessTransformer = file('src/main/resources/META-INF/accesstransformer.cfg')
    // ...
}
```

Forge looks for `META-INF/accesstransformer.cfg` in the JAR at runtime. The path must be exact.

After modifying the AT file, **re-run `genIntellijRuns` / `genEclipseRuns`** (or reboot the IDE) to pick up the changes.

---

## Syntax

```
<access_modifier> <class_name> [<member_name>] [<descriptor>]
```

### Access Modifiers

| Modifier | Effect |
|----------|--------|
| `public` | Makes member public |
| `protected` | Makes member protected |
| `default` | Package-private (removes public/protected) |
| `private` | (rarely used — makes things more restrictive) |

Append `+f` to also **add** `final`. Append `-f` to **remove** `final`.

Examples:
- `public` → make public, leave final unchanged
- `public-f` → make public AND remove final
- `public+f` → make public AND add final

---

## Class Names

Use the **binary name** (dots, not slashes) with inner classes using `$`:

```
net.minecraft.world.level.Level
net.minecraft.world.level.Level$ExplosionInteraction
```

---

## Field Names

For vanilla Minecraft fields, use the **SRG name** (obfuscation-safe name used in mappings):

```
# Format: <access> <class_binary_name> <srg_field_name>
public net.minecraft.world.entity.LivingEntity f_20966_

# Remove final from a field
public-f net.minecraft.world.item.ItemStack f_41591_
```

Find SRG names using:
- Your IDE's MCP/Mojmap mappings display
- MCPBot: `!gf <field_name>` in MCPBot Discord
- The `srg_names` in your Forge workspace's decompiled output

---

## Method Names

```
# Format: <access> <class_binary_name> <srg_method_name><descriptor>
public net.minecraft.world.level.Level m_7702_(Lnet/minecraft/world/phys/AABB;)Ljava/util/List;

# Constructors use <init>
public net.minecraft.world.level.block.entity.BlockEntity <init>(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/BlockState;)V
```

Descriptors use JVM internal type format:
- `Z` = boolean
- `I` = int
- `F` = float
- `D` = double
- `J` = long
- `B` = byte
- `C` = char
- `S` = short
- `V` = void
- `Lpackage/ClassName;` = reference type
- `[type]` = array

---

## Classes (visibility only, no descriptor)

```
public net.minecraft.world.level.block.SomeInternalClass
```

---

## Comments

Lines starting with `#` are comments:

```cfg
# Make LivingEntity knockback-related field accessible
public net.minecraft.world.entity.LivingEntity f_20966_
```

---

## Example accesstransformer.cfg

```cfg
# Allow reading/writing the internal starlight level field
public-f net.minecraft.world.level.Level f_46443_

# Access protected method in LevelRenderer  
public net.minecraft.client.renderer.LevelRenderer m_109619_(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/world/entity/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lcom/mojang/math/Matrix4f;)V

# Make a vanilla block's constructor public
public net.minecraft.world.level.block.GrassBlock <init>(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V
```

---

## Finding SRG Names in Practice

With Mojang mappings (the default for Forge 1.17+), the names in your IDE are the Mojmap (human-readable) names. To find the corresponding SRG name for an AT:

1. Check your `build/tmp/recompileJava/` or use the `srg_` prefixed names visible in intermediary
2. In Forge 1.20.1, Mojmap names ARE the SRG names — so for 1.20.1 you can use the readable names directly in ATs:

```cfg
# 1.20.1: Mojmap names work directly
public net.minecraft.world.entity.LivingEntity knockback
```

Confirm by checking [mcpbot](https://mcpbot.unascribed.com/) or looking at the Forge-provided mappings in your workspace.
