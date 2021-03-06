package com.kotakotik.chaoscraft.chaos_handlers;


import com.google.gson.Gson;
import com.kotakotik.chaoscraft.Chaos;
import com.kotakotik.chaoscraft.Translation;
import com.kotakotik.chaoscraft.TranslationKeys;
import com.kotakotik.chaoscraft.config.Config;
import com.kotakotik.chaoscraft.networking.packets.PacketTimerRestart;
import com.kotakotik.chaoscraft.networking.packets.PacketTimerSet;
import com.kotakotik.chaoscraft.networking.packets.PacketTimerSync;
import com.kotakotik.chaoscraft.utils.FileUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@Mod.EventBusSubscriber()
public class ChaosEventHandler {
    private static Gson GSON = new Gson();

    public static int ticks = 0;
    public static int ticksClient = 0;

    private static List<ChaosEvent> enabledEvents = new ArrayList<>();
    private static List<ChaosEventTemp> activeEvents = new ArrayList<>();

    public static List<ChaosEvent> customEvents = new ArrayList<>();

    private static MinecraftServer Server;

    @SubscribeEvent // i am literally so stupid i forgot to put the @SubscribeEvent here lmao
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        new PacketTimerSet(ticks).sendToClient((ServerPlayerEntity) event.getPlayer());
    }

    public static List<ChaosEvent> getEnabledEvents() {
        return enabledEvents;
    }

    public static List<ChaosEvent> updateEnabledEvents() throws IOException {
        List<ChaosEvent> tempEnabledEvents = new ArrayList<>();
        HashMap<String, ForgeConfigSpec.BooleanValue> vals = Config.getEventBooleans();
        HashMap<String, ChaosEvent> events = ChaosEvents.getAsMap();
        for(String id : events.keySet()) {
            ChaosEvent event = events.get(id);
           boolean val = false;
           if(vals.containsKey(id)) {
               val = vals.get(id).get();
           }
            if( val || (/* handler for events without on/off config */!vals.containsKey(id) && event.isEnabledOnDefault())) {
                if(event instanceof ChaosEventTemp) {
                    if(!activeEvents.contains((ChaosEventTemp) event) && activeEvents.stream().noneMatch((event1) -> event1.isIncompatibleWith((ChaosEventTemp) event))) {
                        tempEnabledEvents.add(events.get(id));
                    }
                } else {
                    tempEnabledEvents.add(events.get(id));
                }
            }
        }

        tempEnabledEvents.addAll(customEvents);

        enabledEvents = tempEnabledEvents;
//        LogManager.getLogger().info(
//                String.format(
//                        "enabled events updated, number of enabled events: %d. number of disabled events: %d (does not include events that cannot be turned off or on)",
//                        enabledEvents.size(),
//                        events.size() - enabledEvents.size()
//                )
//        );
        return enabledEvents;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) throws IOException {
        if(event.phase == TickEvent.Phase.START) return;
        if(Server == null) return;
        // why am i making so many comments rn lol

        int ticksToNext = Config.SECONDS_FOR_EVENT.get() * 20;

        ticks++;
        if(ticks >= ticksToNext) {
            ChaosEvent randomEvent = ChaosEvents.getRandom(enabledEvents);

            TranslationTextComponent message = randomEvent instanceof ChaosEventTemp ?
                    TranslationKeys.TimedEventStarted.getComponent(randomEvent.getTranslation(), String.valueOf(((ChaosEventTemp) randomEvent).getDuration()))
                    :
                    TranslationKeys.EventStarted.getComponent(randomEvent.getTranslation());
            for (ServerPlayerEntity player : Server.getPlayerList().getPlayers()) {
                player.sendStatusMessage(message, false);
            }
            // send packet to restart timer to all players
            new PacketTimerRestart().sendToAllClients();

            if(randomEvent instanceof ChaosEventTemp) {
                ((ChaosEventTemp) randomEvent).reset();
                activeEvents.add((ChaosEventTemp) randomEvent);
                updateEnabledEvents();
            }

            randomEvent.start(Server);

            ticks = 0;
        }

        List<ChaosEventTemp> copy = new ArrayList<>();
        copy.addAll(activeEvents);
        for(ChaosEventTemp event1 : copy) {
            event1.tick(Server);
            if(event1.hasEnded()) {
                activeEvents.remove(event1);
                updateEnabledEvents();
            }
        }
    }

    private static int ticksSinceLastUpdate = 0;
//    private final static int ticksToUpdate = 20 * 20; // sync ticks every 20 seconds

    public static int getTicksToUpdate() {
        return Config.SECONDS_FOR_SYNC.get();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if(event.phase == TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if(mc.isGamePaused()) return;
        if(mc.world == null) {ticksClient = 0; ticks = 0; return;}
        // dont count if not loaded, set client ticks to 0 so it resets when you leave,
        // and set server ticks to 0 so it doesnt save them when leaving a single player world
        // (server ticks are handled on client when playing in singleplayer)

        // who would have thought making a simple timer would be such a pain my god
        ticksClient++;
        if(ticksClient < 0) {
            new PacketTimerSync().sendToServer();
        }
        if(Config.AUTO_SYNC.get()) {
            ticksSinceLastUpdate++;
            if(ticksSinceLastUpdate >= getTicksToUpdate()) {
                ticksSinceLastUpdate = 0;
                new PacketTimerSync().sendToServer();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(FMLServerStartedEvent event) throws IOException, IllegalAccessException, InstantiationException, ClassNotFoundException {
        Server = event.getServer();
        updateEnabledEvents();
        Chaos.registerEvents();
    }

    public static int getTicks() {
        return ticks;
    }

    public static List<ChaosEvent> registerCustomEvents() throws IOException {
        List<ChaosEvent> list = new ArrayList<>();
        Path gamedir = FMLPaths.GAMEDIR.get();
        Path eventFolder = gamedir.resolve("chaoscraft.custom_events");
        Path TEventFolder = gamedir.resolve("chaoscraft.custom_temp_events");
        if(!eventFolder.toFile().exists()) {
            eventFolder.toFile().mkdir();
        }
        if(!TEventFolder.toFile().exists()) {
            TEventFolder.toFile().mkdir();
        }
        for(File file : Objects.requireNonNull(eventFolder.toFile().listFiles())) {
            String json = FileUtils.readFile(file.toPath());
           CustomEvent customEvent = CustomEvent.getCustom(json, GSON);
           ChaosEvent event = customEvent.getEvent(Server);
           list.add(event);
        }
        for(File file : Objects.requireNonNull(TEventFolder.toFile().listFiles())) {
            String json = FileUtils.readFile(file.toPath());
            CustomEvent customEvent = CustomEventTemp.getCustom(json, GSON);
            ChaosEvent event = customEvent.getEvent(Server);
            list.add(event);
        }
        return list;
    }
}
