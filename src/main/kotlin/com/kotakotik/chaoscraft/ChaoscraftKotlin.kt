package com.kotakotik.chaoscraft

import com.kotakotik.chaoscraft.chaos_event_stuff.ChaosEventRegister
import com.kotakotik.chaoscraft.keybinds.ChaosKeybindings
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.serializer.ConfigSerializer


class ChaoscraftKotlin {
    companion object {
        val log: Logger = LogManager.getLogger("chaoscraft")

        var register: ChaosEventRegister? = null;

        fun init() {
            ChaosKeybindings.openConfig // load it so the category gets mentioned

            ServerLifecycleEvents.SERVER_STARTED.register { event ->
                register = ChaosEventRegister();
                register!!.reg()
            }
            ServerTickEvents.END_SERVER_TICK.register { server ->
                register?.tick(server)
            }
        }
    }
}