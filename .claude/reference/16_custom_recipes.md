# Forge 1.20.1 — Custom Recipe Types

Source: https://docs.minecraftforge.net/en/1.20.1/resources/server/recipes/custom/
Forum: https://forums.minecraftforge.net/topic/152204-solved-trying-to-register-a-custom-recipetype-and-recipes-forge-1201/

---

## Overview

Three required pieces:
1. **`RecipeType<T>`** — identifies the category/context of the recipe
2. **`RecipeSerializer<T>`** — encodes/decodes the recipe between JSON and network
3. **`Recipe<C>`** — holds the data and executes the logic

---

## Step 1 — Register RecipeType and RecipeSerializer

```java
public class ModRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(Registries.RECIPE_TYPE, MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

    public static final RegistryObject<RecipeType<AltarRecipe>> ALTAR_TYPE =
        RECIPE_TYPES.register("altar_crafting",
            () -> RecipeType.simple(new ResourceLocation(MODID, "altar_crafting")));

    public static final RegistryObject<RecipeSerializer<AltarRecipe>> ALTAR_SERIALIZER =
        RECIPE_SERIALIZERS.register("altar_crafting",
            () -> new AltarRecipeSerializer());
}
```

---

## Step 2 — Implement Recipe<C>

`C` is the container type your recipe reads from. For non-standard recipes you can use a simple wrapper.

```java
public class AltarRecipe implements Recipe<SimpleContainer> {

    private final ResourceLocation id;
    private final Ingredient input;
    private final ItemStack output;
    private final int starlightCost;

    public AltarRecipe(ResourceLocation id, Ingredient input, ItemStack output, int starlightCost) {
        this.id = id;
        this.input = input;
        this.output = output;
        this.starlightCost = starlightCost;
    }

    @Override
    public boolean matches(SimpleContainer container, Level level) {
        return input.test(container.getItem(0));
    }

    @Override
    public ItemStack assemble(SimpleContainer container, RegistryAccess registryAccess) {
        return output.copy();  // MUST return a copy
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return true; }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) { return output; }

    @Override
    public ResourceLocation getId() { return id; }

    @Override
    public RecipeSerializer<?> getSerializer() { return ModRecipes.ALTAR_SERIALIZER.get(); }

    @Override
    public RecipeType<?> getType() { return ModRecipes.ALTAR_TYPE.get(); }

    // Accessors for your own use
    public Ingredient getInput() { return input; }
    public int getStarlightCost() { return starlightCost; }
}
```

---

## Step 3 — Implement RecipeSerializer<T>

In **Forge 1.20.1**, serializers use `fromJson` / `toNetwork` / `fromNetwork` (not Codec-based yet — that's 1.20.4+):

```java
public class AltarRecipeSerializer implements RecipeSerializer<AltarRecipe> {

    @Override
    public AltarRecipe fromJson(ResourceLocation id, JsonObject json) {
        Ingredient input = Ingredient.fromJson(json.get("input"));
        ItemStack output = CraftingHelper.getItemStack(json.getAsJsonObject("output"), true, true);
        int starlightCost = GsonHelper.getAsInt(json, "starlight_cost", 100);
        return new AltarRecipe(id, input, output, starlightCost);
    }

    @Override
    public AltarRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        Ingredient input = Ingredient.fromNetwork(buf);
        ItemStack output = buf.readItem();
        int starlightCost = buf.readInt();
        return new AltarRecipe(id, input, output, starlightCost);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, AltarRecipe recipe) {
        recipe.getInput().toNetwork(buf);
        buf.writeItem(recipe.getResultItem(null));
        buf.writeInt(recipe.getStarlightCost());
    }
}
```

---

## Step 4 — Recipe JSON

`data/<modid>/recipes/my_altar_recipe.json`:

```json
{
  "type": "astralsorcery:altar_crafting",
  "input": { "item": "minecraft:diamond" },
  "output": { "item": "astralsorcery:rock_crystal", "count": 1 },
  "starlight_cost": 200
}
```

---

## Step 5 — Looking Up Recipes at Runtime

```java
// Get all recipes of your type
List<AltarRecipe> recipes = level.getRecipeManager()
    .getAllRecipesFor(ModRecipes.ALTAR_TYPE.get());

// Find a matching recipe
Optional<AltarRecipe> match = level.getRecipeManager()
    .getRecipeFor(ModRecipes.ALTAR_TYPE.get(), container, level);

// Get by ID
Optional<? extends Recipe<?>> byId = level.getRecipeManager()
    .byKey(new ResourceLocation(MODID, "my_recipe"));
```

---

## Step 6 — Data Generation for Custom Recipes

```java
public class ModRecipeProvider extends RecipeProvider {

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        // Custom recipes need a custom FinishedRecipe implementation
        new AltarFinishedRecipe(
            new ResourceLocation(MODID, "crystal_from_diamond"),
            Ingredient.of(Items.DIAMOND),
            new ItemStack(ModItems.ROCK_CRYSTAL.get()),
            200
        ).save(writer);
    }
}

// FinishedRecipe serializes back to JSON for datagen
public class AltarFinishedRecipe implements FinishedRecipe {
    // implement getId(), serializeRecipeData(), getType(), getSerializer()
}
```

---

## Non-Item Recipes (Starlight Infusion style)

For recipes that don't use a `Container` at all, implement `Recipe<SimpleContainer>` and use `RecipeManager#getAllRecipesFor` to get all recipes of your type, then filter with your own matching logic:

```java
public Optional<StarfieldRecipe> findRecipe(Level level, BlockPos pos, List<ItemStack> inputs) {
    return level.getRecipeManager()
        .getAllRecipesFor(ModRecipes.STARFIELD_TYPE.get())
        .stream()
        .filter(r -> r.matchesInputs(inputs))
        .findFirst();
}
```
