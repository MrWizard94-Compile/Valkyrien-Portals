# Forge 1.20.1 — Entity Attributes

Sources:
- https://medium.com/@colebot17/custom-attributes-in-forge-0a45425b4627
- https://forums.minecraftforge.net/topic/110247-how-would-one-go-about-registering-custom-attributes/

---

## What Attributes Are

Attributes define numeric properties of living entities (max health, movement speed, attack damage, etc.). Each attribute has a base value, which can be modified by `AttributeModifier` objects attached from equipment, effects, or capabilities. This is the primary mechanism for Astral Sorcery's perk system.

---

## Vanilla Attributes Reference

```java
Attributes.MAX_HEALTH
Attributes.MOVEMENT_SPEED
Attributes.ATTACK_DAMAGE
Attributes.ATTACK_SPEED
Attributes.ATTACK_KNOCKBACK
Attributes.ARMOR
Attributes.ARMOR_TOUGHNESS
Attributes.KNOCKBACK_RESISTANCE
Attributes.JUMP_STRENGTH        // horses only
Attributes.FOLLOW_RANGE
Attributes.FLYING_SPEED
ForgeMod.ENTITY_GRAVITY         // Forge-added
ForgeMod.REACH_DISTANCE         // Forge-added
ForgeMod.SWIM_SPEED             // Forge-added
ForgeMod.NAMETAG_DISTANCE       // Forge-added
```

---

## Registering a Custom Attribute

```java
public class ModAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(ForgeRegistries.ATTRIBUTES, MODID);

    // RangedAttribute(descriptionId, defaultValue, min, max)
    public static final RegistryObject<Attribute> STARLIGHT_CAPACITY =
        ATTRIBUTES.register("starlight_capacity",
            () -> new RangedAttribute(
                "attribute.astralsorcery.starlight_capacity",
                100.0,   // default
                0.0,     // min
                10000.0  // max
            ).setSyncable(true));  // sync to client

    public static final RegistryObject<Attribute> CRYSTAL_EFFICIENCY =
        ATTRIBUTES.register("crystal_efficiency",
            () -> new RangedAttribute(
                "attribute.astralsorcery.crystal_efficiency",
                1.0, 0.0, 10.0
            ).setSyncable(true));
}
```

Register in mod constructor: `ModAttributes.ATTRIBUTES.register(modBus)`

---

## Adding Attributes to Players

Players must have attribute instances registered before the attributes can be used. Hook `EntityAttributeModificationEvent` on the **mod bus**:

```java
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {

    @SubscribeEvent
    public static void onAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, ModAttributes.STARLIGHT_CAPACITY.get());
        event.add(EntityType.PLAYER, ModAttributes.CRYSTAL_EFFICIENCY.get());
    }
}
```

For custom entities, add in `EntityAttributeCreationEvent` or by overriding `createAttributes()`:

```java
public static AttributeSupplier.Builder createAttributes() {
    return Monster.createMonsterAttributes()
        .add(ModAttributes.STARLIGHT_CAPACITY.get(), 50.0);
}
```

---

## Reading Attribute Values

```java
// Get the current computed value (base + all modifiers)
if (player instanceof LivingEntity living) {
    AttributeInstance instance = living.getAttribute(ModAttributes.STARLIGHT_CAPACITY.get());
    if (instance != null) {
        double capacity = instance.getValue();          // with all modifiers
        double base     = instance.getBaseValue();      // base only
    }
}
```

---

## Applying AttributeModifiers

### Transient Modifier (not saved — for effects/equipment)

```java
private static final UUID PERK_MODIFIER_UUID =
    UUID.fromString("a7b3c2d1-1234-5678-abcd-ef0123456789");

public void applyPerkBonus(Player player) {
    AttributeInstance instance = player.getAttribute(ModAttributes.CRYSTAL_EFFICIENCY.get());
    if (instance != null && !instance.hasModifier(PERK_MODIFIER_UUID)) {
        instance.addTransientModifier(new AttributeModifier(
            PERK_MODIFIER_UUID,
            "Astral Perk Bonus",
            0.25,                              // amount
            AttributeModifier.Operation.MULTIPLY_BASE  // operation
        ));
    }
}

public void removePerkBonus(Player player) {
    AttributeInstance instance = player.getAttribute(ModAttributes.CRYSTAL_EFFICIENCY.get());
    if (instance != null) {
        instance.removeModifier(PERK_MODIFIER_UUID);
    }
}
```

### Permanent Modifier (saved to disk)

```java
instance.addPermanentModifier(new AttributeModifier(uuid, name, amount, operation));
```

### Operations

| Operation | Formula | Use |
|---|---|---|
| `ADDITION` | `base + amount` | Flat bonus/penalty |
| `MULTIPLY_BASE` | `base + base * amount` | % of base value |
| `MULTIPLY_TOTAL` | `total * (1 + amount)` | % of final total |

Multiple `MULTIPLY_BASE` modifiers are summed before multiplying: `base * (1 + sum(amounts))`.

---

## UUID Best Practice

Generate UUIDs offline and store as constants. Never generate `UUID.randomUUID()` at runtime for permanent modifiers — they won't match on reload.

```java
// Generate once with: UUID.randomUUID().toString()
private static final UUID PERK_SPEED_UUID    = UUID.fromString("a1b2c3d4-...");
private static final UUID PERK_REACH_UUID    = UUID.fromString("b2c3d4e5-...");
```

---

## Item Attribute Modifiers (Equipment)

Override `getAttributeModifiers` in your Item class to add modifiers when equipped:

```java
@Override
public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
        EquipmentSlot slot, ItemStack stack) {
    ImmutableMultimap.Builder<Attribute, AttributeModifier> map =
        ImmutableMultimap.builder();
    if (slot == EquipmentSlot.MAINHAND) {
        map.put(Attributes.ATTACK_DAMAGE,
            new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,
                "Crystal weapon bonus", 5.0, AttributeModifier.Operation.ADDITION));
    }
    return map.build();
}
```

---

## Lang Keys

```
attribute.astralsorcery.starlight_capacity=Starlight Capacity
attribute.astralsorcery.crystal_efficiency=Crystal Efficiency
attribute.modifier.plus.0=+%s %s
attribute.modifier.plus.1=+%s%% %s
attribute.modifier.plus.2=+%s%% %s
attribute.modifier.take.0=-%s %s
attribute.modifier.take.1=-%s%% %s
attribute.modifier.take.2=-%s%% %s
```
