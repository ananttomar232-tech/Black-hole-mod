package com.ananttomar.livingblackhole.registry;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.item.AntiGravityBombItem;
import com.ananttomar.livingblackhole.item.CompressedChunkItem;
import com.ananttomar.livingblackhole.item.CoreFragmentItem;
import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LivingBlackHole.MODID);

    public static final RegistryObject<Item> POCKET_BLACK_HOLE = ITEMS.register("pocket_black_hole",
            () -> new PocketBlackHoleItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> COMPRESSED_CHUNK = ITEMS.register("compressed_chunk",
            () -> new CompressedChunkItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    public static final RegistryObject<Item> CORE_FRAGMENT = ITEMS.register("core_fragment",
            () -> new CoreFragmentItem(ModBlocks.CORE_FRAGMENT.get(), new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    public static final RegistryObject<Item> ANTI_GRAVITY_BOMB = ITEMS.register("anti_gravity_bomb",
            () -> new AntiGravityBombItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));


    private ModItems() {
    }
}
