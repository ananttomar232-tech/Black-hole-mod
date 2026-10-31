package com.ananttomar.livingblackhole.registry;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LivingBlackHole.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.livingblackhole"))
            .icon(() -> new ItemStack(ModItems.POCKET_BLACK_HOLE.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.POCKET_BLACK_HOLE.get());
                // A unit one chunk away from breaching, for showcases and testing.
                ItemStack critical = new ItemStack(ModItems.POCKET_BLACK_HOLE.get());
                PocketBlackHoleItem.setConsumed(critical, Math.max(0, PocketBlackHoleItem.limit() - 1));
                output.accept(critical);
                output.accept(ModItems.CORE_FRAGMENT.get());
                output.accept(ModItems.ANTI_GRAVITY_BOMB.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
