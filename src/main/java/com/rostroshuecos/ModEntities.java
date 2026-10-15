package com.rostroshuecos;

import com.rostroshuecos.entity.DobleBocaEntity;
import com.rostroshuecos.entity.HuecoEntity;
import com.rostroshuecos.entity.MandibulaEntity;
import com.rostroshuecos.entity.RaizEntity;
import com.rostroshuecos.entity.SanMudoEntity;
import com.rostroshuecos.entity.SombraHuecaEntity;
import com.rostroshuecos.entity.SudarioEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.biome.BiomeKeys;

public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<SombraHuecaEntity> SOMBRA_HUECA = reg("sombra_hueca",
            EntityType.Builder.create(SombraHuecaEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 2.9F).maxTrackingRange(10));
    public static final EntityType<RaizEntity> RAIZ = reg("raiz",
            EntityType.Builder.create(RaizEntity::new, SpawnGroup.MONSTER).dimensions(0.9F, 2.6F).maxTrackingRange(10));
    public static final EntityType<SudarioEntity> SUDARIO = reg("sudario",
            EntityType.Builder.create(SudarioEntity::new, SpawnGroup.MONSTER).dimensions(0.7F, 2.8F).maxTrackingRange(10));
    public static final EntityType<HuecoEntity> HUECO = reg("hueco",
            EntityType.Builder.create(HuecoEntity::new, SpawnGroup.MONSTER).dimensions(1.0F, 1.5F).maxTrackingRange(10));
    public static final EntityType<MandibulaEntity> MANDIBULA = reg("mandibula",
            EntityType.Builder.create(MandibulaEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 2.2F).maxTrackingRange(10));
    public static final EntityType<DobleBocaEntity> DOBLE_BOCA = reg("doble_boca",
            EntityType.Builder.create(DobleBocaEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 2.0F).maxTrackingRange(10));

    public static final EntityType<SanMudoEntity> SAN_MUDO = reg("san_mudo",
            EntityType.Builder.create(SanMudoEntity::new, SpawnGroup.MONSTER).dimensions(0.7F, 3.0F).maxTrackingRange(10));

    /** Tipo de monstruo segun su indice (0 Sombra, 1 Raiz, 2 Sudario, 3 Hueco, 4 Mandibula, 5 Doble Boca, 6 San Mudo). */
    public static EntityType<? extends MobEntity> byIndex(int i) {
        return switch (i) {
            case 0 -> SOMBRA_HUECA;
            case 1 -> RAIZ;
            case 2 -> SUDARIO;
            case 3 -> HUECO;
            case 4 -> MANDIBULA;
            case 5 -> DOBLE_BOCA;
            default -> SAN_MUDO;
        };
    }

    private static <T extends Entity> EntityType<T> reg(String name, EntityType.Builder<T> builder) {
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, RostrosHuecos.id(name));
        return Registry.register(Registries.ENTITY_TYPE, key, builder.build(key));
    }

    /** Solo con poca luz, fuera del modo pacifico, y solo si no existe ya otra de ese tipo. */
    private static boolean darkSpawn(EntityType<? extends MobEntity> type, ServerWorldAccess world,
                                     SpawnReason reason, BlockPos pos, Random random) {
        return world.getDifficulty() != Difficulty.PEACEFUL
                && world.getLightLevel(pos) <= 7
                && !HorrorUtil.exists(world.toServerWorld(), type)
                && !HorrorUtil.isDefeatedType(type)
                && MobEntity.canMobSpawn(type, world, reason, pos, random);
    }

    private static <T extends MobEntity> void restrict(EntityType<T> type) {
        SpawnRestriction.register(type, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ModEntities::darkSpawn);
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(SOMBRA_HUECA, SombraHuecaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RAIZ, RaizEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SUDARIO, SudarioEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(HUECO, HuecoEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MANDIBULA, MandibulaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(DOBLE_BOCA, DobleBocaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SAN_MUDO, SanMudoEntity.createAttributes());

        restrict(SOMBRA_HUECA);
        restrict(RAIZ);
        restrict(SUDARIO);
        restrict(HUECO);
        restrict(MANDIBULA);
        restrict(DOBLE_BOCA);

        // peso, grupo minimo, grupo maximo (los vanilla como Zombie tienen peso 100)
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, SOMBRA_HUECA, 6, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, SUDARIO, 5, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, HUECO, 4, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, MANDIBULA, 5, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, DOBLE_BOCA, 5, 1, 1);
        // La Raiz solo en bosques
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.FOREST, BiomeKeys.DARK_FOREST,
                BiomeKeys.BIRCH_FOREST, BiomeKeys.OLD_GROWTH_BIRCH_FOREST, BiomeKeys.FLOWER_FOREST,
                BiomeKeys.TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA),
                SpawnGroup.MONSTER, RAIZ, 10, 1, 1);
    }
}
