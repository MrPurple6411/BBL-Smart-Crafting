package com.benbenlaw.template;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@Mod(Template.MOD_ID)
public class Template {
    public static final String MOD_ID = "template";
    public static final Logger LOGGER = LogManager.getLogger();

    public Template(final IEventBus eventBus, final ModContainer modContainer) {


    }
}
