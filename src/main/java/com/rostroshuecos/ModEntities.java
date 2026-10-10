package com.rostroshuecos;

import com.rostroshuecos.entity.ChicaRotaEntity;
import com.rostroshuecos.entity.MunecoPerdidoEntity;
import com.rostroshuecos.entity.NinoGrisEntity;
import com.rostroshuecos.entity.SombraHuecaEntity;
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

public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<ChicaRotaEntity> CHICA_ROTA = reg("chica_rota",
            EntityType.Builder.create(ChicaRotaEntity::new, SpawnGroup.MONSTER).dimensions(0.7F, 2.1F).maxTrackingRange(10));
    public static final EntityType<SombraHuecaEntity> SOMBRA_HUECA = reg("sombra_hueca",
            EntityType.Builder.create(SombraHuecaEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 2.4F).maxTrackingRange(10));
    public static final EntityType<NinoGrisEntity> NINO_GRIS = reg("nino_gris",
            EntityType.Builder.create(NinoGrisEntity::new, SpawnGroup.MONSTER).dimensions(0.5F, 1.4F).maxTrackingRange(10));
    public static final EntityType<MunecoPerdidoEntity> MUNECO_PERDIDO = reg("muneco_perdido",
            EntityType.Builder.create(MunecoPerdidoEntity::new, SpawnGroup.MONSTER).dimensions(0.4F, 1.0F).maxTrackingRange(10));

    private static <T extends Entity> EntityType<T> reg(String name, EntityType.Builder<T> builder) {
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, RostrosHuecos.id(name));
        return Registry.register(Registries.ENTITY_TYPE, key, builder.build(key));
    }

    /** Solo aparecen con poca luz (y la Pollita y la Sombra no en modo pacifico). */
    private static boolean darkSpawn(EntityType<? extends MobEntity> type, ServerWorldAccess world,
                                     SpawnReason reason, BlockPos pos, Random random) {
        return world.getLightLevel(pos) <= 7 && MobEntity.canMobSpawn(type, world, reason, pos, random);
    }

    private static boolean darkSpawnHostile(EntityType<? extends MobEntity> type, ServerWorldAccess world,
                                            SpawnReason reason, BlockPos pos, Random random) {
        return world.getDifficulty() != Difficulty.PEACEFUL && darkSpawn(type, world, reason, pos, random);
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(CHICA_ROTA, ChicaRotaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SOMBRA_HUECA, SombraHuecaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(NINO_GRIS, NinoGrisEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MUNECO_PERDIDO, MunecoPerdidoEntity.createAttributes());

        SpawnRestriction.register(CHICA_ROTA, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ModEntities::darkSpawnHostile);
        SpawnRestriction.register(SOMBRA_HUECA, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ModEntities::darkSpawnHostile);
        SpawnRestriction.register(NINO_GRIS, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ModEntities::darkSpawn);
        SpawnRestriction.register(MUNECO_PERDIDO, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ModEntities::darkSpawn);

        // peso, grupo minimo, grupo maximo (los vanilla como Zombie tienen peso 100)
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, CHICA_ROTA, 8, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, SOMBRA_HUECA, 6, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, NINO_GRIS, 5, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, MUNECO_PERDIDO, 5, 1, 1);
    }
}
