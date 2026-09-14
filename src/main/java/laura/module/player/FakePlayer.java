package laura.module.player;

import com.mojang.authlib.GameProfile;
import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.AttackEvent;
import laura.event.TickEvent;
import laura.setting.BooleanSetting;
import laura.setting.ModeSetting;
import laura.setting.SliderSetting;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DeathProtectionComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Портировано из Wexside (модуль FakePlayer).
 * Создаёт локального фейкового игрока для тренировки атак и тотемов:
 * бот получает урон, прожимает тотем и может вести себя как манекен или двигаться.
 */
@ModuleRegister(name = "FakePlayer", description = "Создаёт локального бота для тренировки атак и тотемов", category = Category.Player)
public final class FakePlayer extends Module {
    private static final UUID BOT_UUID = UUID.nameUUIDFromBytes("LauraClient:FakeBot".getBytes(StandardCharsets.UTF_8));

    private final ModeSetting armor = new ModeSetting("Броня", "Копировать", "Копировать", "Без брони",
            "Кожаная", "Кольчужная", "Золотая", "Железная", "Алмазная", "Незеритовая");
    private final BooleanSetting totems = new BooleanSetting("Снятие тотемов", true);
    private final ModeSetting behavior = new ModeSetting("Поведение", "Манекен", "Манекен", "Подвижный");
    private final SliderSetting activity = new SliderSetting("Активность", 1.0f, 0.3f, 1.5f, 0.05f)
            .a(() -> this.behavior.l("Подвижный"));
    private final BooleanSetting jumps = new BooleanSetting("Прыжки", true)
            .a(() -> this.behavior.l("Подвижный"));
    private final BooleanSetting swings = new BooleanSetting("Замахи", true)
            .a(() -> this.behavior.l("Подвижный"));

    private FakeBot bot;
    private ClientWorld botWorld;
    private String appliedArmor;
    private int healDelay;
    private double moveX;
    private double moveY;
    private double moveZ;
    private int strafeDirection = 1;
    private int strafeTimer;
    private double orbitDistance = 3.0d;
    private int orbitTimer;
    private int pauseTimer;
    private int swingTimer;
    private int jumpTimer;

    public FakePlayer() {
        a(this.behavior, this.activity, this.jumps, this.swings, this.armor, this.totems);
    }

    @Override
    public void b() {
        super.b();
        spawn();
    }

    @Override
    public void c() {
        super.c();
        remove();
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            remove();
            return;
        }
        if (this.bot == null || this.bot.isRemoved() || this.botWorld != mc.world) {
            spawn();
            return;
        }
        if (!this.armor.c().equals(this.appliedArmor)) {
            applyArmor(this.bot);
        }
        refreshTotem();
        if (this.healDelay > 0) {
            this.healDelay--;
        } else if (this.bot.getHealth() < this.bot.getMaxHealth()) {
            this.bot.heal(0.1f);
        }
    }

    @EventTarget
    public void a(AttackEvent event) {
        if (event.b() != this.bot || this.bot == null || mc.player == null || mc.world == null) {
            return;
        }
        float cooldown = mc.player.getAttackCooldownProgress(0.5f);
        boolean crit = cooldown > 0.9f
                && mc.player.fallDistance > 0.0f
                && !mc.player.isOnGround()
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.hasVehicle()
                && !mc.player.isSprinting();
        float damage = 0.5f * (crit ? 1.5f : 1.0f);
        this.healDelay = 20;
        mc.player.resetLastAttackedTicks();
        this.bot.animateDamage(mc.player.getYaw());
        playAttackSound(crit, cooldown);
        spawnHitParticles(crit);
        if (!this.totems.c().booleanValue()) {
            this.bot.setHealth(this.bot.getMaxHealth());
            this.bot.setAbsorptionAmount(0.0f);
            this.healDelay = 0;
            event.a(true);
            mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }
        float remaining = Math.max(0.0f, damage);
        float absorbed = Math.min(this.bot.getAbsorptionAmount(), remaining);
        if (absorbed > 0.0f) {
            this.bot.setAbsorptionAmount(this.bot.getAbsorptionAmount() - absorbed);
            remaining -= absorbed;
        }
        float health = this.bot.getHealth() - remaining;
        if (health <= 0.0f) {
            popTotem();
        } else {
            this.bot.setHealth(health);
            mc.world.playSoundClient(this.bot.getX(), this.bot.getY(), this.bot.getZ(),
                    SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
        }
        event.a(true);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void spawn() {
        remove();
        if (mc.player == null || mc.world == null) {
            return;
        }
        GameProfile profile = new GameProfile(BOT_UUID, "LauraBot");
        profile.getProperties().putAll(mc.player.getGameProfile().getProperties());
        SkinTextures textures = mc.player.getSkinTextures();
        FakeBot entity = new FakeBot(mc.world, profile, textures);
        entity.setId(nextEntityId(mc.world));
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0.0d, look.z);
        if (flat.lengthSquared() < 1.0E-6d) {
            flat = new Vec3d(0.0d, 0.0d, 1.0d);
        } else {
            flat = flat.normalize();
        }
        double x = mc.player.getX() + (flat.x * 2.5d);
        double z = mc.player.getZ() + (flat.z * 2.5d);
        float yaw = mc.player.getYaw() + 180.0f;
        entity.refreshPositionAndAngles(x, mc.player.getY(), z, yaw, 0.0f);
        entity.bodyYaw = yaw;
        entity.headYaw = yaw;
        entity.lastBodyYaw = yaw;
        entity.lastHeadYaw = yaw;
        entity.setOnGround(true);
        equip(entity);
        entity.setHealth(entity.getMaxHealth());
        mc.world.addEntity(entity);
        this.bot = entity;
        this.botWorld = mc.world;
        this.healDelay = 0;
        this.moveX = 0.0d;
        this.moveY = 0.0d;
        this.moveZ = 0.0d;
        this.strafeTimer = 0;
        this.orbitTimer = 0;
        this.pauseTimer = 0;
        this.swingTimer = 0;
        this.jumpTimer = 0;
    }

    private void equip(FakeBot entity) {
        applyArmor(entity);
        entity.equipStack(EquipmentSlot.MAINHAND, mc.player.getMainHandStack().copy());
        entity.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
    }

    private void applyArmor(FakeBot entity) {
        switch (this.armor.c()) {
            case "Без брони":
                equipSet(entity, null, null, null, null);
                break;
            case "Кожаная":
                equipSet(entity, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
                break;
            case "Кольчужная":
                equipSet(entity, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
                break;
            case "Золотая":
                equipSet(entity, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
                break;
            case "Железная":
                equipSet(entity, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                break;
            case "Алмазная":
                equipSet(entity, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
                break;
            case "Незеритовая":
                equipSet(entity, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
                break;
            default:
                entity.equipStack(EquipmentSlot.HEAD, mc.player.getEquippedStack(EquipmentSlot.HEAD).copy());
                entity.equipStack(EquipmentSlot.CHEST, mc.player.getEquippedStack(EquipmentSlot.CHEST).copy());
                entity.equipStack(EquipmentSlot.LEGS, mc.player.getEquippedStack(EquipmentSlot.LEGS).copy());
                entity.equipStack(EquipmentSlot.FEET, mc.player.getEquippedStack(EquipmentSlot.FEET).copy());
                break;
        }
        this.appliedArmor = this.armor.c();
    }

    private void equipSet(FakeBot entity, Item helmet, Item chestplate, Item leggings, Item boots) {
        entity.equipStack(EquipmentSlot.HEAD, stackOf(helmet));
        entity.equipStack(EquipmentSlot.CHEST, stackOf(chestplate));
        entity.equipStack(EquipmentSlot.LEGS, stackOf(leggings));
        entity.equipStack(EquipmentSlot.FEET, stackOf(boots));
    }

    private ItemStack stackOf(Item item) {
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private void refreshTotem() {
        if (this.bot == null) {
            return;
        }
        if (!this.totems.c().booleanValue()) {
            if (!this.bot.getOffHandStack().isEmpty()) {
                this.bot.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            }
            this.bot.setHealth(this.bot.getMaxHealth());
            this.bot.setAbsorptionAmount(0.0f);
        } else if (!this.bot.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            this.bot.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
    }

    private void popTotem() {
        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        this.bot.setHealth(1.0f);
        this.bot.deathTime = 0;
        DeathProtectionComponent component = totem.get(DataComponentTypes.DEATH_PROTECTION);
        if (component != null) {
            component.applyDeathEffects(totem, this.bot);
        }
        this.healDelay = 20;
        refreshTotem();
        spawnTotemParticles();
        mc.world.playSoundClient(this.bot.getX(), this.bot.getY(), this.bot.getZ(),
                SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
    }

    private void playAttackSound(boolean crit, float cooldown) {
        mc.world.playSoundClient(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                crit ? SoundEvents.ENTITY_PLAYER_ATTACK_CRIT
                        : (cooldown > 0.9f ? SoundEvents.ENTITY_PLAYER_ATTACK_STRONG : SoundEvents.ENTITY_PLAYER_ATTACK_WEAK),
                SoundCategory.PLAYERS, 1.0f, 1.0f, false);
    }

    private void spawnHitParticles(boolean crit) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int critCount = crit ? 18 : 7;
        for (int index = 0; index < critCount; index++) {
            addParticle(ParticleTypes.CRIT,
                    this.bot.getX() + random.nextDouble(-0.32d, 0.32d),
                    this.bot.getBodyY(random.nextDouble(0.25d, 0.85d)),
                    this.bot.getZ() + random.nextDouble(-0.32d, 0.32d),
                    random.nextDouble(-0.35d, 0.35d), random.nextDouble(0.05d, 0.45d), random.nextDouble(-0.35d, 0.35d));
        }
        for (int index = 0; index < 4; index++) {
            addParticle(ParticleTypes.DAMAGE_INDICATOR,
                    this.bot.getX() + random.nextDouble(-0.2d, 0.2d),
                    this.bot.getBodyY(random.nextDouble(0.35d, 0.75d)),
                    this.bot.getZ() + random.nextDouble(-0.2d, 0.2d),
                    random.nextDouble(-0.08d, 0.08d), random.nextDouble(0.05d, 0.18d), random.nextDouble(-0.08d, 0.08d));
        }
        if (mc.player.getMainHandStack().hasEnchantments()) {
            for (int index = 0; index < 12; index++) {
                addParticle(ParticleTypes.ENCHANTED_HIT,
                        this.bot.getX() + random.nextDouble(-0.35d, 0.35d),
                        this.bot.getBodyY(random.nextDouble(0.2d, 0.9d)),
                        this.bot.getZ() + random.nextDouble(-0.35d, 0.35d),
                        random.nextDouble(-0.45d, 0.45d), random.nextDouble(0.05d, 0.5d), random.nextDouble(-0.45d, 0.45d));
            }
        }
    }

    private void spawnTotemParticles() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < 72; index++) {
            double angle = random.nextDouble(0.0d, Math.PI * 2.0d);
            double radius = random.nextDouble(0.05d, 0.48d);
            addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                    this.bot.getX() + (Math.cos(angle) * radius),
                    this.bot.getBodyY(random.nextDouble(0.05d, 0.95d)),
                    this.bot.getZ() + (Math.sin(angle) * radius),
                    (Math.cos(angle) * random.nextDouble(0.12d, 0.65d)) + random.nextDouble(-0.12d, 0.12d),
                    random.nextDouble(0.15d, 0.85d),
                    (Math.sin(angle) * random.nextDouble(0.12d, 0.65d)) + random.nextDouble(-0.12d, 0.12d));
        }
    }

    private void addParticle(ParticleEffect effect, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        if (mc.world != null) {
            mc.world.addParticleClient(effect, true, x, y, z, velocityX, velocityY, velocityZ);
        }
    }

    private int nextEntityId(ClientWorld world) {
        int id = -1337;
        while (world.getEntityById(id) != null) {
            id--;
        }
        return id;
    }

    private void remove() {
        if (this.bot != null && this.botWorld != null) {
            this.botWorld.removeEntity(this.bot.getId(), Entity.RemovalReason.DISCARDED);
        }
        this.bot = null;
        this.botWorld = null;
        this.healDelay = 0;
        this.appliedArmor = null;
    }

    private void updateMovement() {
        if (this.bot == null || mc.player == null || mc.world == null) {
            return;
        }
        if (!this.behavior.l("Подвижный")) {
            this.moveX = 0.0d;
            this.moveZ = 0.0d;
            this.bot.setSprinting(false);
            if (this.bot.isOnGround()) {
                this.moveY = 0.0d;
                this.bot.setVelocity(Vec3d.ZERO);
            } else {
                this.moveY = (this.moveY - 0.08d) * 0.98d;
                this.bot.move(MovementType.SELF, new Vec3d(0.0d, this.moveY, 0.0d));
                this.bot.setVelocity(0.0d, this.bot.getY() - this.bot.lastY, 0.0d);
            }
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double deltaX = mc.player.getX() - this.bot.getX();
        double deltaZ = mc.player.getZ() - this.bot.getZ();
        double distance = Math.hypot(deltaX, deltaZ);
        if (this.bot.getY() < mc.player.getY() - 24.0d || distance > 16.0d) {
            double angle = random.nextDouble(0.0d, Math.PI * 2.0d);
            this.bot.refreshPositionAndAngles(
                    mc.player.getX() + (Math.cos(angle) * 3.0d),
                    mc.player.getY(),
                    mc.player.getZ() + (Math.sin(angle) * 3.0d),
                    this.bot.getYaw(), 0.0f);
            this.moveX = 0.0d;
            this.moveY = 0.0d;
            this.moveZ = 0.0d;
            return;
        }
        float activityValue = this.activity.c().floatValue();
        if (--this.strafeTimer <= 0) {
            this.strafeDirection = random.nextBoolean() ? 1 : -1;
            this.strafeTimer = random.nextInt(18, 60);
        }
        if (--this.orbitTimer <= 0) {
            this.orbitDistance = random.nextDouble(1.6d, 4.4d);
            this.orbitTimer = random.nextInt(40, 110);
        }
        if (this.pauseTimer > 0) {
            this.pauseTimer--;
        } else if (random.nextFloat() < 0.005f) {
            this.pauseTimer = random.nextInt(6, 18);
        }
        double inverse = distance < 1.0E-4d ? 0.0d : 1.0d / distance;
        double normalX = deltaX * inverse;
        double normalZ = deltaZ * inverse;
        double approach = MathHelper.clamp((distance - this.orbitDistance) * 0.45d, -1.0d, 1.0d);
        double targetX = (normalX * approach) - (normalZ * this.strafeDirection * 0.9d);
        double targetZ = (normalZ * approach) + (normalX * this.strafeDirection * 0.9d);
        double length = Math.hypot(targetX, targetZ);
        if (length > 1.0d) {
            targetX /= length;
            targetZ /= length;
        }
        double targetSpeed = this.pauseTimer > 0 ? 0.0d : 0.26d * activityValue;
        double blend = this.bot.isOnGround() ? 0.3d : 0.1d;
        this.moveX += ((targetX * targetSpeed) - this.moveX) * blend;
        this.moveZ += ((targetZ * targetSpeed) - this.moveZ) * blend;
        if (this.jumpTimer > 0) {
            this.jumpTimer--;
        }
        if (this.bot.isOnGround()) {
            this.moveY = -0.0784d;
            if (this.jumps.c().booleanValue() && this.jumpTimer == 0 && (this.bot.horizontalCollision || random.nextFloat() < 0.035f * activityValue)) {
                this.moveY = 0.42d;
                this.jumpTimer = random.nextInt(25, 70);
            }
        } else {
            this.moveY = (this.moveY - 0.08d) * 0.98d;
        }
        this.bot.setSprinting(Math.hypot(this.moveX, this.moveZ) > 0.18d);
        this.bot.move(MovementType.SELF, new Vec3d(this.moveX, this.moveY, this.moveZ));
        this.bot.setVelocity(this.bot.getX() - this.bot.lastX, this.bot.getY() - this.bot.lastY, this.bot.getZ() - this.bot.lastZ);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-(mc.player.getX() - this.bot.getX()), mc.player.getZ() - this.bot.getZ()));
        double eyeDeltaY = mc.player.getEyeY() - this.bot.getEyeY();
        double horizontal = Math.hypot(mc.player.getX() - this.bot.getX(), mc.player.getZ() - this.bot.getZ());
        float targetPitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(eyeDeltaY, horizontal)), -60.0d, 60.0d);
        this.bot.headYaw = this.bot.headYaw + MathHelper.clamp(MathHelper.wrapDegrees(targetYaw - this.bot.headYaw), -30.0f, 30.0f);
        this.bot.setYaw(this.bot.headYaw);
        this.bot.setPitch(this.bot.getPitch() + MathHelper.clamp(targetPitch - this.bot.getPitch(), -15.0f, 15.0f));
        if (this.swingTimer > 0) {
            this.swingTimer--;
        }
        if (this.swings.c().booleanValue() && this.swingTimer == 0 && distance < 3.2d && random.nextFloat() < 0.3f) {
            this.bot.swingHand(Hand.MAIN_HAND);
            this.swingTimer = random.nextInt(11, 22);
        }
    }

    static final class FakeBot extends OtherClientPlayerEntity {
        private final SkinTextures textures;

        FakeBot(ClientWorld world, GameProfile profile, SkinTextures textures) {
            super(world, profile);
            this.textures = textures;
        }

        @Override
        public void tick() {
            FakePlayer module = activeModule();
            if (module != null) {
                module.updateMovement();
            }
            super.tick();
        }

        @Override
        public SkinTextures getSkinTextures() {
            return this.textures != null ? this.textures : super.getSkinTextures();
        }

        private static FakePlayer activeModule() {
            laura.core.Laura instance = laura.core.Laura.getInstance();
            if (instance == null) {
                return null;
            }
            for (laura.core.Module module : instance.getModuleProcessor().t().e()) {
                if (module instanceof FakePlayer fakePlayer) {
                    return fakePlayer.m() ? fakePlayer : null;
                }
            }
            return null;
        }
    }
}
