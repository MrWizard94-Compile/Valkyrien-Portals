# Forge 1.20.1 — SpongeMixin Setup

Sources:
- https://github.com/SpongePowered/Mixin/wiki/Mixins-on-Minecraft-Forge
- https://darkhax.net/2020/07/mixins

---

## Important: Don't Shade Mixin

"Mixin now ships as a library with Minecraft Forge which means it is no longer necessary to shade Mixin into your mod jar. In fact doing so will likely cause issues."

---

## build.gradle Changes

### 1. Add MixinGradle plugin (in `plugins {}` block)

```groovy
plugins {
    id 'org.spongepowered.mixin' version '0.7.+'
    // ... existing plugins
}
```

### 2. Add annotation processor dependency

```groovy
dependencies {
    annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'
    // ... existing deps
}
```

The `:processor` classifier is a fat JAR containing required upstream dependencies.

### 3. Configure the mixin extension

```groovy
mixin {
    add sourceSets.main, 'mixins.astralsorcery.refmap.json'
    config 'astralsorcery.mixins.json'

    debug.verbose = true
    debug.export = true  // exports patched classes for inspection
}
```

All configs in the same SourceSet must specify the **same refmap name**.

---

## settings.gradle / repositories

MixinGradle must be resolvable. Add to `pluginManagement`:

```groovy
pluginManagement {
    repositories {
        maven { url = 'https://repo.spongepowered.org/repository/maven-public/' }
        // ... existing
    }
}
```

---

## Mixin Config JSON (`src/main/resources/astralsorcery.mixins.json`)

```json
{
  "required": true,
  "package": "hellfirepvp.astralsorcery.mixin",
  "compatibilityLevel": "JAVA_17",
  "refmap": "mixins.astralsorcery.refmap.json",
  "mixins": [
    "MixinSomeServerClass"
  ],
  "client": [
    "MixinLevelRenderer",
    "MixinGameRenderer"
  ],
  "server": [],
  "minVersion": "0.8"
}
```

- `"mixins"` — applies on both sides
- `"client"` — physical client only
- `"server"` — dedicated server only

Reference the config JSON in `META-INF/mods.toml`:

```toml
[[mixins]]
    config="astralsorcery.mixins.json"
```

---

## Writing a Mixin Class

```java
package hellfirepvp.astralsorcery.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Inject(method = "renderSky", at = @At("HEAD"))
    private void onRenderSkyHead(PoseStack poseStack, Matrix4f projectionMatrix,
                                  float partialTick, Camera camera,
                                  boolean isFoggy, Runnable skyFogSetup,
                                  CallbackInfo ci) {
        // inject at start of renderSky
    }
}
```

### Common Mixin Annotations

| Annotation | Purpose |
|---|---|
| `@Mixin(Target.class)` | Declares the target class |
| `@Inject` | Inject code at a point |
| `@Overwrite` | Replace a method entirely (requires `@author` + `@reason` javadoc) |
| `@Redirect` | Replace a specific method call inside a method |
| `@ModifyArg` | Change one argument of a called method |
| `@ModifyVariable` | Change a local variable |
| `@Shadow` | Reference a field/method from the target class |
| `@Accessor` | Generate getter/setter for a private field |
| `@Invoker` | Generate caller for a private method |

### CallbackInfo / CallbackInfoReturnable

```java
// Cancel execution
ci.cancel();  // only on @Cancelable methods

// Return early from a void method (Inject with cancellable = true)
@Inject(method = "myMethod", at = @At("HEAD"), cancellable = true)
private void myInject(CallbackInfo ci) {
    ci.cancel();
}

// Override return value
@Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
private void myInject(CallbackInfoReturnable<Integer> cir) {
    cir.setReturnValue(42);
}
```

---

## AT vs Mixin: When to Use Which

| Use AT | Use Mixin |
|--------|-----------|
| Just need to access a private field/method | Need to inject into the middle of a method |
| Remove `final` from a field or class | Need to intercept and modify return values |
| Simple visibility widening | Need to alter method call arguments (Redirect) |
| | Need to hook rendering pipeline specifically |

In general: **prefer ATs**. Mixins are fragile across MC versions. Only use mixins when there is no Forge event hook and ATs alone can't accomplish the goal.

---

## 1.20.1-Specific Known Issues

- The MixinGradle plugin `0.7.x` can have classpath ordering issues in some Forge MDK versions. If mixins aren't being applied, check `debug.log` for "Mixin subsystem version" confirmation.
- When using `@Mixin` on inner classes, use `targets = "net.minecraft.the.ClassName$InnerClass"` (string form) instead of the `.class` form.
