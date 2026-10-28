package com.nwyfre.nwyfre.registry;

import java.util.function.Supplier;

import com.nwyfre.nwyfre.Nwyfre;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NwyfreCreativeTab {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Nwyfre.MODID);

    public static final Supplier<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nwyfre.main"))
                    .icon(() -> new ItemStack(NwyfreItems.RITUAL_ALTAR.get()))
                    .displayItems((params, output) -> {
                        output.accept(NwyfreItems.RITUAL_ALTAR.get());
                        output.accept(NwyfreItems.RITUAL_BOWL.get());
                        output.accept(NwyfreItems.CHALK_GLYPH.get());
                    })
                    .build());

    private NwyfreCreativeTab() {
    }
}
