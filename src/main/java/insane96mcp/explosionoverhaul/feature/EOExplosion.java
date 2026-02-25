package insane96mcp.explosionoverhaul.feature;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import insane96mcp.explosionoverhaul.mixin.accessor.ExplosionAccessor;
import insane96mcp.explosionoverhaul.mixin.accessor.LevelAccessor;
import insane96mcp.insanelib.module.base.betterfallingblocks.BetterFallingBlockExtensor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class EOExplosion extends Explosion {
	ObjectArrayList<Pair<ItemStack, BlockPos>> droppedItems = new ObjectArrayList<>();
	boolean creeperCollateral;
	public final boolean poofParticles;
	private float baseResistanceAdd = 0.3f;
	private float rayStrengthMultiplier = 0.3f;

	private List<Entity> affectedEntities = new ArrayList<>();

	public EOExplosion(Level level, @Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float radius, boolean fire, BlockInteraction blockInteraction, ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles, Holder<SoundEvent> explosionSound, boolean creeperCollateral, boolean poofParticles) {
		super(
				level,
				source,
				damageSource,
				damageCalculator,
				x, y, z,
				BaseFeature.limitExplosionSize != 0 ? Math.min(BaseFeature.limitExplosionSize, radius) : radius,
				fire,
				blockInteraction,
				smallExplosionParticles,
				largeExplosionParticles,
				explosionSound
		);
		this.creeperCollateral = creeperCollateral;
		this.poofParticles = poofParticles;
		this.baseResistanceAdd = BaseFeature.getBaseResistanceAdd(source);
		this.rayStrengthMultiplier = Math.max(0.01f, BaseFeature.getRayStrengthMultiplier(source));
	}

	public void gatherAffectedBlocks(boolean randomize) {
		Vec3 explosionCenter = this.center();
		Set<BlockPos> set = Sets.newHashSet();
		for(int j = 0; j < 16; ++j) {
			for(int k = 0; k < 16; ++k) {
				for(int l = 0; l < 16; ++l) {
					if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
						double d0 = (float)j / 15.0F * 2.0F - 1.0F;
						double d1 = (float)k / 15.0F * 2.0F - 1.0F;
						double d2 = (float)l / 15.0F * 2.0F - 1.0F;
						double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
						d0 = d0 / d3;
						d1 = d1 / d3;
						d2 = d2 / d3;
						float rayStrength;
						if (!randomize)
							rayStrength = this.radius();
						else
							rayStrength = this.radius() * (0.7F + ((ExplosionAccessor) this).getLevel().getRandom().nextFloat() * 0.6F);
						double x = explosionCenter.x;
						double y = explosionCenter.y;
						double z = explosionCenter.z;
						for (;rayStrength > 0.0F; rayStrength -= 0.22500001F) {
							BlockPos blockpos = BlockPos.containing(x, y, z);
							BlockState blockstate = ((ExplosionAccessor) this).getLevel().getBlockState(blockpos);
							FluidState fluidstate = ((ExplosionAccessor) this).getLevel().getFluidState(blockpos);
							Optional<Float> optional = ((ExplosionAccessor) this).getDamageCalculator().getBlockExplosionResistance(this, ((ExplosionAccessor) this).getLevel(), blockpos, blockstate, fluidstate);
							if (optional.isPresent()) {
								float resistance = optional.get();
								rayStrength -= (resistance + baseResistanceAdd) * rayStrengthMultiplier;
							}
							if (rayStrength > 0.0F && ((ExplosionAccessor) this).getDamageCalculator().shouldBlockExplode(this, ((ExplosionAccessor) this).getLevel(), blockpos, blockstate, rayStrength))
								set.add(blockpos);
							x += d0 * (double) 0.3F;
							y += d1 * (double) 0.3F;
							z += d2 * (double) 0.3F;
						}
					}
				}
			}
		}
		this.getToBlow().addAll(set);
		float affectedEntitiesRadius = this.radius() * 2.0F;
		gatherAffectedEntities(affectedEntitiesRadius);
		EventHooks.onExplosionDetonate(((ExplosionAccessor) this).getLevel(), this, this.affectedEntities, affectedEntitiesRadius);

	}

	public void fallingBlocks() {
		if (!this.interactsWithBlocks())
			return;
		List<BlockPos> toClear = new ArrayList<>();
		for (BlockPos blockpos : this.getToBlow()) {
			BlockState blockstate = ((ExplosionAccessor) this).getLevel().getBlockState(blockpos);
			Block block = blockstate.getBlock();
			if (blockstate.isAir() || blockstate.is(BaseFeature.FLYING_BLOCKS_EXPLOSION_BLACKLIST))
				continue;
			if (block instanceof MovingPistonBlock) {
				PistonMovingBlockEntity tileEntity = (PistonMovingBlockEntity) ((ExplosionAccessor) this).getLevel().getBlockEntity(blockpos);
				blockstate = tileEntity.getMovedState();
				block = blockstate.getBlock();
			}
			block.wasExploded(((ExplosionAccessor) this).getLevel(), BlockPos.containing(this.center()), this);
			BlockPos blockpos1 = blockpos.immutable();
			((ExplosionAccessor) this).getLevel().setBlockAndUpdate(blockpos1, Blocks.AIR.defaultBlockState());
			FallingBlockEntity fallingBlockEntity = FallingBlockEntity.fall(((ExplosionAccessor) this).getLevel(), blockpos1.above(2), blockstate);
			this.affectedEntities.add(fallingBlockEntity);
            fallingBlockEntity.time = 1;
            ((BetterFallingBlockExtensor)fallingBlockEntity).insanelib$setSource(((ExplosionAccessor) this).getSource());
			toClear.add(blockpos);
		}
		this.getToBlow().removeAll(toClear);
	}

	public void processEntities() {
		Vec3 explosionCenter = this.center();
		float affectedEntitiesRadius = this.radius() * 2.0F;
		for (Entity entity : this.affectedEntities) {
			if (entity.tickCount == 0 && !(entity instanceof PartEntity<?>)  && !(entity instanceof FallingBlockEntity))
				continue;
			if (entity.ignoreExplosion(this))
				continue;
			double distanceRatio = Mth.sqrt((float) entity.distanceToSqr(explosionCenter)) / affectedEntitiesRadius;
			if (distanceRatio > 1.0D)
				continue;
			double xDistance = entity.getX() - explosionCenter.x;
			double yDistance = (entity.getEyeY() - explosionCenter.y);// * 0.6667d;
			double zDistance = entity.getZ() - explosionCenter.z;
			double d13 = Mth.sqrt((float) (xDistance * xDistance + yDistance * yDistance + zDistance * zDistance));
			if (d13 == 0.00)
				continue;
			//xDistance = xDistance / d13;
			if (!(entity instanceof FallingBlockEntity))
				yDistance = yDistance / d13;
			//zDistance = zDistance / d13;
			double blockDensity = getSeenPercent(explosionCenter, entity);
			double d10 = (1.0D - distanceRatio) * blockDensity;
			//Damage Entities in the explosion radius
			//float damageAmount = (float) ((int) ((d10 * d10 + d10) / 2.0D * BaseFeature.explosionDamageCalculationMultiplier * (double) affectedEntitiesRadius + 1.0D));
			//damageAmount *= BaseFeature.getDamageMultiplier(((ExplosionAccessor) this).getSource());
			if (blockDensity > 0d) {
				DamageSource source = ((ExplosionAccessor) this).getDamageSource();
				boolean isBlocking = false;
				if (entity instanceof LivingEntity living)
					isBlocking = living.isDamageSourceBlocked(source) && living.isBlocking();
				boolean isLiving = entity instanceof LivingEntity;
				boolean hasHurt = false;
				boolean shouldDamageEntity = ((ExplosionAccessor) this).getDamageCalculator().shouldDamageEntity(this, entity);
				if (shouldDamageEntity) {
					float vanillaDamage = ((ExplosionAccessor) this).getDamageCalculator().getEntityDamageAmount(this, entity);
					float modifiedDamage = vanillaDamage * BaseFeature.getDamageMultiplier(((ExplosionAccessor) this).getSource());
					hasHurt = entity.hurt(((ExplosionAccessor) this).getDamageSource(), modifiedDamage);
				}
				if (hasHurt || !shouldDamageEntity || isBlocking || !isLiving) {
					double d11 = d10;
					if (isLiving)
						d11 = d11 * (1.0 - ((LivingEntity) entity).getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
                    if (((ExplosionAccessor) this).getDamageSource().getDirectEntity() instanceof LivingEntity directExploder && BaseFeature.applyEffectFromExploder && isLiving) {
                        directExploder.getActiveEffects().forEach(effect -> ((LivingEntity) entity).addEffect(new MobEffectInstance(effect.getEffect(), (int) (effect.getDuration() * d10), effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon())));
                    }
					d11 *= ((ExplosionAccessor) this).getDamageCalculator().getKnockbackMultiplier(entity);
					if (BaseFeature.knockbackScalesWithSize)
						d11 *= this.radius();
					d11 = Math.max(d11, this.radius() * 0.05d);
 					if (entity instanceof FallingBlockEntity || BaseFeature.shouldTakeReducedKnockback(entity))
						d11 *= 0.2d;
					d11 *= BaseFeature.getKnockbackMultiplier(((ExplosionAccessor) this).getSource());
					d11 = Math.min(d11, 10f);
					if (entity instanceof FallingBlockEntity) {
						d11 = Math.min(d11, 0.35f);
						xDistance += ((ExplosionAccessor) this).getLevel().getRandom().nextFloat() - 0.5f;
						zDistance += ((ExplosionAccessor) this).getLevel().getRandom().nextFloat() - 0.5f;
					}
					double y = yDistance * d11;
					if (isLiving)
						y = Math.max(y, 0.1f * this.radius());
					entity.setDeltaMovement(entity.getDeltaMovement().add(xDistance * d11, y, zDistance * d11));
					entity.onExplosionHit(((ExplosionAccessor) this).getSource());
					if (entity instanceof Player player) {
						if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
							this.getHitPlayers().put(player, new Vec3(xDistance * d11, Math.max(yDistance * d11, 0.1f * this.radius()), zDistance * d11));
						}
					}
				}
			}
		}
	}

	public void destroyBlocks() {
		((ExplosionAccessor) this).getLevel().gameEvent(((ExplosionAccessor) this).getSource(), GameEvent.EXPLODE, this.center());
		if (!this.interactsWithBlocks())
			return;

		Util.shuffle(this.getToBlow(), ((ExplosionAccessor) this).getLevel().getRandom());

		for(BlockPos blockpos : this.getToBlow()) {
			BlockState blockstate = ((ExplosionAccessor) this).getLevel().getBlockState(blockpos);

			if (!blockstate.isAir()) {
				((ExplosionAccessor) this).getLevel().getProfiler().push("explosion_blocks");

				if (!doesCreeperCollateralApply()) {
					blockstate.onExplosionHit(((ExplosionAccessor) this).getLevel(), blockpos, this,
							(stack, pos) -> addBlockDrops(droppedItems, stack, pos));
				} else {
					blockstate.onBlockExploded(((ExplosionAccessor) this).getLevel(), blockpos, this);
				}

				FluidState fluidState = ((ExplosionAccessor) this).getLevel().getFluidState(blockpos);
				if (!fluidState.isEmpty()) {
					((ExplosionAccessor) this).getLevel().setBlock(blockpos, fluidState.createLegacyBlock(), 3);
				}

				((ExplosionAccessor) this).getLevel().getProfiler().pop();
			}
		}
	}

	public static void addBlockDrops(ObjectArrayList<Pair<ItemStack, BlockPos>> pDropPositionArray, ItemStack stack, BlockPos pos) {
		int pairCount = pDropPositionArray.size();

		if (!BaseFeature.dontStackDrops) {
			for (int i = 0; i < pairCount; ++i) {
				Pair<ItemStack, BlockPos> pair = pDropPositionArray.get(i);
				ItemStack pairStack = pair.getFirst();
				if (ItemEntity.areMergable(pairStack, stack)) {
					ItemStack mergedStack = ItemEntity.merge(pairStack, stack, 16);
					pDropPositionArray.set(i, Pair.of(mergedStack, pair.getSecond()));
					if (stack.isEmpty())
						return;
				}
			}
		}

		pDropPositionArray.add(Pair.of(stack, pos));
	}

	public boolean doesCreeperCollateralApply() {
		return this.creeperCollateral && this.getDirectSourceEntity() instanceof Creeper;
	}

	public void dropItems() {
		for(Pair<ItemStack, BlockPos> pair : droppedItems) {
			Block.popResource(((ExplosionAccessor) this).getLevel(), pair.getSecond(), pair.getFirst());
		}
	}

	public void processFire() {
		if (((ExplosionAccessor) this).getFire()) {
			for(BlockPos blockPos : this.getToBlow()) {
				if (((ExplosionAccessor) this).getRandom().nextInt(3) == 0 && ((ExplosionAccessor) this).getLevel().getBlockState(blockPos).isAir() && ((ExplosionAccessor) this).getLevel().getBlockState(blockPos.below()).isSolidRender(((ExplosionAccessor) this).getLevel(), blockPos.below())) {
					((ExplosionAccessor) this).getLevel().setBlockAndUpdate(blockPos, BaseFireBlock.getState(((ExplosionAccessor) this).getLevel(), blockPos));
				}
			}
		}
	}

	private void gatherAffectedEntities(float affectedRadius) {
		Vec3 explosionCenter = this.center();
		int x1 = Mth.floor(explosionCenter.x - (double)affectedRadius - 1.0D);
		int x2 = Mth.floor(explosionCenter.x + (double)affectedRadius + 1.0D);
		int y1 = Mth.floor(explosionCenter.y - (double)affectedRadius - 1.0D);
		int y2 = Mth.floor(explosionCenter.y + (double)affectedRadius + 1.0D);
		int z1 = Mth.floor(explosionCenter.z - (double)affectedRadius - 1.0D);
		int z2 = Mth.floor(explosionCenter.z + (double)affectedRadius + 1.0D);
		this.affectedEntities = ((ExplosionAccessor) this).getLevel().getEntities(((ExplosionAccessor) this).getSource(), new AABB(x1, y1, z1, x2, y2, z2));
	}

	@Nullable
	public static EOExplosion explode(Level level, @Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction explosionInteraction, ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles, Holder<SoundEvent> explosionSound, boolean poofParticles) {
		if (!(level instanceof ServerLevel serverLevel))
			return null;
		BlockInteraction blockInteraction = switch (explosionInteraction) {
			case NONE -> Explosion.BlockInteraction.KEEP;
			case BLOCK -> ((LevelAccessor) level).invokeGetDestroyType(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
			case MOB -> net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, source)
					? ((LevelAccessor) level).invokeGetDestroyType(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY)
					: Explosion.BlockInteraction.KEEP;
			case TNT -> ((LevelAccessor) level).invokeGetDestroyType(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
			case TRIGGER -> Explosion.BlockInteraction.TRIGGER_BLOCK;
		};
		return explode(serverLevel, source, damageSource, damageCalculator, x, y, z, radius, fire, blockInteraction, smallExplosionParticles, largeExplosionParticles, explosionSound, poofParticles);
	}

	public static EOExplosion explode(ServerLevel level, @Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float radius, boolean fire, BlockInteraction blockInteraction, ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles, Holder<SoundEvent> explosionSound, boolean poofParticles) {
		EOExplosion explosion = new EOExplosion(level, source, damageSource, damageCalculator, x, y, z, radius, fire, blockInteraction, smallExplosionParticles, largeExplosionParticles, explosionSound, BaseFeature.creeperCollateral, poofParticles);
		//if (ISOEventFactory.onITRExplosionCreated(explosion)) return explosion;
		if (level.getGameRules().getBoolean(BaseFeature.RULE_MOBGRIEFING))
			explosion.gatherAffectedBlocks(!BaseFeature.disableExplosionRandomness);
		if (BaseFeature.enableFlyingBlocks)
			explosion.fallingBlocks();
		explosion.destroyBlocks();
		explosion.processEntities();
		explosion.dropItems();
		explosion.processFire();
		if (explosion.getBlockInteraction() == BlockInteraction.KEEP) {
			explosion.clearToBlow();
		}
		for (ServerPlayer serverPlayer : level.players()) {
			if (serverPlayer.distanceToSqr(explosion.center().x, explosion.center().y, explosion.center().z) < 4096.0D) {
				serverPlayer.connection.send(new ClientboundExplodePacket(
						explosion.center().x,
						explosion.center().y,
						explosion.center().z,
						explosion.radius(),
						explosion.getToBlow(),
						explosion.getHitPlayers().get(serverPlayer),
						explosion.getBlockInteraction(),
						explosion.getSmallExplosionParticles(),
						explosion.getLargeExplosionParticles(),
						explosion.getExplosionSound()
				));
			}
		}
		return explosion;
	}
}
