package com.rostroshuecos;

import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

public final class ModItems {
    private ModItems() {}

    public static final Item NOTA_ROTA = item("nota_rota", NotaRotaItem::new, new Item.Settings().maxCount(16));
    public static final Item CHICA_ROTA_EGG = item("chica_rota_spawn_egg",
            s -> new SpawnEggItem(ModEntities.CHICA_ROTA, s), new Item.Settings());
    public static final Item NINO_GRIS_EGG = item("nino_gris_spawn_egg",
            s -> new SpawnEggItem(ModEntities.NINO_GRIS, s), new Item.Settings());
    public static final Item SOMBRA_HUECA_EGG = item("sombra_hueca_spawn_egg",
            s -> new SpawnEggItem(ModEntities.SOMBRA_HUECA, s), new Item.Settings());
    public static final Item MUNECO_PERDIDO_EGG = item("muneco_perdido_spawn_egg",
            s -> new SpawnEggItem(ModEntities.MUNECO_PERDIDO, s), new Item.Settings());

    private static Item item(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, RostrosHuecos.id(name));
        return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
    }

    public static void register() {
        ItemGroup group = FabricItemGroup.builder()
                .icon(() -> new ItemStack(NOTA_ROTA))
                .displayName(Text.translatable("itemGroup.rostroshuecos"))
                .entries((ctx, entries) -> {
                    entries.add(NOTA_ROTA);
                    entries.add(CHICA_ROTA_EGG);
                    entries.add(NINO_GRIS_EGG);
                    entries.add(SOMBRA_HUECA_EGG);
                    entries.add(MUNECO_PERDIDO_EGG);
                })
                .build();
        Registry.register(Registries.ITEM_GROUP, RegistryKey.of(RegistryKeys.ITEM_GROUP, RostrosHuecos.id("main")), group);
    }
}
