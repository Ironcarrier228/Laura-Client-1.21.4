package laura.module.bots;

import baritone.api.BaritoneAPI;
import baritone.api.BaritoneSettings;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Laura;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.PacketEvent;
import laura.event.TickEvent;
import laura.module.render.AncientXray;
import laura.setting.BooleanSetting;
import laura.setting.SliderSetting;
import laura.util.ChatUtil;
import laura.util.Rotation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.Perspective;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ModuleRegister(name = "AutoAncientBot", description = "Автоматический фарм древних обломков через ТНТ", category = Category.Bots)
public class AutoAncientBot extends Module {
    private final BooleanSetting logChat = new BooleanSetting("Логи в чат", true);
    private final BooleanSetting pearlsEnabled = new BooleanSetting("Пёрки", true);
    private final SliderSetting pearlMinDistance = new SliderSetting("Мин. дистанция пёрки", 16.0F, 8.0F, 48.0F, 1.0F, false).a(() -> !this.pearlsEnabled.c());
    private final BooleanSetting debug = new BooleanSetting("Debug", false);

    private State state = State.SEARCHING;
    private MiningPhase miningPhase = MiningPhase.APPROACHING;

    private final Stopwatch stateTimer = new Stopwatch();
    private final Stopwatch stuckTimer = new Stopwatch();
    private final Stopwatch breakRetryTimer = new Stopwatch();
    private final Stopwatch pearlCooldown = new Stopwatch();
    private final Stopwatch pearlThrowTimer = new Stopwatch();
    private final Stopwatch pathStuckTimer = new Stopwatch();
    private final Stopwatch approachTimer = new Stopwatch();
    private final Stopwatch reachLostTimer = new Stopwatch();
    private final Stopwatch eatTimer = new Stopwatch();
    private final Stopwatch drinkTimer = new Stopwatch();
    private final Stopwatch oreRetryTimer = new Stopwatch();
    private final Stopwatch pearlPlanTimer = new Stopwatch();
    private final Stopwatch lavaJumpTimer = new Stopwatch();

    private final Set<BlockPos> skippedOres = new HashSet<>();
    private final Set<BlockPos> explosionSites = new HashSet<>();
    private final Map<BlockPos, Boolean> lavaWallCache = new HashMap<>();

    private BlockPos searchTarget;
    private BlockPos siteTarget;
    private BlockPos tntTarget;
    private BlockPos oreTarget;
    private BlockPos miningTarget;
    private BlockPos lavaEscapeTarget;
    private BlockPos lastExplosion;
    private BlockPos walkGoal;

    private boolean explosionExpected;
    private boolean baritonePaused;
    private boolean tntSeen;
    private boolean baritoneSettingsApplied;
    private boolean inLava;
    private boolean eating;
    private boolean drinking;
    private boolean eatPaused;
    private boolean drinkPaused;
    private boolean pearlWarned;

    private List<Block> savedBlocksToAvoid = List.of();
    private List<Item> savedThrowawayItems = List.of();
    private boolean savedAllowPlace;
    private boolean savedAllowBreak;
    private boolean savedAssumeLava;
    private boolean savedWalkWhileBreaking;

    private double searchAngle;
    private int failedSites;
    private int minedOres;
    private int placedTnt;
    private int lostTnt;
    private int thrownPearls;
    private int breakAttempts;
    private int reachFailures;
    private int pearlFailures;
    private long enabledAt;

    private int eatSlot = -1;
    private int eatReturnSlot = -1;
    private int drinkSlot = -1;
    private int drinkReturnSlot = -1;
    private int pearlStateSlot = -1;

    private PearlPhase pearlPhase = PearlPhase.IDLE;
    private Vec3d pearlTarget;
    private Vec3d pearlThrowFrom;
    private String pearlReason;
    private float pearlYaw;
    private float pearlPitch;
    private Vec3d lastFailedPearl;

    public AutoAncientBot() {
        a(this.logChat, this.pearlsEnabled, this.pearlMinDistance, this.debug);
    }

    @Override
    public void b() {
        if (mc.player == null || mc.world == null) {
            a(false);
            return;
        }

        if (Laura.getInstance().getModuleProcessor().t().B().m()) {
            ChatUtil.sendMessage("[AutoAncient] Disable Aura first.");
            a(false);
            return;
        }

        if (hotbarSlot(Blocks.TNT.asItem()) == -1 || hotbarSlot(Items.FLINT_AND_STEEL) == -1) {
            ChatUtil.sendMessage("[AutoAncient] TNT and flint must be in hotbar.");
            a(false);
            return;
        }

        super.b();
        if (mc.options != null) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }

        AncientXray xray = xray();
        if (xray != null) {
            xray.clearState();
        }

        applyBaritoneSettings();
        this.skippedOres.clear();
        this.explosionSites.clear();
        this.lavaWallCache.clear();
        this.lavaEscapeTarget = null;
        this.miningTarget = null;
        this.walkGoal = null;
        this.searchTarget = null;
        this.siteTarget = null;
        this.tntTarget = null;
        this.oreTarget = null;
        this.lastExplosion = null;
        this.explosionExpected = false;
        this.failedSites = 0;
        this.state = State.SEARCHING;
        this.miningPhase = MiningPhase.APPROACHING;
        this.pearlPhase = PearlPhase.IDLE;
        this.pearlTarget = null;
        this.pearlThrowFrom = null;
        this.pearlReason = null;
        this.lastFailedPearl = null;
        this.pearlFailures = 0;
        this.breakAttempts = 0;
        this.reachFailures = 0;
        this.eating = false;
        this.drinking = false;
        this.inLava = false;
        this.stateTimer.reset();
        this.stuckTimer.reset();
        this.breakRetryTimer.reset();
        this.pearlCooldown.reset();
        this.pearlThrowTimer.reset();
        this.oreRetryTimer.reset();
        this.pearlPlanTimer.reset();
        this.lavaJumpTimer.reset();
        this.searchAngle = Math.toRadians(mc.player.getYaw()) + (Math.PI / 2);
        this.setState(State.SEARCHING);
        logMain("Запущен. ТНТ: " + countItem(Blocks.TNT.asItem()) + ", пёрок: " + countItem(Items.ENDER_PEARL));
        if (this.pearlsEnabled.c() && hotbarSlot(Items.ENDER_PEARL) == -1) {
            logMain("Пёрок в хотбаре нет — броски работать не будут.");
        }

        logDebug("enabled");
    }

    @Override
    public void c() {
        Laura.getInstance().getModuleProcessor().k().reset();
        super.c();
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        stopPathing();
        releaseKeys();
        baritone.getMineProcess().cancel();
        baritone.getBuilderProcess().onLostControl();
        stopPathing();
        baritone.getSelectionManager().removeAllSelections();
        this.walkGoal = null;
        if (this.baritonePaused) {
            baritone.getCommandManager().execute("resume");
            this.baritonePaused = false;
        }

        restoreBaritoneSettings();
        if (this.enabledAt > 0L) {
            long seconds = Math.max(1L, (System.currentTimeMillis() - this.enabledAt) / 1000L);
            logMain("Итог: обломков " + this.minedOres
                    + ", ТНТ " + this.placedTnt
                    + " (съедено " + this.lostTnt
                    + "), пёрок " + this.thrownPearls
                    + ", время " + seconds / 60L + " мин " + seconds % 60L + " с");
            this.enabledAt = 0L;
        }

        logDebug("disabled");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        AncientXray xray = xray();
        if (xray == null) {
            ChatUtil.sendMessage("[AutoAncient] AncientXray module is not registered.");
            a(false);
            return;
        }

        if (!xray.m()) {
            xray.refresh();
        }

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (isPearlInFlight(baritone)) {
            return;
        }

        if (drinkFireResistance(baritone)) {
            return;
        }

        if (escapeLava(baritone)) {
            return;
        }

        if (eatFood(baritone)) {
            return;
        }

        if (this.state != State.MINING
                && this.state != State.PLACING_TNT
                && this.state != State.IGNITING_TNT
                && this.state != State.WAITING_EXPLOSION) {
            BlockPos nearestOre = nearestOre(xray);
            if (nearestOre != null) {
                stopPathing();
                startMining(nearestOre);
                return;
            }
        }

        switch (this.state) {
            case SEARCHING -> tickSearching(baritone);
            case MOVING_SEARCH -> tickMovingSearch(baritone);
            case MOVING_SITE -> tickMovingSite(baritone);
            case CLEARING_SITE -> tickClearingSite(baritone);
            case PLACING_TNT -> tickPlacingTnt(baritone);
            case IGNITING_TNT -> tickIgnitingTnt(baritone);
            case WAITING_EXPLOSION -> tickWaitingExplosion();
            case WAITING_SCAN -> tickWaitingScan(xray, baritone);
            case MINING -> tickMining(baritone, xray);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.world == null || event.isSend()) {
            return;
        }

        AncientXray xray = xray();
        if (xray == null) {
            return;
        }

        if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
            BlockPos center = BlockPos.ofFloored(explosion.center());
            if (isMyExplosion(center)) {
                this.explosionExpected = true;
                this.lastExplosion = center;
                this.skippedOres.clear();
                this.lavaWallCache.clear();
                this.explosionSites.add(center.toImmutable());
                this.pearlPlanned = false;
                if (!xray.m()) {
                    xray.scheduleScan(center, 10);
                }

                if (this.state == State.WAITING_EXPLOSION) {
                    logMain("Взрыв! Сканирую обломки...");
                }

                if (this.state == State.WAITING_EXPLOSION || this.state == State.WAITING_SCAN) {
                    this.setState(State.WAITING_SCAN);
                }
            }
        } else if (event.getPacket() instanceof BlockUpdateS2CPacket update) {
            if (this.tntTarget != null && update.getPos().equals(this.tntTarget) && update.getState().isOf(Blocks.TNT)) {
                this.tntSeen = true;
            }

            if (!xray.m()) {
                xray.onBlockUpdate(update.getPos(), update.getState().getBlock());
            }
        } else if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket delta) {
            delta.visitUpdates((pos, blockState) -> {
                if (this.tntTarget != null && pos.equals(this.tntTarget) && blockState.isOf(Blocks.TNT)) {
                    this.tntSeen = true;
                }

                if (!xray.m()) {
                    xray.onBlockUpdate(pos, blockState.getBlock());
                }
            });
        }
    }

    private boolean pearlPlanned;

    private void tickSearching(IBaritone baritone) {
        releaseKeys();
        BlockPos site = findExplosionSite();
        if (site != null) {
            this.siteTarget = site;
            walkTo(baritone, site, 2);
            this.setState(State.MOVING_SITE);
            logMain("Место для взрыва: " + site.toShortString());
        } else {
            pickSearchTarget(baritone);
        }
    }

    private void tickMovingSearch(IBaritone baritone) {
        if (throwPearlToOre(this.searchTarget, "лечу к зоне поиска")) {
            return;
        }

        if (isStuck(baritone)) {
            rebuildPath(baritone);
        } else if (this.searchTarget == null
                || within(this.searchTarget, 9.0)
                || this.stateTimer.elapsed(18000L)
                || !isPathing(baritone) && this.stateTimer.elapsed(2500L)) {
            stopPathing();
            this.setState(State.SEARCHING);
        }
    }

    private void tickMovingSite(IBaritone baritone) {
        if (this.siteTarget == null) {
            this.setState(State.SEARCHING);
        } else if (!throwPearlToOre(this.siteTarget, "лечу к месту взрыва")) {
            if (isStuck(baritone)) {
                rebuildPath(baritone);
            } else if (this.stateTimer.elapsed(22000L)) {
                this.failedSites++;
                this.setState(State.SEARCHING);
            } else if (within(this.siteTarget, 10.0) || !isPathing(baritone) && this.stateTimer.elapsed(2500L)) {
                stopPathing();
                if (!isSiteSuitable(this.siteTarget)) {
                    this.failedSites++;
                    this.setState(State.SEARCHING);
                } else {
                    this.tntTarget = pickTntSpot(this.siteTarget);
                    if (this.tntTarget == null) {
                        this.failedSites++;
                        this.setState(State.SEARCHING);
                    } else {
                        this.lostTntReset();
                        this.tntSeen = false;
                        this.miningTarget = null;
                        this.setState(State.CLEARING_SITE);
                        logMain("Расчищаю площадку: " + this.tntTarget.toShortString());
                    }
                }
            }
        }
    }

    private void lostTntReset() {
        this.lostTntCounter = 0;
    }

    private int lostTntCounter;

    private void tickClearingSite(IBaritone baritone) {
        if (this.tntTarget == null) {
            this.setState(State.SEARCHING);
        } else if (isAreaClear(this.tntTarget)) {
            stopPathing();
            this.miningTarget = null;
            this.setState(State.PLACING_TNT);
        } else if (this.stateTimer.elapsed(14000L)) {
            this.failedSites++;
            stopPathing();
            this.setState(State.SEARCHING);
        } else {
            BlockPos support = findSupportBlock(this.tntTarget);
            if (support == null) {
                this.failedSites++;
                stopPathing();
                this.setState(State.SEARCHING);
            } else {
                BlockHitResult hit = raycastTo(support, 4.2);
                if (hit == null) {
                    if (isStuck(baritone)) {
                        rebuildPath(baritone);
                    } else {
                        approachForBreaking(baritone, this.tntTarget);
                    }
                } else {
                    BreakMode mode = breakBlock(hit.getBlockPos(), 3000L);
                    if (mode == BreakMode.STUCK) {
                        this.failedSites++;
                        this.setState(State.SEARCHING);
                    } else if (mode == BreakMode.NO_REACH) {
                        approachForBreaking(baritone, this.tntTarget);
                    }
                }
            }
        }
    }

    private BlockPos findSupportBlock(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) {
            return pos;
        }

        return !mc.world.getBlockState(pos.up()).isReplaceable() ? pos.up() : null;
    }

    private void tickPlacingTnt(IBaritone baritone) {
        if (this.tntTarget == null) {
            this.setState(State.SEARCHING);
        } else if (mc.world.getBlockState(this.tntTarget).isOf(Blocks.TNT)) {
            if (this.tntSeen) {
                if (this.stuckTimer.elapsed(1400L)) {
                    this.setState(State.IGNITING_TNT);
                }
            } else if (this.stuckTimer.elapsed(2500L)) {
                this.lostTnt++;
                this.lostTntCounter++;
                logMain("Сервер съел ТНТ — переставляю (#" + this.lostTntCounter + ")");
                pingBlocks(this.tntTarget, 0);
                if (this.lostTntCounter >= 3) {
                    logMain("ТНТ пропадает на этом месте — ищу другое");
                    this.failedSites++;
                    this.setState(State.SEARCHING);
                    return;
                }

                this.stateTimer.reset();
                this.stuckTimer.reset();
            }
        } else if (!within(this.tntTarget, 9.0)) {
            if (isStuck(baritone)) {
                rebuildPath(baritone);
            } else {
                approachForBreaking(baritone, this.tntTarget);
            }
        } else {
            stopPathing();
            if (!isAreaClear(this.tntTarget)) {
                this.failedSites++;
                this.setState(State.SEARCHING);
            } else {
                Vec3d lookPoint = new Vec3d(this.tntTarget.getX() + 0.5, this.tntTarget.getY(), this.tntTarget.getZ() + 0.5);
                Rotation target = Rotation.a(mc.player.getEyePos(), lookPoint);
                aimAt(target);
                if (!(new Rotation(mc.player).a(target) > 4.0F)) {
                    if (this.stuckTimer.elapsed(900L)) {
                        if (!placeTnt(this.tntTarget)) {
                            if (switchToOreIfAny()) {
                                return;
                            }

                            a(false);
                            return;
                        }

                        this.stuckTimer.reset();
                    }

                    if (this.stateTimer.elapsed(8000L)) {
                        this.failedSites++;
                        this.setState(State.SEARCHING);
                    }
                }
            }
        }
    }

    private boolean switchToOreIfAny() {
        AncientXray xray = xray();
        if (xray == null) {
            return false;
        }

        BlockPos nearestOre = nearestOre(xray);
        if (nearestOre == null) {
            return false;
        }

        startMining(nearestOre);
        return true;
    }

    private void tickIgnitingTnt(IBaritone baritone) {
        if (this.tntTarget == null) {
            this.setState(State.SEARCHING);
        } else if (!mc.world.getBlockState(this.tntTarget).isOf(Blocks.TNT)) {
            if (this.stateTimer.elapsed(600L)) {
                stopPathing();
                this.explosionExpected = false;
                this.setState(State.WAITING_EXPLOSION);
            }
        } else if (!within(this.tntTarget, 9.0)) {
            if (isStuck(baritone)) {
                rebuildPath(baritone);
            } else {
                approachForBreaking(baritone, this.tntTarget);
            }
        } else {
            stopPathing();
            BlockHitResult hit = raycastProbe(this.tntTarget);
            if (hit != null) {
                Rotation target = Rotation.a(mc.player.getEyePos(), hit.getPos());
                aimAt(target);
                if (!(new Rotation(mc.player).a(target) > 4.0F)) {
                    if (this.stateTimer.elapsed(1400L)) {
                        if (this.stuckTimer.elapsed(900L)) {
                            if (!igniteTnt(this.tntTarget)) {
                                this.failedSites++;
                                this.setState(State.SEARCHING);
                                return;
                            }

                            this.explosionExpected = false;
                            this.placedTnt++;
                            stopPathing();
                            this.setState(State.WAITING_EXPLOSION);
                            logMain("Поджёг ТНТ #" + this.placedTnt + " (" + this.tntTarget.toShortString() + ")");
                        }
                    }
                }
            }
        }
    }

    private void tickWaitingExplosion() {
        if (!this.explosionExpected
                && this.tntTarget != null
                && mc.world.getBlockState(this.tntTarget).isOf(Blocks.TNT)
                && this.stateTimer.elapsed(1800L)) {
            this.setState(State.IGNITING_TNT);
        } else if (this.explosionExpected || this.stateTimer.elapsed(6500L)) {
            this.setState(State.WAITING_SCAN);
        }
    }

    private void tickWaitingScan(AncientXray xray, IBaritone baritone) {
        if (!this.stateTimer.elapsed(1200L)) {
            return;
        }

        BlockPos nearestOre = nearestOre(xray);
        if (nearestOre != null) {
            startMining(nearestOre);
        } else if (hotbarSlot(Blocks.TNT.asItem()) == -1 && this.lastExplosion != null && !this.pearlPlanned) {
            xray.scheduleScan(this.lastExplosion, 2);
            this.pearlPlanned = true;
            logMain("ТНТ закончилась — финальный скан вокруг взрыва");
            this.stateTimer.reset();
        } else if (this.stateTimer.elapsed(4500L)) {
            logMain("Обломков рядом нет — ищу новое место");
            this.lavaEscapeTarget = null;
            this.miningTarget = null;
            this.miningPhase = MiningPhase.APPROACHING;
            this.setState(State.SEARCHING);
            pickSearchTarget(BaritoneAPI.getProvider().getPrimaryBaritone());
        }
    }

    private void tickMining(IBaritone baritone, AncientXray xray) {
        if (this.miningPhase != MiningPhase.BREAKING) {
            releaseKeys();
        }

        List<BlockPos> ores = usableOres(xray);
        if (ores.isEmpty()) {
            cancelMining(baritone);
            this.setState(State.SEARCHING);
            pickSearchTarget(baritone);
        } else if (this.oreTarget != null && !ores.contains(this.oreTarget)) {
            countMinedIfDone();
            resetMining(xray);
        } else if (this.oreTarget != null && !mc.world.getBlockState(this.oreTarget).isOf(Blocks.ANCIENT_DEBRIS)) {
            countMinedIfDone();
            resetMining(xray);
        } else {
            BlockPos target = this.oreTarget == null ? nearestOf(ores) : this.oreTarget;
            if (this.oreTarget != null && this.oreTarget.equals(target)) {
                if (this.miningPhase == MiningPhase.APPROACHING) {
                    approachOre(baritone, xray);
                } else if (this.miningPhase == MiningPhase.BREAKING) {
                    breakOre(baritone, xray);
                }
            } else {
                if (this.oreTarget == null) {
                    this.breakAttempts = 0;
                }

                selectOre(baritone, target, true);
            }
        }
    }

    private void selectOre(IBaritone baritone, BlockPos target, boolean announce) {
        this.oreTarget = target.toImmutable();
        releaseKeys();
        stopPathing();
        this.miningPhase = MiningPhase.APPROACHING;
        this.breakAttempts = 0;
        this.reachFailures = 0;
        this.miningTarget = null;
        this.approachTimer.reset();
        this.reachLostTimer.reset();
        if (announce) {
            this.stateTimer.reset();
        }

        logDebug("target ore " + this.oreTarget.toShortString());
    }

    private void cancelMining(IBaritone baritone) {
        baritone.getMineProcess().cancel();
        baritone.getBuilderProcess().onLostControl();
        stopPathing();
        baritone.getSelectionManager().removeAllSelections();
        this.oreTarget = null;
        this.miningTarget = null;
        this.miningPhase = MiningPhase.APPROACHING;
        releaseKeys();
    }

    private List<BlockPos> usableOres(AncientXray xray) {
        ArrayList<BlockPos> ores = new ArrayList<>(xray.getFoundPositions());
        ores.removeIf(pos -> {
            boolean gone = !mc.world.getBlockState(pos).isOf(Blocks.ANCIENT_DEBRIS);
            if (gone) {
                xray.removePosition(pos);
            }

            if (!gone && !this.skippedOres.contains(pos) && isWalledInLava(pos)) {
                this.skippedOres.add(pos.toImmutable());
                xray.removePosition(pos);
                logMain("Пропускаю обломок " + pos.toShortString() + " — замурован в лаве, не подойти");
                return true;
            }

            return gone || this.skippedOres.contains(pos);
        });
        return ores;
    }

    private BlockPos nearestOf(List<BlockPos> positions) {
        return positions.stream().min(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)))).orElse(positions.get(0));
    }

    private void approachOre(IBaritone baritone, AncientXray xray) {
        releaseKeys();
        if (canReach(this.oreTarget, 3.7)) {
            beginBreaking();
        } else if (this.approachTimer.elapsed(22000L)) {
            skipOre(xray, "не смог дойти");
        } else if (!throwPearlToOre(this.oreTarget, "лечу к обломку")) {
            walkTo(baritone, this.oreTarget, 2);
        }
    }

    private void breakOre(IBaritone baritone, AncientXray xray) {
        BlockHitResult hit = raycastTo(this.oreTarget, 4.2);
        if (hit == null) {
            this.miningTarget = null;
            if (this.reachLostTimer.elapsed(900L)) {
                this.reachFailures++;
                if (this.reachFailures > 4) {
                    skipOre(xray, "не удержаться рядом (лава/обрыв)");
                    return;
                }

                retryApproach(baritone);
                logDebug("lost reach " + this.oreTarget.toShortString());
            }
        } else {
            this.reachLostTimer.reset();
            BlockPos hitPos = hit.getBlockPos();
            if (!hitPos.equals(this.miningTarget)) {
                this.miningTarget = hitPos.toImmutable();
                this.reachFailures = 0;
            }

            boolean isOreItself = hitPos.equals(this.oreTarget);
            BreakMode mode = breakBlock(hitPos, isOreItself ? 9000L : 3000L);
            if (mode != BreakMode.STUCK) {
                if (this.stateTimer.elapsed(18000L)) {
                    if (canRetryOre(baritone)) {
                        this.breakAttempts++;
                        logDebug("retry ore " + this.oreTarget.toShortString() + " #" + this.breakAttempts);
                        retryApproach(baritone);
                        this.stateTimer.reset();
                        return;
                    }

                    skipOre(xray, "не выкопался за таймаут");
                }
            } else {
                this.breakAttempts++;
                if (isOreItself || this.breakAttempts > 2) {
                    skipOre(xray, "фантомные блоки, ресинк");
                }
            }
        }
    }

    private void retryApproach(IBaritone baritone) {
        releaseKeys();
        this.reachLostTimer.reset();
        this.miningPhase = MiningPhase.APPROACHING;
        this.approachTimer.reset();
        walkTo(baritone, this.oreTarget, 2);
    }

    private void beginBreaking() {
        stopPathing();
        this.miningPhase = MiningPhase.BREAKING;
        this.breakAttempts = 0;
        this.miningTarget = null;
        this.stateTimer.reset();
        this.reachLostTimer.reset();
        logDebug("break ore " + this.oreTarget.toShortString());
    }

    private void startMining(BlockPos pos) {
        this.oreTarget = pos.toImmutable();
        this.breakAttempts = 0;
        this.miningPhase = MiningPhase.APPROACHING;
        this.setState(State.MINING);
        selectOre(BaritoneAPI.getProvider().getPrimaryBaritone(), this.oreTarget, true);
        AncientXray xray = xray();
        int queued = xray == null ? 0 : usableOres(xray).size();
        logMain("Иду к обломку " + this.oreTarget.toShortString() + (queued > 1 ? " (в очереди: " + queued + ")" : ""));
    }

    private boolean canRetryOre(IBaritone baritone) {
        if (this.oreTarget != null && this.breakAttempts < 2) {
            if (!mc.world.getBlockState(this.oreTarget).isOf(Blocks.ANCIENT_DEBRIS)) {
                return false;
            }

            double distance = mc.player.squaredDistanceTo(Vec3d.ofCenter(this.oreTarget));
            return baritone.getPathingBehavior().isPathing() || distance <= 144.0 || hasAdjacentSolid(this.oreTarget);
        }

        return false;
    }

    private void countMinedIfDone() {
        if (this.oreTarget != null && this.miningPhase == MiningPhase.BREAKING) {
            if (!mc.world.getBlockState(this.oreTarget).isOf(Blocks.ANCIENT_DEBRIS)) {
                this.minedOres++;
                logMain("Обломок добыт (всего: " + this.minedOres + ")");
            }
        }
    }

    private void resetMining(AncientXray xray) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getBuilderProcess().onLostControl();
        stopPathing();
        baritone.getSelectionManager().removeAllSelections();
        xray.removePosition(this.oreTarget);
        this.oreTarget = null;
        this.miningTarget = null;
        this.miningPhase = MiningPhase.APPROACHING;
        releaseKeys();
    }

    private void skipOre(AncientXray xray, String reason) {
        if (this.oreTarget != null) {
            this.skippedOres.add(this.oreTarget.toImmutable());
            xray.removePosition(this.oreTarget);
            logMain("Пропускаю обломок " + this.oreTarget.toShortString() + " — " + reason);
        }

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getMineProcess().cancel();
        baritone.getBuilderProcess().onLostControl();
        stopPathing();
        baritone.getSelectionManager().removeAllSelections();
        this.oreTarget = null;
        this.miningTarget = null;
        this.miningPhase = MiningPhase.APPROACHING;
        releaseKeys();
    }

    private BlockPos nearestOre(AncientXray xray) {
        return usableOres(xray).stream().min(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)))).orElse(null);
    }

    private void pickSearchTarget(IBaritone baritone) {
        BlockPos origin = mc.player.getBlockPos();
        if (this.failedSites > 0 && this.failedSites % 4 == 0) {
            this.searchAngle += Math.PI / 2;
        }

        int radius = 36;
        int x = origin.getX() + (int) Math.round(Math.cos(this.searchAngle) * radius);
        int z = origin.getZ() + (int) Math.round(Math.sin(this.searchAngle) * radius);
        this.searchTarget = new BlockPos(x, clampY(origin.getY()), z);
        walkTo(baritone, this.searchTarget, 1);
        this.failedSites++;
        this.setState(State.MOVING_SEARCH);
        logDebug("search " + this.searchTarget.toShortString());
    }

    private BlockPos findExplosionSite() {
        BlockPos origin = mc.player.getBlockPos();
        int range = 26;
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int x = -range; x <= range; x += 4) {
            for (int z = -range; z <= range; z += 4) {
                for (int y = -6; y <= 6; y += 2) {
                    BlockPos pos = new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    int score = siteScore(pos);
                    if (score != Integer.MIN_VALUE && (best == null || score > bestScore)) {
                        best = pos;
                        bestScore = score;
                    }
                }
            }
        }

        return best;
    }

    private int siteScore(BlockPos pos) {
        if (nearExplosion(pos)) {
            return Integer.MIN_VALUE;
        }

        if (!isSolidNonReplaceable(pos.down())) {
            return Integer.MIN_VALUE;
        }

        int total = 0;
        int solid = 0;
        int air = 0;
        int lava = 0;
        for (int x = -5; x <= 5; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos check = pos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(check);
                    Block block = state.getBlock();
                    total++;
                    if (isAirLike(block)) {
                        air++;
                    } else if (block == Blocks.LAVA) {
                        lava++;
                    } else if (isNetherRock(block)) {
                        solid++;
                    }
                }
            }
        }

        if (air < total * 0.48 && !(solid > total * 0.34) && !(lava > total * 0.2)) {
            double distance = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
            return air * 3 - solid * 4 - lava * 5 - (int) (distance * 0.02) + heightBonus(pos.getY());
        }

        return Integer.MIN_VALUE;
    }

    private boolean isSiteSuitable(BlockPos pos) {
        return siteScore(pos) != Integer.MIN_VALUE;
    }

    private boolean nearExplosion(BlockPos pos) {
        double maxSq = 842.4;
        for (BlockPos explosion : this.explosionSites) {
            if (distanceSqXZ3D(pos, explosion) <= maxSq) {
                return true;
            }
        }

        return false;
    }

    private BlockPos pickTntSpot(BlockPos site) {
        BlockPos fallback = null;
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = site.add(x, y, z);
                    if (canStandOn(pos)) {
                        if (isPlaceableAt(pos)) {
                            return pos.toImmutable();
                        }

                        if (fallback == null) {
                            fallback = pos.toImmutable();
                        }
                    }
                }
            }
        }

        if (fallback != null) {
            return fallback;
        }

        return canStandOn(site) ? site.toImmutable() : null;
    }

    private boolean canStandOn(BlockPos pos) {
        BlockState below = mc.world.getBlockState(pos.down());
        BlockState at = mc.world.getBlockState(pos);
        BlockState above = mc.world.getBlockState(pos.up());
        return isSolidNonReplaceable(pos.down()) && at.getFluidState().isEmpty() && above.getFluidState().isEmpty();
    }

    private boolean isPlaceableAt(BlockPos pos) {
        return isSolidNonReplaceable(pos.down())
                && mc.world.getBlockState(pos).isReplaceable()
                && mc.world.getBlockState(pos.up()).isReplaceable();
    }

    private boolean isAreaClear(BlockPos pos) {
        return isSolidNonReplaceable(pos.down())
                && mc.world.getBlockState(pos).isReplaceable()
                && mc.world.getBlockState(pos.up()).isReplaceable();
    }

    private boolean placeTnt(BlockPos pos) {
        int slot = hotbarSlot(Blocks.TNT.asItem());
        if (slot == -1) {
            ChatUtil.sendMessage("[AutoAncient] TNT is missing from hotbar.");
            return false;
        }

        selectSlot(slot);
        this.tntSeen = false;
        BlockPos below = pos.down();
        BlockHitResult hit = new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), Direction.UP, below, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private boolean igniteTnt(BlockPos pos) {
        int slot = hotbarSlot(Items.FLINT_AND_STEEL);
        if (slot == -1) {
            ChatUtil.sendMessage("[AutoAncient] Flint and steel is missing from hotbar.");
            a(false);
            return false;
        }

        BlockHitResult hit = raycastProbe(pos);
        if (hit == null) {
            return false;
        }

        selectSlot(slot);
        BlockHitResult aimed = currentAimHit();
        BlockHitResult use = aimed != null && aimed.getBlockPos().equals(pos) ? aimed : hit;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, use);
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private BreakMode breakBlock(BlockPos pos, long stuckTimeout) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return BreakMode.NO_REACH;
        }

        BlockHitResult hit = raycastProbe(pos);
        if (hit != null && !(mc.player.getEyePos().distanceTo(hit.getPos()) > 4.2)) {
            stopPathing();
            Rotation target = Rotation.a(mc.player.getEyePos(), hit.getPos());
            aimAt(target);
            if (new Rotation(mc.player).a(target) > 4.0F) {
                return BreakMode.AIMING;
            }

            BlockHitResult aimed = currentAimHit();
            BlockHitResult use = aimed != null && aimed.getBlockPos().equals(pos) ? aimed : hit;
            if (!pos.equals(this.miningTarget)) {
                if (!this.breakRetryTimer.elapsed(90L)) {
                    return BreakMode.AIMING;
                }

                mc.interactionManager.attackBlock(pos, use.getSide());
                this.miningTarget = pos.toImmutable();
                this.stuckTimer.reset();
                this.breakRetryTimer.reset();
            } else {
                if (this.stuckTimer.elapsed(stuckTimeout)) {
                    abortBreaking(pos);
                    this.miningTarget = null;
                    return BreakMode.STUCK;
                }

                mc.interactionManager.updateBlockBreakingProgress(pos, use.getSide());
            }

            mc.player.swingHand(Hand.MAIN_HAND);
            return BreakMode.BREAKING;
        }

        return BreakMode.NO_REACH;
    }

    private void abortBreaking(BlockPos pos) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN));
        }
    }

    private void stopPathing() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone.getPathingBehavior().isPathing() || baritone.getCustomGoalProcess().isActive()) {
            baritone.getPathingBehavior().cancelEverything();
        }

        this.walkGoal = null;
    }

    private boolean isPathing(IBaritone baritone) {
        return baritone.getPathingBehavior().isPathing() || baritone.getCustomGoalProcess().isActive();
    }

    private void walkTo(IBaritone baritone, BlockPos target, int radius) {
        BlockPos immutable = target.toImmutable();
        boolean changed = !immutable.equals(this.walkGoal);
        if (changed || !baritone.getCustomGoalProcess().isActive() && this.pathStuckTimer.elapsed(600L)) {
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(immutable, radius));
            this.walkGoal = immutable;
            this.pathStuckTimer.reset();
            if (changed) {
                resetStuckCheck();
                logDebug("walk " + immutable.toShortString());
            }
        }
    }

    private void aimAt(Rotation target) {
        float delta = (float) new Rotation(mc.player).a(target);
        float speed = Math.max(34.0F, Math.min(140.0F, delta * 1.35F));
        Laura.getInstance().getModuleProcessor().k().startAiming(target, speed, 1, 2);
    }

    private BlockHitResult currentAimHit() {
        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        double horizontal = Math.cos(pitch);
        Vec3d direction = new Vec3d(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal);
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(direction.multiply(4.6));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private BlockHitResult raycastTo(BlockPos pos, double maxDistance) {
        BlockHitResult hit = raycastProbe(pos);
        if (hit == null) {
            Vec3d eye = mc.player.getEyePos();
            BlockHitResult direct = mc.world.raycast(new RaycastContext(eye, Vec3d.ofCenter(pos), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (direct.getType() != HitResult.Type.BLOCK) {
                return null;
            }

            BlockPos hitPos = direct.getBlockPos();
            if (!hitPos.equals(pos) && mc.world.getBlockState(hitPos).getHardness(mc.world, hitPos) < 0.0F) {
                return null;
            }

            hit = direct;
        }

        return mc.player.getEyePos().distanceTo(hit.getPos()) > maxDistance ? null : hit;
    }

    private boolean canReach(BlockPos pos, double maxDistance) {
        return pos != null && raycastTo(pos, maxDistance) != null;
    }

    private BlockHitResult raycastProbe(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        double[] offsets = new double[]{0.5, 0.2, 0.8};
        for (double ox : offsets) {
            for (double oy : offsets) {
                for (double oz : offsets) {
                    Vec3d point = new Vec3d(pos.getX() + ox, pos.getY() + oy, pos.getZ() + oz);
                    BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                    if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                        return hit;
                    }
                }
            }
        }

        return null;
    }

    private void releaseKeys() {
        if (mc.options != null) {
            mc.options.attackKey.setPressed(false);
        }

        this.miningTarget = null;
    }

    private boolean drinkFireResistance(IBaritone baritone) {
        if (mc.player == null || mc.interactionManager == null || mc.options == null) {
            return false;
        }

        if (this.drinking) {
            if (needsFireResistance() && this.drinkSlot >= 0 && isFireResistancePotion(this.drinkSlot) && !this.drinkTimer.elapsed(3500L)) {
                selectSlot(this.drinkSlot);
                mc.options.useKey.setPressed(true);
                if (!mc.player.isUsingItem()) {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }

                return true;
            }

            stopDrinking(baritone);
            return false;
        }

        if (!needsFireResistance()) {
            this.pearlWarned = false;
            return false;
        }

        int slot = findFireResistancePotion();
        if (slot == -1) {
            if (!this.pearlWarned) {
                ChatUtil.sendMessage("[AutoAncient] Fire resistance potion is missing from hotbar.");
                this.pearlWarned = true;
            }

            return false;
        }

        this.pearlWarned = false;
        stopEating(baritone);
        this.drinking = true;
        this.drinkSlot = slot;
        this.drinkReturnSlot = mc.player.getInventory().selectedSlot;
        this.drinkTimer.reset();
        if (!this.drinkPaused) {
            baritone.getCommandManager().execute("pause");
            this.drinkPaused = true;
        }

        releaseKeys();
        selectSlot(this.drinkSlot);
        mc.options.useKey.setPressed(true);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        logDebug("drink fire res " + this.drinkSlot);
        return true;
    }

    private void stopDrinking(IBaritone baritone) {
        if (mc.options != null) {
            mc.options.useKey.setPressed(false);
        }

        if (mc.player != null && this.drinkReturnSlot >= 0 && this.drinkReturnSlot < 9) {
            selectSlot(this.drinkReturnSlot);
        }

        if (baritone != null && this.drinkPaused) {
            baritone.getCommandManager().execute("resume");
        }

        this.drinkPaused = false;
        this.drinking = false;
        this.drinkSlot = -1;
        this.drinkReturnSlot = -1;
    }

    private boolean needsFireResistance() {
        if (mc.player == null) {
            return false;
        }

        StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
        return effect == null || effect.getDuration() <= 300;
    }

    private int findFireResistancePotion() {
        if (mc.player == null) {
            return -1;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (isFireResistancePotion(slot)) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isFireResistancePotion(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) {
            return false;
        }

        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.isEmpty() || !stack.isOf(Items.POTION)) {
            return false;
        }

        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }

        for (StatusEffectInstance effect : contents.getEffects()) {
            RegistryEntry<?> type = effect.getEffectType();
            if (type.equals(StatusEffects.FIRE_RESISTANCE)) {
                return true;
            }
        }

        return false;
    }

    private boolean eatFood(IBaritone baritone) {
        if (mc.player == null || mc.interactionManager == null || mc.options == null) {
            return false;
        }

        if (this.eating) {
            if (mc.player.getHungerManager().getFoodLevel() < 19
                    && this.eatSlot >= 0
                    && isFood(this.eatSlot)
                    && !this.eatTimer.elapsed(7000L)) {
                selectSlot(this.eatSlot);
                mc.options.useKey.setPressed(true);
                if (!mc.player.isUsingItem()) {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }

                return true;
            }

            stopEating(baritone);
            return false;
        }

        if (this.state == State.PLACING_TNT
                || this.state == State.IGNITING_TNT
                || this.state == State.WAITING_EXPLOSION) {
            return false;
        }

        if (mc.player.getHungerManager().getFoodLevel() <= 16 && mc.player.canConsume(false)) {
            int slot = findFood();
            if (slot == -1) {
                return false;
            }

            this.eating = true;
            this.eatSlot = slot;
            this.eatReturnSlot = mc.player.getInventory().selectedSlot;
            this.eatTimer.reset();
            if (!this.eatPaused) {
                baritone.getCommandManager().execute("pause");
                this.eatPaused = true;
            }

            releaseKeys();
            selectSlot(this.eatSlot);
            mc.options.useKey.setPressed(true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            logDebug("eat " + this.eatSlot);
            return true;
        }

        return false;
    }

    private void stopEating(IBaritone baritone) {
        if (mc.options != null) {
            mc.options.useKey.setPressed(false);
        }

        if (mc.player != null && this.eatReturnSlot >= 0 && this.eatReturnSlot < 9) {
            selectSlot(this.eatReturnSlot);
        }

        if (baritone != null && this.eatPaused) {
            baritone.getCommandManager().execute("resume");
        }

        this.eatPaused = false;
        this.eating = false;
        this.eatSlot = -1;
        this.eatReturnSlot = -1;
    }

    private int findFood() {
        if (mc.player == null) {
            return -1;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (isFood(slot)) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isFood(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) {
            return false;
        }

        ItemStack stack = mc.player.getInventory().getStack(slot);
        return !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD);
    }

    private boolean escapeLava(IBaritone baritone) {
        if (mc.player == null || mc.world == null || mc.options == null) {
            return false;
        }

        if (!mc.player.isInLava()) {
            if (this.inLava) {
                stopLavaEscape();
            }

            return false;
        }

        if (!this.inLava) {
            this.lavaJumpTimer.reset();
            logMain("Упал в лаву — выбираюсь");
        }

        this.inLava = true;
        stopEating(baritone);
        releaseKeys();
        this.miningTarget = null;
        BaritoneAPI.getSettings().assumeWalkOnLava.value = true;
        if (this.lavaEscapeTarget == null || this.pathStuckTimer.elapsed(2500L)) {
            BlockPos escape = findEscapeSpot(10);
            if (escape != null) {
                this.lavaEscapeTarget = escape.toImmutable();
                this.pathStuckTimer.reset();
                logDebug("lava escape " + this.lavaEscapeTarget.toShortString());
            }
        }

        if (this.lavaEscapeTarget != null) {
            walkTo(baritone, this.lavaEscapeTarget, 1);
        }

        if (this.pearlPhase == PearlPhase.IDLE && this.pearlsEnabled.c() && this.lavaJumpTimer.elapsed(3500L)) {
            BlockPos jumpTarget = findEscapeSpot(24);
            if (jumpTarget != null && throwPearlTo(Vec3d.ofBottomCenter(jumpTarget), "выбираюсь из лавы")) {
                this.lavaJumpTimer.reset();
                mc.options.jumpKey.setPressed(true);
                return true;
            }

            this.lavaJumpTimer.reset();
        }

        mc.options.jumpKey.setPressed(true);
        return true;
    }

    private void stopLavaEscape() {
        if (mc.options != null) {
            mc.options.jumpKey.setPressed(false);
        }

        if (this.baritoneSettingsApplied) {
            BaritoneAPI.getSettings().assumeWalkOnLava.value = false;
        }

        this.inLava = false;
        this.lavaEscapeTarget = null;
    }

    private BlockPos findEscapeSpot(int radius) {
        BlockPos origin = mc.player.getBlockPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 7; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (isEscapeSpot(pos)) {
                        double score = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) + Math.max(0, y) * 0.6;
                        if (score < bestScore) {
                            bestScore = score;
                            best = pos.toImmutable();
                        }
                    }
                }
            }
        }

        return best;
    }

    private boolean isEscapeSpot(BlockPos pos) {
        return isSolidNonReplaceable(pos.down())
                && isPassable(pos)
                && isPassable(pos.up())
                && !mc.world.getBlockState(pos.down()).isOf(Blocks.LAVA)
                && !mc.world.getBlockState(pos).isOf(Blocks.LAVA)
                && !mc.world.getBlockState(pos.up()).isOf(Blocks.LAVA);
    }

    private boolean isPassable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private boolean isWalledInLava(BlockPos pos) {
        int lava = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (mc.world.getBlockState(neighbor).isOf(Blocks.LAVA)) {
                lava++;
            } else if (isPassable(neighbor)) {
                return false;
            }
        }

        if (lava == 0) {
            return false;
        }

        return this.lavaWallCache.computeIfAbsent(pos.toImmutable(), check -> findEscapeSpotNear(check, 4) == null);
    }

    private boolean hasAdjacentSolid(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            Block block = mc.world.getBlockState(pos.offset(direction)).getBlock();
            if (isAirLike(block) || block == Blocks.LAVA) {
                return true;
            }
        }

        return false;
    }

    private boolean isStuck(IBaritone baritone) {
        if (mc.player != null && baritone.getPathingBehavior().isPathing()) {
            Vec3d pos = mc.player.getPos();
            if (this.lastPathPos != null && !(pos.squaredDistanceTo(this.lastPathPos) > 0.04)) {
                return this.pathStuckTimer.elapsed(2500L);
            }

            this.lastPathPos = pos;
            this.pathStuckTimer.reset();
            return false;
        }

        resetStuckCheck();
        return false;
    }

    private Vec3d lastPathPos;

    private void resetStuckCheck() {
        this.lastPathPos = mc.player == null ? null : mc.player.getPos();
        this.pathStuckTimer.reset();
    }

    private void rebuildPath(IBaritone baritone) {
        releaseKeys();
        pingBlocks(mc.player.getBlockPos(), 1);
        stopPathing();
        switch (this.state) {
            case MOVING_SEARCH -> {
                if (this.searchTarget != null) {
                    walkTo(baritone, this.searchTarget, 1);
                }
            }
            case MOVING_SITE -> {
                if (this.siteTarget != null) {
                    walkTo(baritone, this.siteTarget, 2);
                }
            }
            case CLEARING_SITE -> {
                if (this.tntTarget != null) {
                    approachForBreaking(baritone, this.tntTarget);
                }
            }
            case PLACING_TNT, IGNITING_TNT -> {
                if (this.tntTarget != null) {
                    approachForBreaking(baritone, this.tntTarget);
                }
            }
            default -> {
            }
        }

        resetStuckCheck();
        logDebug("path rebuild");
    }

    private void approachForBreaking(IBaritone baritone, BlockPos target) {
        walkTo(baritone, target, 2);
    }

    private boolean within(BlockPos pos, double squaredDistance) {
        return mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= squaredDistance;
    }

    private int clampY(int y) {
        return y + MathHelper.clamp(36 - y, -4, 4);
    }

    private int heightBonus(int y) {
        int distance = Math.abs(y - 36);
        return Math.max(-120, 90 - distance * 6);
    }

    private boolean isMyExplosion(BlockPos center) {
        if (this.tntTarget != null) {
            return distanceSq3D(center, this.tntTarget) <= 2304.0;
        }

        return this.state == State.WAITING_EXPLOSION || this.state == State.WAITING_SCAN;
    }

    private double distanceSq3D(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private double distanceSqXZ3D(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private boolean isSolidNonReplaceable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.isReplaceable() && state.getFluidState().isEmpty();
    }

    private boolean isAirLike(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }

    private boolean isNetherRock(Block block) {
        return block == Blocks.NETHERRACK
                || block == Blocks.BASALT
                || block == Blocks.SMOOTH_BASALT
                || block == Blocks.BLACKSTONE
                || block == Blocks.SOUL_SAND
                || block == Blocks.SOUL_SOIL
                || block == Blocks.GRAVEL
                || block == Blocks.NETHER_GOLD_ORE
                || block == Blocks.NETHER_QUARTZ_ORE;
    }

    private int hotbarSlot(Item item) {
        if (mc.player == null) {
            return -1;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(item)) {
                return slot;
            }
        }

        return -1;
    }

    private void selectSlot(int slot) {
        if (mc.player.getInventory().selectedSlot != slot) {
            mc.player.getInventory().selectedSlot = slot;
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
        }
    }

    private void pingBlocks(BlockPos center, int radius) {
        if (mc.getNetworkHandler() != null) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = center.add(x, y, z);
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.UP));
                    }
                }
            }
        }
    }

    private void setState(State newState) {
        if (this.state != newState) {
            logDebug(this.state + " -> " + newState);
        }

        this.state = newState;
        this.stateTimer.reset();
        this.stuckTimer.reset();
    }

    private AncientXray xray() {
        return Laura.getInstance().getModuleProcessor().t().ancientXray();
    }

    private void logDebug(String message) {
        if (this.debug.c()) {
            ChatUtil.sendMessage("[AutoAncient] " + message);
        }
    }

    private void logMain(String message) {
        if (this.logChat.c()) {
            ChatUtil.sendMessage("[AutoAncient] " + message);
        }
    }

    private int countItem(Item item) {
        if (mc.player == null) {
            return 0;
        }

        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private boolean isPearlInFlight(IBaritone baritone) {
        if (this.pearlPhase == PearlPhase.IDLE) {
            return false;
        }

        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.options == null) {
            cancelPearl(baritone);
            return false;
        }

        if (mc.player.isInLava()) {
            mc.options.jumpKey.setPressed(true);
        }

        if (this.pearlStateSlot >= 0 && this.pearlPhase == PearlPhase.AWAITING) {
            selectSlot(this.pearlStateSlot);
            this.pearlStateSlot = -1;
        }

        if (this.pearlPhase == PearlPhase.AIMING) {
            if (this.pearlThrowTimer.elapsed(2000L)) {
                logDebug("pearl aim timeout");
                cancelPearl(baritone);
                return false;
            }

            int pearlSlot = hotbarSlot(Items.ENDER_PEARL);
            if (pearlSlot == -1) {
                cancelPearl(baritone);
                return false;
            }

            Rotation target = new Rotation(this.pearlYaw, this.pearlPitch);
            aimAt(target);
            if (new Rotation(mc.player).a(target) > 2.5F) {
                return true;
            }

            Vec3d from = mc.player.getEyePos().subtract(0.0, 0.1, 0.0);
            PearlSolution solution = simulatePearl(from, this.pearlTarget);
            if (solution != null && !(solution.error > 1.8) && landsSafely(solution.landing)) {
                this.pearlYaw = solution.yaw;
                this.pearlPitch = solution.pitch;
                if (new Rotation(mc.player).a(new Rotation(this.pearlYaw, this.pearlPitch)) > 2.5F) {
                    return true;
                }

                this.pearlStateSlot = mc.player.getInventory().selectedSlot;
                selectSlot(pearlSlot);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                this.thrownPearls++;
                this.pearlCooldown.reset();
                this.pearlThrowFrom = mc.player.getPos();
                this.pearlPhase = PearlPhase.AWAITING;
                this.pearlThrowTimer.reset();
                return true;
            }

            logDebug("pearl solution lost");
            cancelPearl(baritone);
            return false;
        }

        boolean teleported = this.pearlTarget != null && mc.player.squaredDistanceTo(this.pearlTarget) <= 25.0;
        boolean moved = this.pearlThrowFrom != null && mc.player.getPos().squaredDistanceTo(this.pearlThrowFrom) > 64.0;
        if (teleported || moved) {
            logMain("Телепорт: " + this.pearlReason);
            this.pearlFailures = 0;
            this.lastFailedPearl = null;
            resetStuckCheck();
            afterPearlCancel(baritone);
            cancelPearl(baritone);
            return false;
        }

        if (this.pearlThrowTimer.elapsed(5000L)) {
            this.pearlFailures++;
            this.lastFailedPearl = this.pearlTarget;
            logMain("Пёрка не долетела — эта цель в бане, иду пешком");
            afterPearlCancel(baritone);
            cancelPearl(baritone);
            return false;
        }

        return true;
    }

    private void cancelPearl(IBaritone baritone) {
        if (this.pearlStateSlot >= 0) {
            selectSlot(this.pearlStateSlot);
            this.pearlStateSlot = -1;
        }

        this.pearlPhase = PearlPhase.IDLE;
        this.pearlTarget = null;
        this.pearlReason = null;
        this.pearlThrowFrom = null;
    }

    private void afterPearlCancel(IBaritone baritone) {
        pingBlocks(mc.player.getBlockPos(), 1);
        this.walkGoal = null;
        if (this.state == State.MINING) {
            this.miningPhase = MiningPhase.APPROACHING;
            this.miningTarget = null;
            this.approachTimer.reset();
        } else {
            rebuildPath(baritone);
        }

        resetStuckCheck();
    }

    private boolean throwPearlToOre(BlockPos target, String reason) {
        if (target == null || !this.pearlsEnabled.c() || this.pearlPhase != PearlPhase.IDLE) {
            return false;
        }

        if (!this.pearlCooldown.elapsed(1200L)) {
            return false;
        }

        this.pearlCooldown.reset();
        Vec3d targetCenter = Vec3d.ofCenter(target);
        if (targetCenter.y - mc.player.getY() > 2.5) {
            return false;
        }

        double dx = targetCenter.x - mc.player.getX();
        double dz = targetCenter.z - mc.player.getZ();
        double horizontalSq = dx * dx + dz * dz;
        double minDistance = this.pearlMinDistance.c();
        if (horizontalSq < minDistance * minDistance) {
            return false;
        }

        BlockPos clamped = target;
        if (horizontalSq > 729.0) {
            Vec3d direction = targetCenter.subtract(mc.player.getPos()).normalize();
            clamped = BlockPos.ofFloored(mc.player.getPos().add(direction.multiply(27.0)));
        }

        BlockPos landing = findEscapeSpotNear(clamped, 5);
        return landing != null && throwPearlTo(Vec3d.ofBottomCenter(landing), reason);
    }

    private boolean throwPearlTo(Vec3d target, String reason) {
        if (!this.pearlsEnabled.c() || this.pearlPhase != PearlPhase.IDLE || target == null) {
            return false;
        }

        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }

        if (this.eating || this.drinking) {
            return false;
        }

        if (!this.pearlPlanReady()) {
            return false;
        }

        if (hotbarSlot(Items.ENDER_PEARL) == -1) {
            return false;
        }

        boolean inLava = mc.player.isInLava();
        if (!inLava && mc.player.getHealth() < 8.0F) {
            return false;
        }

        if (!inLava && target.y - mc.player.getY() > 2.5) {
            return false;
        }

        if (this.lastFailedPearl != null && target.squaredDistanceTo(this.lastFailedPearl) < 16.0) {
            return false;
        }

        Vec3d from = mc.player.getEyePos().subtract(0.0, 0.1, 0.0);
        PearlSolution solution = simulatePearl(from, target);
        if (solution != null && !(solution.error > 1.8) && landsSafely(solution.landing)) {
            this.pearlTarget = target;
            this.pearlReason = reason;
            this.pearlYaw = solution.yaw;
            this.pearlPitch = solution.pitch;
            this.pearlPhase = PearlPhase.AIMING;
            this.pearlThrowTimer.reset();
            releaseKeys();
            stopPathing();
            logMain("Кидаю пёрку: " + reason + " → " + (int) Math.floor(target.x) + " " + (int) Math.floor(target.y) + " " + (int) Math.floor(target.z));
            return true;
        }

        logDebug("pearl no solution: " + reason);
        return false;
    }

    private boolean pearlPlanReady() {
        long cooldown = 2500L * (1L + Math.min(this.pearlFailures, 3));
        return this.pearlPlanTimer.elapsed(cooldown);
    }

    private BlockPos findEscapeSpotNear(BlockPos center, int radius) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (isEscapeSpot(pos)) {
                        double score = distanceSq3D(pos, center);
                        if (score < bestScore) {
                            bestScore = score;
                            best = pos.toImmutable();
                        }
                    }
                }
            }
        }

        return best;
    }

    private boolean landsSafely(Vec3d landing) {
        BlockPos pos = BlockPos.ofFloored(landing);
        if (mc.world.getBlockState(pos).isOf(Blocks.LAVA) || mc.world.getBlockState(pos.up()).isOf(Blocks.LAVA)) {
            return false;
        }

        for (int down = 1; down <= 4; down++) {
            BlockPos below = pos.down(down);
            if (mc.world.getBlockState(below).isOf(Blocks.LAVA)) {
                return false;
            }

            if (isSolidNonReplaceable(below)) {
                return true;
            }
        }

        return false;
    }

    private PearlSolution simulatePearl(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        float baseYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        PearlSolution best = null;

        for (float yawOffset = -6.0F; yawOffset <= 6.0F; yawOffset += 2.0F) {
            float yaw = baseYaw + yawOffset;
            for (float pitch = -40.0F; pitch <= 80.0F; pitch += 2.0F) {
                PearlSolution solution = simulatePearl(from, to, yaw, pitch);
                if (solution != null && (best == null || solution.error < best.error)) {
                    best = solution;
                }
            }
        }

        if (best == null) {
            return null;
        }

        PearlSolution refined = best;
        for (float yaw = best.yaw - 2.0F; yaw <= best.yaw + 2.0F; yaw += 0.5F) {
            for (float pitch = best.pitch - 2.0F; pitch <= best.pitch + 2.0F; pitch += 0.3F) {
                PearlSolution solution = simulatePearl(from, to, yaw, pitch);
                if (solution != null && solution.error < refined.error) {
                    refined = solution;
                }
            }
        }

        return refined;
    }

    private PearlSolution simulatePearl(Vec3d from, Vec3d to, float yaw, float pitch) {
        Vec3d velocity = pearlVelocity(yaw, pitch);
        Vec3d landing = tracePearl(from, velocity);
        if (landing == null) {
            return null;
        }

        double error = Math.sqrt(landing.squaredDistanceTo(to));
        return new PearlSolution(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0F, 90.0F), error, landing);
    }

    private Vec3d pearlVelocity(float yaw, float pitch) {
        float yawRad = (float) (yaw * (Math.PI / 180.0));
        float pitchRad = (float) (pitch * (Math.PI / 180.0));
        double x = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        double y = -MathHelper.sin(pitchRad);
        double z = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        Vec3d direction = new Vec3d(x, y, z).normalize().multiply(1.5);
        Vec3d movement = mc.player.getMovement();
        return direction.add(movement.x, mc.player.isOnGround() ? 0.0 : movement.y, movement.z);
    }

    private Vec3d tracePearl(Vec3d start, Vec3d velocity) {
        if (mc.world == null) {
            return null;
        }

        Vec3d pos = start;
        Vec3d vel = velocity;
        for (int step = 0; step < 160; step++) {
            vel = vel.subtract(0.0, 0.03, 0.0).multiply(0.99);
            Vec3d next = pos.add(vel);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit.getPos();
            }

            pos = next;
        }

        return pos;
    }

    private void applyBaritoneSettings() {
        BaritoneSettings settings = BaritoneAPI.getSettings();
        this.savedAllowPlace = settings.allowPlace.value;
        this.savedAllowBreak = settings.allowBreak.value;
        this.savedAssumeLava = settings.assumeWalkOnLava.value;
        this.savedWalkWhileBreaking = settings.walkWhileBreaking.value;
        List<Block> blocksToAvoid = settings.blocksToAvoid.value;
        this.savedBlocksToAvoid = blocksToAvoid == null ? List.of() : new ArrayList<>(blocksToAvoid);
        List<Item> throwaway = settings.acceptableThrowawayItems.value;
        this.savedThrowawayItems = throwaway == null ? List.of() : new ArrayList<>(throwaway);
        settings.allowPlace.value = true;
        settings.allowBreak.value = true;
        settings.assumeWalkOnLava.value = false;
        settings.walkWhileBreaking.value = false;
        if (blocksToAvoid != null) {
            blocksToAvoid.remove(Blocks.LAVA);
        }

        if (throwaway != null) {
            throwaway.remove(Blocks.TNT.asItem());
            addUniqueItem(throwaway, Blocks.NETHERRACK.asItem());
            addUniqueItem(throwaway, Blocks.BLACKSTONE.asItem());
            addUniqueItem(throwaway, Blocks.BASALT.asItem());
            addUniqueItem(throwaway, Blocks.COBBLESTONE.asItem());
        }

        this.baritoneSettingsApplied = true;
        this.enabledAt = System.currentTimeMillis();
    }

    private void restoreBaritoneSettings() {
        if (this.baritoneSettingsApplied) {
            BaritoneSettings settings = BaritoneAPI.getSettings();
            settings.allowPlace.value = this.savedAllowPlace;
            settings.allowBreak.value = this.savedAllowBreak;
            settings.assumeWalkOnLava.value = this.savedAssumeLava;
            settings.walkWhileBreaking.value = this.savedWalkWhileBreaking;
            List<Block> blocksToAvoid = settings.blocksToAvoid.value;
            if (blocksToAvoid != null) {
                blocksToAvoid.clear();
                blocksToAvoid.addAll(this.savedBlocksToAvoid);
            }

            List<Item> throwaway = settings.acceptableThrowawayItems.value;
            if (throwaway != null) {
                throwaway.clear();
                throwaway.addAll(this.savedThrowawayItems);
            }

            this.baritoneSettingsApplied = false;
        }
    }

    private static void addUniqueItem(List<Item> list, Item item) {
        if (!list.contains(item)) {
            list.add(item);
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

    private record PearlSolution(float yaw, float pitch, double error, Vec3d landing) {
    }

    private enum State {
        SEARCHING,
        MOVING_SEARCH,
        MOVING_SITE,
        CLEARING_SITE,
        PLACING_TNT,
        IGNITING_TNT,
        WAITING_EXPLOSION,
        WAITING_SCAN,
        MINING
    }

    private enum BreakMode {
        AIMING,
        BREAKING,
        STUCK,
        NO_REACH
    }

    private enum PearlPhase {
        IDLE,
        AIMING,
        AWAITING
    }

    private enum MiningPhase {
        APPROACHING,
        BREAKING
    }
}
