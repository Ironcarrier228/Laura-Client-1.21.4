package laura.module.bots;

import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.core.Category;
import laura.event.TickEvent;
import laura.setting.BooleanSetting;
import laura.setting.MultiModeSetting;
import laura.setting.SliderSetting;
import laura.util.ChatUtil;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@ModuleRegister(name = "AutoPottBot", description = "Автоматически варит и складывает зелья", category = Category.Bots)
public class AutoPottBot extends Module {
    public static volatile boolean running;
    public static volatile String modeName = "—";
    public static volatile int pending;
    public static volatile int brewing;
    public static volatile int idle;
    public static volatile int ready;
    public static volatile int waterBottlesAvailable;
    public static volatile int glassBottles;
    public static volatile int[] ingredientCounts = new int[7];
    public static volatile List<StatusRow> standRows = List.of();
    public static volatile List<String> missingItems = List.of();

    private final BooleanSetting strengthSetting = new BooleanSetting("Зелье силы", true);
    private final BooleanSetting swiftnessSetting = new BooleanSetting("Зелье скорости", false);
    private final BooleanSetting fireResSetting = new BooleanSetting("Зелье огнестойкости", false);
    private final MultiModeSetting brewSetting = new MultiModeSetting("Варить", this.strengthSetting, this.swiftnessSetting, this.fireResSetting);
    private final SliderSetting clickDelay = new SliderSetting("Задержка кликов", 120.0F, 30.0F, 600.0F, 10.0F, false);
    private final SliderSetting standRadius = new SliderSetting("Радиус варок", 4.5F, 2.0F, 6.0F, 0.5F, false);
    private final BooleanSetting fillBottles = new BooleanSetting("Наполнять бутылки", true);
    private final SliderSetting waterBuffer = new SliderSetting("Буфер воды", 12.0F, 3.0F, 24.0F, 1.0F, false);
    private final BooleanSetting depositToChest = new BooleanSetting("Складывать в сундук", true);

    private final Stopwatch clickTimer = new Stopwatch();
    private final Stopwatch openTimer = new Stopwatch();
    private final Map<BlockPos, StandCache> stands = new LinkedHashMap<>();
    private final Map<String, Long> warnings = new HashMap<>();

    private State state = State.SCAN;
    private BlockPos brewingStand;
    private BlockPos chest;
    private BlockPos waterSource;
    private int fillTarget;
    private int fillAttempts;
    private int fillStart;
    private long waterBackoff;

    public AutoPottBot() {
        a(this.brewSetting, this.clickDelay, this.standRadius, this.fillBottles, this.waterBuffer, this.depositToChest);
    }

    @Override
    public void b() {
        this.resetState();
        running = true;
        super.b();
    }

    @Override
    public void c() {
        running = false;
        if (mc.player != null
                && (mc.player.currentScreenHandler instanceof BrewingStandScreenHandler
                || mc.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
            mc.player.closeHandledScreen();
        }

        this.resetState();
        super.c();
    }

    private void resetState() {
        this.state = State.SCAN;
        this.brewingStand = null;
        this.chest = null;
        this.waterSource = null;
        this.waterBackoff = 0L;
        this.fillAttempts = 0;
        this.stands.clear();
        this.warnings.clear();
        this.clickTimer.reset();
        this.openTimer.reset();
        standRows = List.of();
        missingItems = List.of();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        this.refreshStandCache();
        switch (this.state) {
            case SCAN -> this.tickScan();
            case OPENING -> this.tickOpening();
            case SERVICING -> this.tickServicing();
            case CLOSING -> this.tickClosing();
            case FILL_WATER -> this.tickFillWater();
            case DEPOSIT_OPEN -> this.tickDepositOpen();
            case DEPOSIT_MOVE -> this.tickDepositMove();
        }

        this.collectStatus();
    }

    private void refreshStandCache() {
        long now = System.currentTimeMillis();
        int range = (int) Math.ceil(this.standRadius.c() + 4.0);
        BlockPos origin = mc.player.getBlockPos();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (mc.world.getBlockEntity(pos) instanceof BrewingStandBlockEntity) {
                        StandCache cached = this.stands.get(pos);
                        if (cached == null) {
                            this.stands.put(pos.toImmutable(), new StandCache(pos.toImmutable()));
                        } else {
                            cached.seenAt = now;
                        }
                    }
                }
            }
        }

        Iterator<Map.Entry<BlockPos, StandCache>> iterator = this.stands.entrySet().iterator();
        while (iterator.hasNext()) {
            StandCache cached = iterator.next().getValue();
            if (mc.world.getBlockEntity(cached.pos) instanceof BrewingStandBlockEntity) {
                cached.seenAt = now;
            } else if (now - cached.seenAt > 8000L) {
                iterator.remove();
            }
        }
    }

    private void tickScan() {
        if (mc.player.currentScreenHandler instanceof BrewingStandScreenHandler
                || mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            mc.player.closeHandledScreen();
            return;
        }

        if (this.depositToChest.c() && this.freeSlots() <= 3 && this.finishedPotionCount() > 0) {
            BlockPos nearestChest = this.findNearestChest();
            if (nearestChest != null) {
                this.chest = nearestChest;
                this.state = State.DEPOSIT_OPEN;
                this.openTimer.reset();
                this.clickTimer.reset();
                return;
            }
        }

        if (this.fillBottles.c()
                && this.countItem(Items.GLASS_BOTTLE) > 0
                && this.waterBottleCount() < 3
                && System.currentTimeMillis() >= this.waterBackoff) {
            int capacity = this.emptyStandCapacity();
            int currentWater = this.waterBottleCount();
            int desired = Math.min(this.waterBuffer.c().intValue(), Math.max(3, capacity));
            int spareFree = Math.max(0, this.freeSlots() - 5);
            int take = Math.min(desired, currentWater + spareFree);
            if (capacity > 0 && take > currentWater) {
                BlockPos water = this.findNearestWater();
                if (water != null) {
                    if (this.hasBottleInHotbar()) {
                        this.waterSource = water;
                        this.fillTarget = take;
                        this.fillAttempts = 0;
                        this.fillStart = currentWater;
                        this.state = State.FILL_WATER;
                        this.clickTimer.reset();
                        return;
                    }

                    this.warn("Бутылочки в хотбар");
                }
            }
        }

        StandCache candidate = this.findStandToService();
        if (candidate != null) {
            this.brewingStand = candidate.pos;
            this.state = State.OPENING;
            this.openTimer.reset();
            this.clickTimer.reset();
        }
    }

    private StandCache findStandToService() {
        long now = System.currentTimeMillis();
        Vec3d eye = mc.player.getEyePos();
        double radiusSq = this.standRadius.c() * this.standRadius.c();
        boolean canBrew = this.canBrewAnything();
        StandCache best = null;
        int bestScore = -1;
        double bestDistance = Double.MAX_VALUE;

        for (StandCache cached : this.stands.values()) {
            double distance = Vec3d.ofCenter(cached.pos).squaredDistanceTo(eye);
            if (!(distance > radiusSq) && now >= cached.cooldownUntil && (!cached.brewing || now >= cached.readyAt)) {
                int score = this.serviceScore(cached, canBrew);
                if (score > 0 && (score > bestScore || score == bestScore && distance < bestDistance)) {
                    best = cached;
                    bestScore = score;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private int serviceScore(StandCache cached, boolean canBrew) {
        return switch (cached.stage) {
            case UNKNOWN -> 2;
            case EMPTY -> canBrew ? 1 : 0;
            case WATER, AWKWARD, BASE -> 3;
            case FINAL -> 4;
            default -> 0;
        };
    }

    private void tickOpening() {
        if (mc.player.currentScreenHandler instanceof BrewingStandScreenHandler) {
            this.state = State.SERVICING;
            this.clickTimer.reset();
        } else if (this.brewingStand == null || !(mc.world.getBlockEntity(this.brewingStand) instanceof BrewingStandBlockEntity)) {
            this.state = State.SCAN;
        } else if (this.openTimer.elapsed(1600L)) {
            StandCache cached = this.stands.get(this.brewingStand);
            if (cached != null) {
                cached.cooldownUntil = System.currentTimeMillis() + 4500L;
            }

            this.state = State.SCAN;
        } else if (this.clickTimer.elapsed(450L)) {
            this.openScreen(this.brewingStand);
            this.clickTimer.reset();
        }
    }

    private void tickServicing() {
        if (!(mc.player.currentScreenHandler instanceof BrewingStandScreenHandler handler)) {
            this.state = State.SCAN;
            return;
        }

        StandCache cached = this.stands.get(this.brewingStand);
        if (cached == null) {
            this.state = State.CLOSING;
            return;
        }

        if (!this.clickTimer.elapsed(this.clickDelay.c().longValue())) {
            return;
        }

        this.clickTimer.reset();
        BrewResult result = this.serviceStand(handler, cached);
        switch (result) {
            case BREW_STARTED -> {
                long now = System.currentTimeMillis();
                cached.brewing = true;
                cached.brewStartedAt = now;
                cached.readyAt = now + 20000L;
                cached.cooldownUntil = cached.readyAt;
                this.state = State.CLOSING;
            }
            case DONE -> {
                long cooldown = cached.stage == StandStage.EMPTY ? 4000L : 1500L;
                cached.cooldownUntil = System.currentTimeMillis() + cooldown;
                this.state = State.CLOSING;
            }
            default -> {
            }
        }
    }

    private BrewResult serviceStand(BrewingStandScreenHandler handler, StandCache cached) {
        boolean ingredientPresent = !handler.getSlot(3).getStack().isEmpty();
        int stage = this.detectBrewStage(handler, cached);
        boolean valid = stage >= 0;
        cached.hasIngredientPlan = valid;
        cached.bottleState = valid ? Math.min(3, stage + (ingredientPresent ? 1 : 0)) : 0;
        cached.stage = this.stageFrom(stage, ingredientPresent);

        if (ingredientPresent) {
            cached.brewing = true;
            if (cached.readyAt == 0L) {
                cached.brewStartedAt = System.currentTimeMillis();
                cached.readyAt = cached.brewStartedAt + 20000L;
            }

            return BrewResult.DONE;
        }

        cached.brewing = false;
        if (cached.stage == StandStage.OTHER) {
            return BrewResult.DONE;
        }

        if (cached.stage == StandStage.FINAL) {
            if (this.freeSlots() <= 0) {
                return BrewResult.DONE;
            }

            for (int slot = 0; slot < 3; slot++) {
                if (!handler.getSlot(slot).getStack().isEmpty()) {
                    this.quickMove(slot);
                }
            }

            cached.hasIngredientPlan = false;
            cached.bottleState = 0;
            cached.stage = StandStage.EMPTY;
            return BrewResult.CONTINUE;
        }

        if (!valid) {
            Recipe recipe = this.nextRecipe();
            if (recipe == null) {
                return BrewResult.DONE;
            }

            cached.recipe = recipe;
        }

        if (handler.getFuel() <= 0 && handler.getSlot(4).getStack().isEmpty() && this.moveItemTo(Items.BLAZE_POWDER, 4)) {
            return BrewResult.CONTINUE;
        }

        if (this.hasEmptyBottleSlot(handler)) {
            int waterSlot = this.findStack(stack -> this.isPotionOf(stack, Potions.WATER));
            if (waterSlot != -1) {
                this.moveStackTo(waterSlot);
                return BrewResult.CONTINUE;
            }

            if (this.filledBottleSlots(handler) == 0) {
                return BrewResult.DONE;
            }
        }

        Recipe recipe = cached.recipe != null ? cached.recipe : this.nextRecipe();
        if (recipe == null) {
            return BrewResult.DONE;
        }

        cached.recipe = recipe;
        Item ingredient = switch (cached.stage) {
            case WATER -> Items.NETHER_WART;
            case AWKWARD -> recipe.baseIngredient;
            case BASE -> recipe.upgradeIngredient;
            default -> null;
        };
        if (ingredient == null) {
            return BrewResult.DONE;
        }

        if (!this.hasItem(ingredient)) {
            return BrewResult.DONE;
        }

        return this.moveItemTo(ingredient, 3) ? BrewResult.BREW_STARTED : BrewResult.DONE;
    }

    private void tickClosing() {
        mc.player.closeHandledScreen();
        this.brewingStand = null;
        this.state = State.SCAN;
    }

    private void tickFillWater() {
        if (mc.player.currentScreenHandler instanceof BrewingStandScreenHandler
                || mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            mc.player.closeHandledScreen();
            return;
        }

        if (this.waterBottleCount() >= this.fillTarget || this.countItem(Items.GLASS_BOTTLE) <= 0) {
            this.state = State.SCAN;
            return;
        }

        if (this.fillAttempts > this.fillTarget * 2 + 20) {
            if (this.waterBottleCount() <= this.fillStart) {
                this.waterBackoff = System.currentTimeMillis() + 6000L;
                this.warn("Источник воды недосягаем");
            }

            this.state = State.SCAN;
            return;
        }

        if (this.waterSource == null || !this.isWaterSource(this.waterSource)) {
            this.waterSource = this.findNearestWater();
            if (this.waterSource == null) {
                this.state = State.SCAN;
                return;
            }
        }

        if (!this.selectBottleSlot()) {
            this.state = State.SCAN;
            return;
        }

        if (this.clickTimer.elapsed(this.clickDelay.c().longValue())) {
            this.clickTimer.reset();
            this.lookAt(this.waterSource);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            this.fillAttempts++;
        }
    }

    private int emptyStandCapacity() {
        int emptyStands = 0;
        for (StandCache cached : this.stands.values()) {
            if (cached.stage == StandStage.EMPTY || cached.stage == StandStage.UNKNOWN) {
                emptyStands++;
            }
        }

        return emptyStands * 3;
    }

    private BlockPos findNearestWater() {
        BlockPos origin = mc.player.getBlockPos();
        int range = (int) Math.ceil(this.standRadius.c());
        double radiusSq = this.standRadius.c() * this.standRadius.c();
        Vec3d eye = mc.player.getEyePos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (this.isWaterSource(pos)) {
                        double distance = Vec3d.ofCenter(pos).squaredDistanceTo(eye);
                        if (distance <= radiusSq && distance < bestDistance) {
                            bestDistance = distance;
                            best = pos.toImmutable();
                        }
                    }
                }
            }
        }

        return best;
    }

    private boolean isWaterSource(BlockPos pos) {
        FluidState fluid = mc.world.getFluidState(pos);
        return fluid.isStill() && fluid.isIn(FluidTags.WATER);
    }

    private boolean hasBottleInHotbar() {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == Items.GLASS_BOTTLE) {
                return true;
            }
        }

        return false;
    }

    private boolean selectBottleSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == Items.GLASS_BOTTLE) {
                if (mc.player.getInventory().selectedSlot != slot) {
                    mc.player.getInventory().selectedSlot = slot;
                }

                return true;
            }
        }

        return false;
    }

    private void lookAt(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        double dx = pos.getX() + 0.5 - eye.x;
        double dy = pos.getY() + 0.5 - eye.y;
        double dz = pos.getZ() + 0.5 - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        mc.player.setYaw(yaw);
        mc.player.setPitch(Math.max(-90.0F, Math.min(90.0F, pitch)));
    }

    private void warn(String message) {
        long now = System.currentTimeMillis();
        Long last = this.warnings.get(message);
        if (last == null || now - last > 15000L) {
            this.warnings.put(message, now);
            ChatUtil.sendMessage("§8[AutoPottBot] §c" + message);
        }
    }

    private void tickDepositOpen() {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            this.state = State.DEPOSIT_MOVE;
            this.clickTimer.reset();
        } else if (this.chest == null || !(mc.world.getBlockEntity(this.chest) instanceof ChestBlockEntity)) {
            this.state = State.SCAN;
        } else if (this.openTimer.elapsed(1600L)) {
            this.state = State.SCAN;
        } else if (this.clickTimer.elapsed(450L)) {
            this.openScreen(this.chest);
            this.clickTimer.reset();
        }
    }

    private void tickDepositMove() {
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            this.state = State.SCAN;
            return;
        }

        if (!this.clickTimer.elapsed(this.clickDelay.c().longValue())) {
            return;
        }

        this.clickTimer.reset();
        int playerStart = handler.getRows() * 9;
        for (int slot = playerStart; slot < handler.slots.size(); slot++) {
            if (this.isFinishedPotion(handler.slots.get(slot).getStack())) {
                this.quickMove(slot);
                return;
            }
        }

        mc.player.closeHandledScreen();
        this.chest = null;
        this.state = State.SCAN;
    }

    private List<Recipe> selectedRecipes() {
        ArrayList<Recipe> recipes = new ArrayList<>(3);
        if (this.strengthSetting.c()) {
            recipes.add(Recipe.STRENGTH);
        }

        if (this.swiftnessSetting.c()) {
            recipes.add(Recipe.SWIFTNESS);
        }

        if (this.fireResSetting.c()) {
            recipes.add(Recipe.FIRE_RESISTANCE);
        }

        return recipes;
    }

    private Map<Item, Integer> plannedIngredients() {
        HashMap<Item, Integer> planned = new HashMap<>();
        for (StandCache cached : this.stands.values()) {
            if (cached.hasIngredientPlan && cached.recipe != null) {
                if (cached.bottleState < 1) {
                    planned.merge(Items.NETHER_WART, 1, Integer::sum);
                }

                if (cached.bottleState < 2) {
                    planned.merge(cached.recipe.baseIngredient, 1, Integer::sum);
                }

                if (cached.bottleState < 3) {
                    planned.merge(cached.recipe.upgradeIngredient, 1, Integer::sum);
                }
            }
        }

        return planned;
    }

    private List<Recipe> brewableRecipes() {
        Map<Item, Integer> planned = this.plannedIngredients();
        int water = this.waterBottleCount();
        ArrayList<Recipe> brewable = new ArrayList<>(3);
        for (Recipe recipe : this.selectedRecipes()) {
            int wart = this.countItem(Items.NETHER_WART) - planned.getOrDefault(Items.NETHER_WART, 0);
            int base = this.countItem(recipe.baseIngredient) - planned.getOrDefault(recipe.baseIngredient, 0);
            int upgrade = this.countItem(recipe.upgradeIngredient) - planned.getOrDefault(recipe.upgradeIngredient, 0);
            if (water >= 1 && wart >= 1 && base >= 1 && upgrade >= 1) {
                brewable.add(recipe);
            }
        }

        return brewable;
    }

    private boolean canBrewAnything() {
        return !this.brewableRecipes().isEmpty();
    }

    private int recipeCursor = 0;

    private Recipe nextRecipe() {
        List<Recipe> brewable = this.brewableRecipes();
        if (brewable.isEmpty()) {
            return null;
        }

        Recipe recipe = brewable.get(Math.floorMod(this.recipeCursor, brewable.size()));
        this.recipeCursor++;
        return recipe;
    }

    private int detectBrewStage(BrewingStandScreenHandler handler, StandCache cached) {
        RegistryEntry<Potion> first = null;
        int bottles = 0;
        for (int slot = 0; slot < 3; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.getItem() == Items.POTION) {
                bottles++;
                if (first == null) {
                    first = this.potionOf(stack);
                }
            }
        }

        if (bottles == 0 || first == null) {
            return -1;
        }

        if (this.samePotion(first, Potions.WATER)) {
            return 0;
        }

        if (this.samePotion(first, Potions.AWKWARD)) {
            return 1;
        }

        for (Recipe recipe : Recipe.values()) {
            if (this.samePotion(first, recipe.finalPotion)) {
                cached.recipe = recipe;
                return 3;
            }

            if (this.samePotion(first, recipe.basePotion)) {
                cached.recipe = recipe;
                return 2;
            }
        }

        return -2;
    }

    private StandStage stageFrom(int stage, boolean ingredientPresent) {
        return switch (stage) {
            case -1 -> StandStage.EMPTY;
            case 0 -> StandStage.WATER;
            case 1 -> StandStage.AWKWARD;
            case 2 -> StandStage.BASE;
            case 3 -> StandStage.FINAL;
            default -> StandStage.OTHER;
        };
    }

    private boolean hasEmptyBottleSlot(BrewingStandScreenHandler handler) {
        for (int slot = 0; slot < 3; slot++) {
            if (handler.getSlot(slot).getStack().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private int filledBottleSlots(BrewingStandScreenHandler handler) {
        int filled = 0;
        for (int slot = 0; slot < 3; slot++) {
            if (!handler.getSlot(slot).getStack().isEmpty()) {
                filled++;
            }
        }

        return filled;
    }

    private boolean moveItemTo(Item item, int targetSlot) {
        int from = this.findStack(stack -> stack.getItem() == item);
        if (from == -1) {
            return false;
        }

        this.clickSlot(from, 0, SlotActionType.PICKUP);
        this.clickSlot(targetSlot, 1, SlotActionType.PICKUP);
        this.clickSlot(from, 0, SlotActionType.PICKUP);
        return true;
    }

    private void moveStackTo(int fromSlot) {
        this.clickSlot(fromSlot, 0, SlotActionType.PICKUP);
        for (int slot = 0; slot < 3; slot++) {
            ScreenHandler handler = mc.player.currentScreenHandler;
            if (handler.getSlot(slot).getStack().isEmpty()) {
                this.clickSlot(slot, 0, SlotActionType.PICKUP);
                return;
            }
        }

        this.clickSlot(fromSlot, 0, SlotActionType.PICKUP);
    }

    private int findStack(Predicate<ItemStack> predicate) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        for (int slot = 5; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.slots.get(slot).getStack();
            if (!stack.isEmpty() && predicate.test(stack)) {
                return slot;
            }
        }

        return -1;
    }

    private boolean hasItem(Item item) {
        return this.findStack(stack -> stack.getItem() == item) != -1;
    }

    private boolean isPotionOf(ItemStack stack, RegistryEntry<Potion> potion) {
        if (stack.getItem() != Items.POTION) {
            return false;
        }

        RegistryEntry<Potion> actual = this.potionOf(stack);
        return actual != null && this.samePotion(actual, potion);
    }

    private boolean isAnyPotion(ItemStack stack) {
        return stack.getItem() == Items.POTION || stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION;
    }

    private RegistryEntry<Potion> potionOf(ItemStack stack) {
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        return contents != null && !contents.potion().isEmpty() ? contents.potion().get() : null;
    }

    private boolean samePotion(RegistryEntry<Potion> a, RegistryEntry<Potion> b) {
        return a == b || a.getKey().isPresent() && b.getKey().isPresent() && a.getKey().get().equals(b.getKey().get());
    }

    private int freeSlots() {
        int free = 0;
        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) {
                free++;
            }
        }

        return free;
    }

    private boolean isFinishedPotion(ItemStack stack) {
        if (!this.isAnyPotion(stack)) {
            return false;
        }

        RegistryEntry<Potion> potion = this.potionOf(stack);
        return potion != null && !this.samePotion(potion, Potions.WATER) && !this.samePotion(potion, Potions.AWKWARD);
    }

    private int finishedPotionCount() {
        int count = 0;
        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            if (this.isFinishedPotion(mc.player.getInventory().getStack(slot))) {
                count++;
            }
        }

        return count;
    }

    private int countItem(Item item) {
        int count = 0;
        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private int waterBottleCount() {
        int count = 0;
        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() == Items.POTION && this.isPotionOf(stack, Potions.WATER)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private BlockPos findNearestChest() {
        BlockPos origin = mc.player.getBlockPos();
        int range = (int) Math.ceil(this.standRadius.c() + 1.0);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (mc.world.getBlockEntity(pos) instanceof ChestBlockEntity) {
                        double distance = Vec3d.ofCenter(pos).squaredDistanceTo(eye);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = pos.toImmutable();
                        }
                    }
                }
            }
        }

        return best;
    }

    private void openScreen(BlockPos pos) {
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(center, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    private void quickMove(int slot) {
        this.clickSlot(slot, 0, SlotActionType.QUICK_MOVE);
    }

    private void clickSlot(int slot, int button, SlotActionType type) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, button, type, mc.player);
    }

    private void collectStatus() {
        long now = System.currentTimeMillis();
        int brewingCount = 0;
        int idleCount = 0;
        int readyCount = 0;
        ArrayList<StatusRow> rows = new ArrayList<>(this.stands.size());

        for (StandCache cached : this.stands.values()) {
            if (cached.stage == StandStage.FINAL) {
                readyCount++;
            } else if (cached.brewing && now < cached.readyAt) {
                brewingCount++;
            } else if (cached.stage != StandStage.OTHER) {
                idleCount++;
            }

            rows.add(new StatusRow(cached.label(), cached.color(), cached.progress(now), cached.statusText(now)));
        }

        rows.sort((left, right) -> Float.compare(right.progress(), left.progress()));
        ingredientCounts = new int[]{
                this.waterBottleCount(),
                this.countItem(Items.NETHER_WART),
                this.countItem(Items.BLAZE_POWDER),
                this.countItem(Items.GLOWSTONE_DUST),
                this.countItem(Items.SUGAR),
                this.countItem(Items.MAGMA_CREAM),
                this.countItem(Items.REDSTONE)
        };
        glassBottles = this.countItem(Items.GLASS_BOTTLE);
        pending = this.stands.size();
        brewing = brewingCount;
        idle = idleCount;
        ready = readyCount;
        waterBottlesAvailable = this.brewableBottles();
        standRows = rows;
        modeName = this.state.displayName;
        missingItems = this.collectMissing();
        this.reportMissing();
    }

    private int brewableBottles() {
        List<Recipe> selected = this.selectedRecipes();
        if (selected.isEmpty()) {
            return 0;
        }

        Map<Item, Integer> planned = this.plannedIngredients();
        int water = this.waterBottleCount();
        int wart = Math.max(0, this.countItem(Items.NETHER_WART) - planned.getOrDefault(Items.NETHER_WART, 0));
        int total = 0;
        for (Recipe recipe : selected) {
            int base = this.countItem(recipe.baseIngredient) - planned.getOrDefault(recipe.baseIngredient, 0);
            int upgrade = this.countItem(recipe.upgradeIngredient) - planned.getOrDefault(recipe.upgradeIngredient, 0);
            total += Math.max(0, Math.min(base, upgrade));
        }

        total = Math.min(total, wart);
        return Math.max(0, Math.min(water, total * 3));
    }

    private List<String> collectMissing() {
        List<Recipe> selected = this.selectedRecipes();
        if (selected.isEmpty()) {
            return List.of("Не выбрано зелье");
        }

        Map<Item, Integer> planned = this.plannedIngredients();
        ArrayList<String> missing = new ArrayList<>();
        if (this.waterBottleCount() < 1) {
            boolean canFill = this.fillBottles.c() && this.countItem(Items.GLASS_BOTTLE) > 0;
            if (!canFill) {
                missing.add(this.countItem(Items.GLASS_BOTTLE) > 0 ? "Источник воды" : "Вода / Бутылочки");
            }
        }

        if (this.countItem(Items.NETHER_WART) - planned.getOrDefault(Items.NETHER_WART, 0) < 1) {
            missing.add("Адский нарост");
        }

        for (Recipe recipe : selected) {
            if (this.countItem(recipe.baseIngredient) - planned.getOrDefault(recipe.baseIngredient, 0) < 1) {
                addUnique(missing, recipe.baseIngredient.getName().getString());
            }

            if (this.countItem(recipe.upgradeIngredient) - planned.getOrDefault(recipe.upgradeIngredient, 0) < 1) {
                addUnique(missing, recipe.upgradeIngredient.getName().getString());
            }
        }

        if (this.countItem(Items.BLAZE_POWDER) <= 0) {
            addUnique(missing, "Огненный порошок (топливо)");
        }

        return missing;
    }

    private void reportMissing() {
        boolean hasIdleStand = false;
        for (StandCache cached : this.stands.values()) {
            if (cached.stage == StandStage.EMPTY || cached.stage == StandStage.UNKNOWN) {
                hasIdleStand = true;
                break;
            }
        }

        if (hasIdleStand && !missingItems.isEmpty()) {
            long now = System.currentTimeMillis();
            for (String item : missingItems) {
                Long last = this.warnings.get(item);
                if (last == null || now - last > 15000L) {
                    this.warnings.put(item, now);
                    ChatUtil.sendMessage("§8[AutoPottBot] §cНе хватает: §f" + item);
                }
            }
        }
    }

    private static void addUnique(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private static final class Stopwatch {
        private long startedAt = System.currentTimeMillis();

        void reset() {
            this.startedAt = System.currentTimeMillis();
        }

        boolean elapsed(long millis) {
            return System.currentTimeMillis() - this.startedAt >= millis;
        }
    }

    private static final class StandCache {
        final BlockPos pos;
        Recipe recipe;
        StandStage stage = StandStage.UNKNOWN;
        boolean hasIngredientPlan;
        int bottleState;
        boolean brewing;
        long brewStartedAt;
        long readyAt;
        long cooldownUntil;
        long seenAt = System.currentTimeMillis();

        StandCache(BlockPos pos) {
            this.pos = pos;
        }

        float progress(long now) {
            if (this.stage == StandStage.FINAL) {
                return 1.0F;
            }

            if (this.brewing && this.readyAt > this.brewStartedAt) {
                float progress = (float) (now - this.brewStartedAt) / (float) (this.readyAt - this.brewStartedAt);
                return progress < 0.0F ? 0.0F : Math.min(progress, 1.0F);
            }

            return 0.0F;
        }

        int color() {
            if (this.stage == StandStage.FINAL) {
                return 5954680;
            }

            return this.recipe != null ? this.recipe.hudColor : 9868960;
        }

        String label() {
            return this.recipe != null ? this.recipe.displayName : "—";
        }

        String statusText(long now) {
            if (this.stage == StandStage.FINAL) {
                return this.label() + " ✓";
            }

            if (this.brewing && now < this.readyAt) {
                return this.label() + " " + (int) (this.progress(now) * 100.0F) + "%";
            }

            if (this.stage == StandStage.EMPTY) {
                return "Свободна";
            }

            return this.stage == StandStage.UNKNOWN ? "…" : this.label() + " готова";
        }
    }

    public record StatusRow(String name, int color, float progress, String label) {
    }

    private enum StandStage {
        UNKNOWN,
        EMPTY,
        WATER,
        AWKWARD,
        BASE,
        FINAL,
        OTHER
    }

    private enum BrewResult {
        CONTINUE,
        BREW_STARTED,
        DONE
    }

    private enum Recipe {
        STRENGTH("Сила", Items.BLAZE_POWDER, Items.GLOWSTONE_DUST, Potions.STRENGTH, Potions.STRONG_STRENGTH, 14042437),
        SWIFTNESS("Скорость", Items.SUGAR, Items.GLOWSTONE_DUST, Potions.SWIFTNESS, Potions.STRONG_SWIFTNESS, 5227511),
        FIRE_RESISTANCE("Огнестойкость", Items.MAGMA_CREAM, Items.REDSTONE, Potions.FIRE_RESISTANCE, Potions.LONG_FIRE_RESISTANCE, 16750592);

        final String displayName;
        final Item baseIngredient;
        final Item upgradeIngredient;
        final RegistryEntry<Potion> basePotion;
        final RegistryEntry<Potion> finalPotion;
        final int hudColor;

        Recipe(String displayName, Item baseIngredient, Item upgradeIngredient, RegistryEntry<Potion> basePotion, RegistryEntry<Potion> finalPotion, int hudColor) {
            this.displayName = displayName;
            this.baseIngredient = baseIngredient;
            this.upgradeIngredient = upgradeIngredient;
            this.basePotion = basePotion;
            this.finalPotion = finalPotion;
            this.hudColor = hudColor;
        }
    }

    private enum State {
        SCAN("Поиск"),
        OPENING("Открытие"),
        SERVICING("Загрузка"),
        CLOSING("Закрытие"),
        FILL_WATER("Налив воды"),
        DEPOSIT_OPEN("Сундук"),
        DEPOSIT_MOVE("Разгрузка");

        final String displayName;

        State(String displayName) {
            this.displayName = displayName;
        }
    }
}
