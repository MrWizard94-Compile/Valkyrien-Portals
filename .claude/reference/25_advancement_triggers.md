# Forge 1.20.1 — Custom Advancement Triggers

Sources:
- https://docs.minecraftforge.net/en/latest/resources/server/advancements/
- https://forums.minecraftforge.net/topic/150844-how-to-create-custom-advancement-triggers119/

---

## Overview

Custom advancement triggers let you fire advancement criteria from your own code (e.g. "player first attunes a crystal", "player discovers a constellation"). They hook into Minecraft's advancement system and can be checked in advancement JSON files.

---

## Step 1 — Define the TriggerInstance

A `TriggerInstance` holds the conditions checked when the trigger fires.

```java
public class AttuneConstellationTrigger 
        extends SimpleCriterionTrigger<AttuneConstellationTrigger.TriggerInstance> {

    public static final ResourceLocation ID =
        new ResourceLocation(MODID, "attune_constellation");

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        // Optional conditions — null means "any constellation"
        @Nullable
        private final ResourceLocation constellationId;

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                ExtraCodecs.strictOptionalField(EntityPredicate.ADVANCEMENT_CODEC, "player")
                    .forGetter(TriggerInstance::getPlayerPredicate),
                ExtraCodecs.strictOptionalField(ResourceLocation.CODEC, "constellation")
                    .forGetter(t -> Optional.ofNullable(t.constellationId))
            ).apply(inst, (player, constellation) ->
                new TriggerInstance(player, constellation.orElse(null)))
        );

        public TriggerInstance(Optional<ContextAwarePredicate> player,
                                @Nullable ResourceLocation constellationId) {
            super(ID, player);
            this.constellationId = constellationId;
        }

        // Factory for code use
        public static TriggerInstance any() {
            return new TriggerInstance(Optional.empty(), null);
        }

        public static TriggerInstance forConstellation(ResourceLocation id) {
            return new TriggerInstance(Optional.empty(), id);
        }

        public boolean matches(ResourceLocation attuned) {
            return constellationId == null || constellationId.equals(attuned);
        }
    }

    // Called from your mod code to fire the trigger
    public void trigger(ServerPlayer player, ResourceLocation constellationId) {
        trigger(player, instance -> instance.matches(constellationId));
    }
}
```

---

## Step 2 — Register the Trigger

In **Forge 1.20.1**, `CriterionTriggers` uses a private registry. Registration requires reflection or a static init pattern:

```java
public class ModTriggers {

    public static final AttuneConstellationTrigger ATTUNE_CONSTELLATION =
        register(new AttuneConstellationTrigger());

    private static <T extends CriterionTrigger<?>> T register(T trigger) {
        return CriterionTriggers.register(trigger);
    }
}
```

Call `ModTriggers.class.getSimpleName()` or just reference `ModTriggers.ATTUNE_CONSTELLATION` somewhere in your mod init to force class loading, which triggers registration.

The cleanest approach — force the class to load during `FMLCommonSetupEvent`:

```java
@SubscribeEvent
public void onCommonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(() -> {
        // touch the class to register triggers
        ModTriggers.ATTUNE_CONSTELLATION.getId();
    });
}
```

---

## Step 3 — Fire the Trigger

From your server-side game code:

```java
// When a player attunes a crystal to Discidia
if (player instanceof ServerPlayer serverPlayer) {
    ModTriggers.ATTUNE_CONSTELLATION.trigger(
        serverPlayer,
        new ResourceLocation(MODID, "discidia")
    );
}
```

---

## Step 4 — Advancement JSON

`data/<modid>/advancements/attune_first_constellation.json`:

```json
{
  "display": {
    "icon": { "item": "astralsorcery:attuned_crystal" },
    "title": { "translate": "advancements.astralsorcery.attune_first_constellation.title" },
    "description": { "translate": "advancements.astralsorcery.attune_first_constellation.desc" },
    "frame": "challenge",
    "show_toast": true,
    "announce_to_chat": true
  },
  "parent": "astralsorcery:root",
  "criteria": {
    "attune": {
      "trigger": "astralsorcery:attune_constellation"
    }
  }
}
```

With a constellation condition:

```json
"criteria": {
  "attune_discidia": {
    "trigger": "astralsorcery:attune_constellation",
    "conditions": {
      "constellation": "astralsorcery:discidia"
    }
  }
}
```

---

## Datagen for Advancements

```java
public class ModAdvancementProvider implements DataProvider {

    @Override
    public void run(CachedOutput output) {
        Set<ResourceLocation> ids = new HashSet<>();
        Consumer<Advancement> writer = advancement -> {
            if (!ids.add(advancement.getId())) {
                throw new IllegalStateException("Duplicate advancement: " + advancement.getId());
            }
            // write to output...
        };

        new ModAdvancements().generate(writer);
    }
}

public class ModAdvancements implements Consumer<Consumer<Advancement>> {
    @Override
    public void accept(Consumer<Advancement> writer) {
        Advancement root = Advancement.Builder.advancement()
            .display(ModItems.ROCK_CRYSTAL.get(),
                Component.translatable("advancements.astralsorcery.root.title"),
                Component.translatable("advancements.astralsorcery.root.desc"),
                new ResourceLocation(MODID, "textures/gui/advancement_bg.png"),
                FrameType.TASK, false, false, false)
            .save(writer, MODID + ":root");

        Advancement.Builder.advancement()
            .parent(root)
            .display(ModItems.ATTUNED_CRYSTAL.get(),
                Component.translatable("advancements.astralsorcery.attune_first.title"),
                Component.translatable("advancements.astralsorcery.attune_first.desc"),
                null, FrameType.CHALLENGE, true, true, false)
            .addCriterion("attune",
                AttuneConstellationTrigger.TriggerInstance.any())
            .save(writer, MODID + ":attune_first_constellation");
    }
}
```
