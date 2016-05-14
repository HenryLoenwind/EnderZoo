package crazypants.enderzoo.entity;

import java.util.UUID;

import crazypants.enderzoo.config.Config;
import crazypants.enderzoo.entity.ai.EntityAIAttackOnCollideOwned;
import crazypants.enderzoo.entity.ai.EntityAIFollowOwner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

public class EntityWitherCat extends EntityMob implements IOwnable<EntityWitherCat, EntityWitherWitch>, IEnderZooMob {

  public enum GrowthMode {
    NONE,
    GROW,
    SHRINK
  };

  public static final String NAME = "WitherCat";
  public static final int EGG_BG_COL = 0x303030;
  public static final int EGG_FG_COL = 0xFFFFFF;

  private static final float DEF_HEIGHT = 0.8F;
  private static final float DEF_WIDTH = 0.6F;

//  private static final int SCALE_INDEX = 20;
//  private static final int GROWTH_MODE_INDEX = 21;
  private static final DataParameter<Float> SCALE_INDEX = EntityDataManager.<Float>createKey(EntityWitherCat.class, DataSerializers.FLOAT);
  private static final DataParameter<Integer> GROWTH_MODE_INDEX = EntityDataManager.<Integer>createKey(EntityWitherCat.class, DataSerializers.VARINT);

  private static final float ANGRY_SCALE = 2;
  private static final float SCALE_INC = 0.05f;

  private static final UUID ATTACK_BOOST_MOD_UID = UUID.fromString("B9662B59-9566-4402-BC1F-2ED2B276D846");
  private static final UUID HEALTH_BOOST_MOD_UID = UUID.fromString("B9662B29-9467-3302-1D1A-2ED2B276D846");

  private float lastScale = 1f;
  private EntityWitherWitch owner;
  private EntityAIFollowOwner followTask;

  private boolean attackTargetChanged = false;

  public EntityWitherCat(World world) {
    super(world);

    followTask = new EntityAIFollowOwner(this, 2.5, 5, 1);
    EntityAIFollowOwner retreatTask = new EntityAIFollowOwner(this, 2.5, 5, 2.5);

    tasks.addTask(1, new EntityAISwimming(this));
    tasks.addTask(2, new EntityAIAttackOnCollideOwned(this, EntityPlayer.class, 2.5, false, retreatTask));
    tasks.addTask(3, followTask);
    tasks.addTask(4, new EntityAIWander(this, 1.0D));
    tasks.addTask(5, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
    tasks.addTask(6, new EntityAILookIdle(this));
    tasks.addTask(7, new EntityAIAttackMelee(this, 1.0D, true));

    setSize(DEF_WIDTH, DEF_HEIGHT);
  }

  @Override
  protected void entityInit() {
    super.entityInit();

    dataWatcher.register(SCALE_INDEX, Float.valueOf(1));
    dataWatcher.register(GROWTH_MODE_INDEX, Integer.valueOf(GrowthMode.NONE.ordinal()));
  }

  @Override
  public EntityWitherWitch getOwner() {
    return owner;
  }

  @Override
  public void setOwner(EntityWitherWitch owner) {
    this.owner = owner;
  }

  @Override
  public EntityWitherCat asEntity() {
    return this;
  }

  public void setScale(float scale) {
    dataWatcher.set(SCALE_INDEX, scale);
  }

  public float getScale() {
    return dataWatcher.get(SCALE_INDEX);
  }

  public void setGrowthMode(GrowthMode mode) {
    setGrowthMode(mode.ordinal());
  }

  private void setGrowthMode(int ordinal) {
    dataWatcher.set(GROWTH_MODE_INDEX, ordinal);
  }

  public GrowthMode getGrowthMode() {
    return GrowthMode.values()[dataWatcher.get(GROWTH_MODE_INDEX)];
  }

  public float getAngryScale() {
    return ANGRY_SCALE;
  }

  public float getScaleInc() {
    return SCALE_INC;
  }

  public boolean isAngry() {
    return getScale() >= ANGRY_SCALE;
  }

  @Override
  public void setAttackTarget(EntityLivingBase target) {
    if (getAttackTarget() != target) {
      attackTargetChanged = true;
    }
    super.setAttackTarget(target);
  }

  @Override
  protected void applyEntityAttributes() {
    super.applyEntityAttributes();
    getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25D);
    MobInfo.WITHER_CAT.applyAttributes(this);
  }

  @Override
  public boolean isPotionApplicable(PotionEffect potion) {
    return potion.getPotion() != MobEffects.wither && super.isPotionApplicable(potion);
  }

  @Override
  public boolean attackEntityFrom(DamageSource source, float amount) {
    if (owner != null && source.getEntity() == owner) {
      return false;
    }
    boolean res = super.attackEntityFrom(source, amount);
    if (!worldObj.isRemote) {
      if (source.getEntity() instanceof EntityLivingBase) {
        if (owner != null) {
          EntityLivingBase ownerHitBy = owner.getAITarget();
          if (ownerHitBy == null) {
            owner.setRevengeTarget((EntityLivingBase) source.getEntity());
          }
        } else if (owner == null) {
          setAttackTarget((EntityLivingBase) source.getEntity());
        }
      }
    }
    return res;
  }

  @Override
  public void setDead() {
    super.setDead();
    if (owner != null) {
      owner.catDied(this);
    }
  }

  @Override
  public void onLivingUpdate() {
    super.onLivingUpdate();
    if (worldObj.isRemote) {
      float scale = getScale();
      if (lastScale != scale) {
        spawnParticles();
        lastScale = scale;
        setSize(DEF_WIDTH * scale, DEF_HEIGHT * scale);
      }
      return;
    }

    if (!worldObj.isRemote && attackTargetChanged) {
      EntityUtil.cancelCurrentTasks(this);
      tasks.removeTask(followTask);
      if (getAttackTarget() == null) {
        tasks.addTask(3, followTask);
      }
      attackTargetChanged = false;
    }

    if (owner != null && owner.isDead) {
      setOwner(null);
    }
    if (/* getOwner() != null && */getAttackTarget() != null && !isAngry() && getGrowthMode() != GrowthMode.GROW) {
      setGrowthMode(GrowthMode.GROW);
    }

    updateScale();

    float scale = getScale();
    if (lastScale != scale) {
      lastScale = scale;
      setSize(DEF_WIDTH * scale, DEF_HEIGHT * scale);
      float growthRatio = (lastScale - 1) / (ANGRY_SCALE - 1);
      updateAttackDamage(growthRatio);
      updateHealth(growthRatio);
    }
  }

  public void updateScale() {
    GrowthMode curMode = getGrowthMode();
    if (curMode == GrowthMode.NONE) {
      return;
    }

    float scale = getScale();
    if (curMode == GrowthMode.GROW) {
      if (scale < ANGRY_SCALE) {
        setScale(Math.min(scale + SCALE_INC, ANGRY_SCALE));
      } else {
        setScale(ANGRY_SCALE);
        setGrowthMode(GrowthMode.NONE);
      }
    } else {
      if (scale > 1) {
        setScale(Math.max(1, scale - SCALE_INC));
      } else {
        setScale(1);
        setGrowthMode(GrowthMode.NONE);
      }
    }
  }

  protected void updateAttackDamage(float growthRatio) {
    IAttributeInstance att = EntityUtil.removeModifier(this, SharedMonsterAttributes.ATTACK_DAMAGE, ATTACK_BOOST_MOD_UID);
    if (growthRatio == 0) {
      return;
    }
    double damageInc = EntityUtil.isHardDifficulty(worldObj) ? Config.witherCatAngryAttackDamageHardModifier : 0;
    double attackDif = (damageInc + Config.witherCatAngryAttackDamage) - Config.witherCatAttackDamage;
    double toAdd = attackDif * growthRatio;
    AttributeModifier mod = new AttributeModifier(ATTACK_BOOST_MOD_UID, "Transformed Attack Modifier", toAdd, 0);
    att.applyModifier(mod);
  }

  protected void updateHealth(float growthRatio) {
    IAttributeInstance att = EntityUtil.removeModifier(this, SharedMonsterAttributes.MAX_HEALTH, HEALTH_BOOST_MOD_UID);
    if (growthRatio == 0) {
      return;
    }
    double currentRatio = getHealth() / getMaxHealth();
    double healthDif = Config.witherCatAngryHealth - Config.witherCatHealth;
    double toAdd = healthDif * growthRatio;
    AttributeModifier mod = new AttributeModifier(HEALTH_BOOST_MOD_UID, "Transformed Attack Modifier", toAdd, 0);
    att.applyModifier(mod);

    double newHealth = currentRatio * getMaxHealth();
    setHealth((float) newHealth);

  }

  private void spawnParticles() {
    double startX = posX;
    double startY = posY;
    double startZ = posZ;
    double offsetScale = 0.8 * getScale();
    for (int i = 0; i < 2; i++) {
      double xOffset = offsetScale - rand.nextFloat() * offsetScale * 2;
      double yOffset = offsetScale / 3 + rand.nextFloat() * offsetScale / 3 * 2F;
      double zOffset = offsetScale - rand.nextFloat() * offsetScale * 2;
      EntityFX fx = Minecraft.getMinecraft().effectRenderer.spawnEffectParticle(EnumParticleTypes.SPELL.getParticleID(), startX + xOffset, startY + yOffset,
          startZ + zOffset, 0.0D, 0.0D, 0.0D);
      if (fx != null) {
        fx.setRBGColorF(0.8f, 0.2f, 0.2f);
        // TODO: 1.9
        // fx.motionY *= 0.025f;
      }
    }
  }

  @Override
  public void setPosition(double x, double y, double z) {
    posX = x;
    posY = y;
    posZ = z;
    updateBounds();
  }

  @Override
  protected boolean isValidLightLevel() {
    return true;
  }

  @Override
  protected void setSize(float width, float height) {
    if (width != this.width || height != this.height) {
      this.width = width;
      this.height = height;
      updateBounds();
    }
  }

  private void updateBounds() {
    double hw = width / 2.0F;
    double hd = hw * 2.75;
    float f1 = height;
    setEntityBoundingBox(new AxisAlignedBB(posX - hw, posY, posZ - hd, posX + hw, posY + f1, posZ + hd));
  }

  // TODO: New sounds
  @Override
  protected SoundEvent getAmbientSound() {
    return SoundEvents.entity_cat_ambient;
  }

  @Override
  protected SoundEvent getHurtSound() {
    return SoundEvents.entity_cat_hurt;
  }

  @Override
  protected SoundEvent getDeathSound() {
    return SoundEvents.entity_cat_death;
  }

  @Override
  public boolean writeToNBTOptional(NBTTagCompound root) {
    if (getOwner() == null) {
      return super.writeToNBTOptional(root);
    }
    return false;
  }

  @Override
  public void writeEntityToNBT(NBTTagCompound root) {
    super.writeEntityToNBT(root);
    root.setFloat("scale", getScale());
    root.setByte("growthMode", (byte) getGrowthMode().ordinal());
  }

  @Override
  public void readEntityFromNBT(NBTTagCompound root) {
    super.readEntityFromNBT(root);
    if (root.hasKey("scale")) {
      setScale(root.getFloat("scale"));
    }
    if (root.hasKey("growthMode")) {
      setGrowthMode(root.getByte("growthMode"));
    }
  }

}
