package net.pixeldreamstudios.mobs_of_mythology.entity.mobs;

import mod.azure.azurelib.animatable.GeoEntity;
import mod.azure.azurelib.core.animatable.instance.AnimatableInstanceCache;
import mod.azure.azurelib.core.animation.AnimatableManager;
import mod.azure.azurelib.core.animation.AnimationController;
import mod.azure.azurelib.core.object.PlayState;
import mod.azure.azurelib.util.AzureLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.pixeldreamstudios.mobs_of_mythology.MobsOfMythology;
import net.pixeldreamstudios.mobs_of_mythology.entity.constant.DefaultMythAnimations;
import net.pixeldreamstudios.mobs_of_mythology.registry.ItemRegistry;
import net.pixeldreamstudios.mobs_of_mythology.registry.SoundRegistry;
import net.tslat.smartbrainlib.api.core.navigation.SmoothGroundNavigation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AutomatonEntity extends TamableAnimal implements GeoEntity, NeutralMob {
    private final AnimatableInstanceCache cache = AzureLibUtil.createInstanceCache(this);
    private int remainingAngerTime;
    private UUID persistentAngerTarget;

    public AutomatonEntity(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.navigation = new SmoothGroundNavigation(this, level);
    }

    @Override
    public void setTame(boolean bl) {
        super.setTame(bl);
        if (bl) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(MobsOfMythology.config.automatonHealth * 2);
            this.setHealth((float) (MobsOfMythology.config.automatonHealth * 2));
        } else {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(MobsOfMythology.config.automatonHealth);
        }
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return null;
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemRegistry.BRONZE_INGOT.get());
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.2, 8.0F, 2.0F, false));
        this.goalSelector.addGoal(4, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Monster.class, false));
        this.targetSelector.addGoal(5, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MobsOfMythology.config.automatonHealth)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, MobsOfMythology.config.automatonAttackDamage);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    protected void produceParticles(ParticleOptions parameters) {
        if (this.level().isClientSide) {
            for (int i = 0; i < 2; ++i) {
                double d = this.random.nextGaussian() * 0.02;
                double e = this.random.nextGaussian() * 0.02;
                double f = this.random.nextGaussian() * 0.02;
                this.level().addParticle(parameters, this.getRandomX(1.0), this.getRandomY() + 1.0, this.getRandomZ(1.0), d, e, f);
            }
        }
    }
    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (this.remainingAngerTime > 0) {
                this.remainingAngerTime--;
                if (this.remainingAngerTime == 0) {
                    this.setPersistentAngerTarget(null);
                    this.setTarget(null);
                }
            }
        }
        if (this.isTame() && this.getOwner() != null && !this.isOrderedToSit()) {
            double distanceSq = this.distanceToSqr(this.getOwner());
            if (distanceSq > 144.0D) {
                this.teleportToOwner();
            }
        }

        if (getHealth() < 50) {
            produceParticles(getHealth() < 25 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.SMOKE);
        }
    }
    private void teleportToOwner() {
        LivingEntity owner = this.getOwner();
        if (owner != null) {
            BlockPos ownerPos = owner.blockPosition();
            for (int i = 0; i < 10; ++i) {
                int offsetX = this.random.nextInt(7) - 3;
                int offsetY = this.random.nextInt(3) - 1;
                int offsetZ = this.random.nextInt(7) - 3;

                BlockPos teleportPos = ownerPos.offset(offsetX, offsetY, offsetZ);
                if (this.level().noCollision(this, this.getBoundingBox().move(teleportPos.subtract(this.blockPosition())))) {
                    this.teleportTo(teleportPos.getX() + 0.5, teleportPos.getY(), teleportPos.getZ() + 0.5);
                    this.navigation.recomputePath();
                    break;
                }
            }
        }
    }

    @Override
    public @NotNull InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);
        Item item = itemStack.getItem();
        if (((Level)this.level()).isClientSide) {
            boolean bl = this.isOwnedBy(player) || this.isTame() || itemStack.is(ItemRegistry.GEAR.get()) && !this.isTame();
            return bl ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        if (this.isTame()) {
            InteractionResult interactionResult;
            if (this.isFood(itemStack) && this.getHealth() < this.getMaxHealth()) {
                FoodProperties foodProperties = item.getFoodProperties();
                if (foodProperties != null) {
                    if (!player.getAbilities().instabuild) {
                        itemStack.shrink(1);
                    }
                    this.heal(2.0f * foodProperties.getNutrition());
                    return InteractionResult.SUCCESS;
                }
            }

            if ((interactionResult = super.mobInteract(player, interactionHand)).consumesAction() && !this.isBaby() || !this.isOwnedBy(player)) return interactionResult;
            this.playSound(SoundRegistry.ROBOTIC_VOICE.get(), 1.0f, 1.0f);
            if (getServer() != null) {
                getServer().tell(new TickTask(0, () -> player.displayClientMessage(Component.literal(isInSittingPose() ? "I will follow you." : "I will wait for you."), true)));
            }
            this.setOrderedToSit(!this.isOrderedToSit());
            this.jumping = false;
            this.navigation.recomputePath();
            this.setTarget(null);
            return InteractionResult.SUCCESS;
        }
        if (!itemStack.is(ItemRegistry.GEAR.get())) return super.mobInteract(player, interactionHand);
        if (!player.getAbilities().instabuild) {
            itemStack.shrink(1);
        }
        if (this.random.nextInt(3) == 0) {
            this.tame(player);
            this.navigation.recomputePath();
            this.setTarget(null);
            this.setOrderedToSit(true);
            this.playSound(SoundRegistry.ROBOTIC_VOICE.get(), 1.0f, 1.0f);
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.tell(new TickTask(0, () -> player.displayClientMessage(Component.literal("I will protect you at all costs, " + player.getScoreboardName() + "."), true)));
            }
            ((Level)this.level()).broadcastEntityEvent(this, (byte)7);
            return InteractionResult.SUCCESS;
        } else {
            ((Level)this.level()).broadcastEntityEvent(this, (byte)6);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.845f * this.getEyeHeight(), this.getBbWidth() * 0.4f);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "livingController", 3, event -> {
            if (event.isMoving() && !swinging) {
                return event.setAndContinue(DefaultMythAnimations.WALK);
            }
            return event.setAndContinue(DefaultMythAnimations.IDLE);
        })).add(new AnimationController<>(this, "attackController", 3, event -> {
            swinging = false;
            return PlayState.STOP;
        }).triggerableAnim("attack", DefaultMythAnimations.ATTACK).triggerableAnim("attack2", DefaultMythAnimations.ATTACK2));
    }
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            if (this.isTame() && this.isOwnedBy(attacker)) {
                return super.hurt(source, amount);
            }
            this.setOrderedToSit(false);
            this.setPersistentAngerTarget(attacker.getUUID());
            this.startPersistentAngerTimer();
            this.setTarget(attacker);
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        this.triggerAnim("attackController", "attack");
        this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 255, true, true, true));
        return super.doHurtTarget(entity);
    }
    public void tame(Player player) {
        this.setTame(true);
        this.setOwnerUUID(player.getUUID());
    }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundRegistry.ROBOTIC_VOICE.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.0f, 1.0f);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int time) {
        this.remainingAngerTime = time;
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target) {
        this.persistentAngerTarget = target;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.remainingAngerTime = 600;
    }
    @Override
    public boolean canAttack(LivingEntity livingEntity) {
        return !this.isOwnedBy(livingEntity) && super.canAttack(livingEntity);
    }
}