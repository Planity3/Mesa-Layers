package com.example.mesalayers;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@Mod(MesaLayers.MOD_ID)
public class MesaLayers {
    public static final String MOD_ID = "mesalayers";

    public MesaLayers(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new MesaLayerLoader()));
    }
}
