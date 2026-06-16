# Forge 1.20.1 — GUIs (Menus & Screens)

Sources:
- https://docs.minecraftforge.net/en/1.19.x/gui/menus/
- https://docs.minecraftforge.net/en/1.19.x/gui/screens/
- https://forums.minecraftforge.net/topic/117108-need-help-registering-container-gui-menu-1192/

---

## Menu vs Screen

| Concept | Class | Side | Role |
|---------|-------|------|------|
| **Menu** | `AbstractContainerMenu` | Both | Logic: slot management, data sync, validation |
| **Screen** | `AbstractContainerScreen<T>` | Client only | Rendering + input handling |

Menus run on both sides. Screens are purely client-side views of the menu.

---

## Step 1 — Register MenuType

```java
public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    public static final RegistryObject<MenuType<AltarMenu>> ALTAR_MENU =
        MENU_TYPES.register("altar_menu",
            () -> new MenuType<>(AltarMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
```

The `MenuSupplier` lambda receives `(containerId, playerInventory)`.

---

## Step 2 — AbstractContainerMenu

```java
public class AltarMenu extends AbstractContainerMenu {

    private final AltarBlockEntity blockEntity;

    // Constructor used by factory (client + server)
    public AltarMenu(int containerId, Inventory playerInventory, AltarBlockEntity be) {
        super(ModMenus.ALTAR_MENU.get(), containerId);
        this.blockEntity = be;

        // Add BE slots
        addSlot(new Slot(be.getItemHandler(), 0, 80, 35));

        // Add player inventory slots (standard layout)
        addPlayerInventorySlots(playerInventory, 8, 84);
    }

    // Factory constructor (client side only — no BE reference)
    public AltarMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory,
            getBlockEntity(playerInventory));  // look up from client world
    }

    // Basic validation: player must stay near the block
    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(),
            blockEntity.getBlockPos()), player, MyBlocks.ALTAR.get());
    }

    // Shift-click logic
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // implement quick-move transfer logic
        return ItemStack.EMPTY;
    }

    // Helper to add 3x9 + hotbar player slots
    private void addPlayerInventorySlots(Inventory inv, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, x + col * 18, y + 58));
        }
    }
}
```

---

## Step 3 — AbstractContainerScreen

```java
public class AltarScreen extends AbstractContainerScreen<AltarMenu> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(MODID, "textures/gui/altar.png");

    public AltarScreen(AltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
```

---

## Step 4 — Register Screen (client-side)

```java
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.ALTAR_MENU.get(), AltarScreen::new);
        });
    }
}
```

---

## Opening a Menu from a Block

In your Block class override `useWithoutItem` (or `use` in older versions):

```java
@Override
public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                        Player player, BlockHitResult hit) {
    if (!level.isClientSide) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AltarBlockEntity altar) {
            NetworkHooks.openScreen((ServerPlayer) player,
                altar,           // MenuProvider
                altar.getBlockPos());  // extra data sent to client
        }
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
}
```

Your `BlockEntity` must implement `MenuProvider`:

```java
public class AltarBlockEntity extends BlockEntity implements MenuProvider {

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AltarMenu(id, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container." + MODID + ".altar");
    }
}
```

---

## Data Sync — ContainerData

For lightweight integer data (progress, energy levels) sync via `ContainerData`:

```java
private final ContainerData data = new ContainerData() {
    @Override public int get(int index) {
        return switch (index) {
            case 0 -> craftProgress;
            case 1 -> maxProgress;
            default -> 0;
        };
    }
    @Override public void set(int index, int value) {
        switch (index) {
            case 0 -> craftProgress = value;
            case 1 -> maxProgress = value;
        }
    }
    @Override public int getCount() { return 2; }
};
```

In the menu constructor:

```java
addDataSlots(data);  // syncs integers to client automatically
```

---

## Standalone Screens (no menu)

Extend `Screen` directly for HUDs, info overlays, or custom non-inventory GUIs:

```java
public class ConstellationScreen extends Screen {
    public ConstellationScreen() {
        super(Component.literal("Constellations"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "Title", width / 2, 20, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
```

Open from client code: `Minecraft.getInstance().setScreen(new ConstellationScreen())`
