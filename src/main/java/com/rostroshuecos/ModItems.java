package com.rostroshuecos;

import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

public final class ModItems {
    private ModItems() {}

    public static final Item NOTA_ROTA = item("nota_rota", NotaRotaItem::new, new Item.Settings().maxCount(16));
    public static final Item DIARIO = item("diario", DiarioItem::new, new Item.Settings().maxCount(1));
    public static final Item VELA = item("vela", Item::new, new Item.Settings().maxDamage(60));
    public static final Item SELLO = item("sello", Item::new, new Item.Settings().maxCount(1).rarity(Rarity.EPIC));
    public static final Item[] FRAGMENTOS = new Item[6];
    static {
        String[] ids = {"sombra", "raiz", "sudario", "hueco", "mandibula", "doble_boca"};
        for (int i = 0; i < ids.length; i++) {
            FRAGMENTOS[i] = item("fragmento_" + ids[i], Item::new, new Item.Settings().maxCount(16));
        }
    }
    public static final Item SOMBRA_HUECA_EGG = egg("sombra_hueca_spawn_egg", ModEntities.SOMBRA_HUECA);
    public static final Item RAIZ_EGG = egg("raiz_spawn_egg", ModEntities.RAIZ);
    public static final Item SUDARIO_EGG = egg("sudario_spawn_egg", ModEntities.SUDARIO);
    public static final Item HUECO_EGG = egg("hueco_spawn_egg", ModEntities.HUECO);
    public static final Item MANDIBULA_EGG = egg("mandibula_spawn_egg", ModEntities.MANDIBULA);
    public static final Item DOBLE_BOCA_EGG = egg("doble_boca_spawn_egg", ModEntities.DOBLE_BOCA);

    private static Item egg(String name, EntityType<? extends MobEntity> type) {
        return item(name, s -> new SpawnEggItem(type, s), new Item.Settings());
    }

    private static Item item(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, RostrosHuecos.id(name));
        return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
    }

    public static void register() {
        ItemGroup group = FabricItemGroup.builder()
                .icon(() -> new ItemStack(DIARIO))
                .displayName(Text.translatable("itemGroup.rostroshuecos"))
                .entries((ctx, entries) -> {
                    entries.add(DIARIO);
                    entries.add(NOTA_ROTA);
                    entries.add(VELA);
                    entries.add(SELLO);
                    for (Item fr : FRAGMENTOS) entries.add(fr);
                    entries.add(SOMBRA_HUECA_EGG);
                    entries.add(RAIZ_EGG);
                    entries.add(SUDARIO_EGG);
                    entries.add(HUECO_EGG);
                    entries.add(MANDIBULA_EGG);
                    entries.add(DOBLE_BOCA_EGG);
                })
                .build();
        Registry.register(Registries.ITEM_GROUP, RegistryKey.of(RegistryKeys.ITEM_GROUP, RostrosHuecos.id("main")), group);
    }
}
