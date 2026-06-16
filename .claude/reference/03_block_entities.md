# Forge 1.20.1 — Block Entities (BlockEntity)

Sources:
- https://docs.minecraftforge.net/en/1.20.1/blockentities/
- https://docs.minecraftforge.net/en/1.19.x/blockentities/ber/
- https://mc.lukegrahamlandry.ca/1.19.2/tile-entities/

---

## Registration

```java
public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
    DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

public static final RegistryObject<BlockEntityType<MyBlockEntity>> MY_BE =
    BLOCK_ENTITY_TYPES.register("my_be", () ->
        BlockEntityType.Builder.of(MyBlockEntity::new, MyBlocks.MY_BLOCK.get())
            .build(null));  // null = no DataFixer
```

The `BlockEntityType.Builder.of(factory, validBlocks...)` links the BlockEntity type to one or more blocks.

---

## Attaching to a Block

Implement `EntityBlock` on your Block class:

```java
public class MyBlock extends Block implements EntityBlock {

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MyBlockEntity(pos, state);
    }

    // Optional ticker — only include if you need tick()
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return type == MyBlockEntities.MY_BE.get()
            ? (lvl, pos, st, be) -> MyBlockEntity.tick(lvl, pos, st, be)
            : null;
    }
}
```

---

## BlockEntity Class

```java
public class MyBlockEntity extends BlockEntity {

    private int someValue;

    public MyBlockEntity(BlockPos pos, BlockState state) {
        super(MyBlockEntities.MY_BE.get(), pos, state);
    }

    // Tick (if registered via getTicker above)
    public static void tick(Level level, BlockPos pos, BlockState state, MyBlockEntity be) {
        if (level.isClientSide) return;
        // Do work — keep this lightweight
    }

    // --- Data persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);  // MUST call super — reserves id, x, y, z, ForgeData, ForgeCaps
        tag.putInt("SomeValue", someValue);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);  // MUST call super
        someValue = tag.getInt("SomeValue");
    }

    // Always call this after mutating fields
    public void setSomeValue(int value) {
        this.someValue = value;
        setChanged();  // marks chunk dirty so it gets saved
    }
}
```

**Reserved tag names (never use):** `id`, `x`, `y`, `z`, `ForgeData`, `ForgeCaps`

---

## Network Synchronization

### Option A — On chunk load (initial sync)

```java
@Override
public CompoundTag getUpdateTag() {
    CompoundTag tag = new CompoundTag();
    // only write what the client needs
    tag.putInt("SomeValue", someValue);
    return tag;
}

@Override
public void handleUpdateTag(CompoundTag tag) {
    load(tag);
}
```

### Option B — On block update (push to nearby clients)

```java
@Override
@Nullable
public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
}

@Override
public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
    handleUpdateTag(pkt.getTag());
}
```

Trigger from server:

```java
level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
```

### Option C — Custom networking packet (see `04_networking.md`)

Most efficient for selective / frequent updates.

---

## Capability Exposure (see also `05_capabilities.md`)

```java
private LazyOptional<IItemHandler> itemHandler =
    LazyOptional.of(() -> new ItemStackHandler(9));

@Override
public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.ITEM_HANDLER) {
        return itemHandler.cast();
    }
    return super.getCapability(cap, side);
}

@Override
public void invalidateCaps() {
    super.invalidateCaps();
    itemHandler.invalidate();
}
```

Always invalidate in `invalidateCaps()` to avoid memory leaks.

---

## Performance Notes

- Do NOT perform heavy calculations every tick.
- Gate expensive work: `if (level.getGameTime() % 20 == 0)` for ~1/sec.
- Avoid sending network packets every tick; batch or debounce.
- Use `isClientSide` guard in tick to skip client-side server logic.

---

## BlockEntityRenderer (BER)

Register in `FMLClientSetupEvent`:

```java
@SubscribeEvent
public static void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> {
        BlockEntityRenderers.register(MyBlockEntities.MY_BE.get(), MyBER::new);
    });
}
```

```java
public class MyBER implements BlockEntityRenderer<MyBlockEntity> {

    public MyBER(BlockEntityRendererProvider.Context ctx) { }

    @Override
    public void render(MyBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // draw geometry here using PoseStack / VertexConsumer
    }

    @Override
    public boolean shouldRenderOffScreen(MyBlockEntity be) {
        return true; // render even when block is off-screen (for large custom renderers)
    }
}
```

`render()` is called every frame — keep it cheap or cache expensive computations.
