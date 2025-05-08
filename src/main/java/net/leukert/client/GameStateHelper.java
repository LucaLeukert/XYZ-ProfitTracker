package net.leukert.client;

import net.leukert.util.InventoryUtils;
import net.leukert.util.ScoreboardUtils;
import net.leukert.util.TablistUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameStateHelper {

    public static final GameStateHelper instance = new GameStateHelper();

    private final List<LocationChangeListener> listeners = new ArrayList<>();
    private final Pattern areaPattern = Pattern.compile("Area:\\s(.+)");
    private final Minecraft mc = Minecraft.getMinecraft();

    private Location lastLocation = Location.TELEPORTING;
    private Location location = Location.TELEPORTING;

    public boolean inCrimsonIsle() {
        return location != Location.TELEPORTING && location == Location.CRIMSON_ISLE;
    }

    public void addLocationChangeListener(LocationChangeListener listener) {
        listeners.add(listener);
    }

    public void removeLocationChangeListener(LocationChangeListener listener) {
        listeners.remove(listener);
    }

    @SubscribeEvent
    public void onWorldChange(WorldEvent.Unload event) {
        lastLocation = location;
        location = Location.TELEPORTING;
    }

    @SubscribeEvent
    public void onTickCheckLocation(TickEvent.ClientTickEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        if (TablistUtils.getTabList().size() == 1 && ScoreboardUtils.getScoreboardLines().isEmpty() && InventoryUtils.isInventoryEmpty(mc.thePlayer)) {
            lastLocation = location;
            setLocation(Location.LIMBO);
            return;
        }

        for (String line : TablistUtils.getTabList()) {
            Matcher matcher = areaPattern.matcher(line);
            if (matcher.find()) {
                String area = matcher.group(1);
                for (Location island : Location.values()) {
                    if (area.equals(island.name)) {
                        lastLocation = location;
                        setLocation(island);
                        return;
                    }
                }
            }
        }

        if (!ScoreboardUtils.getScoreboardTitle().contains("SKYBLOCK")
                && !ScoreboardUtils.getScoreboardLines().isEmpty()
                && ScoreboardUtils.cleanSB(ScoreboardUtils.getScoreboardLines().get(0)).contains("www.hypixel.net")) {
            lastLocation = location;
            setLocation(Location.LOBBY);
            return;
        }

        if (location != Location.TELEPORTING) lastLocation = location;
        setLocation(Location.TELEPORTING);
    }

    private void setLocation(Location location) {
        this.location = location;

        if (lastLocation != location) {
            for (LocationChangeListener listener : listeners) {
                listener.onLocationChange(lastLocation, location);
            }
        }
    }

    public enum Location {
        PRIVATE_ISLAND("Private Island"),
        HUB("Hub"),
        THE_PARK("The Park"),
        THE_FARMING_ISLANDS("The Farming Islands"),
        SPIDER_DEN("Spider's Den"),
        THE_END("The End"),
        CRIMSON_ISLE("Crimson Isle"),
        GOLD_MINE("Gold Mine"),
        DEEP_CAVERNS("Deep Caverns"),
        DWARVEN_MINES("Dwarven Mines"),
        CRYSTAL_HOLLOWS("Crystal Hollows"),
        JERRY_WORKSHOP("Jerry's Workshop"),
        DUNGEON_HUB("Dungeon Hub"),
        LIMBO("UNKNOWN"),
        LOBBY("PROTOTYPE"),
        GARDEN("Garden"),
        DUNGEON("Dungeon"),
        TELEPORTING("Teleporting");

        private final String name;

        Location(String name) {
            this.name = name;
        }
    }

    public interface LocationChangeListener {
        void onLocationChange(GameStateHelper.Location oldLocation, GameStateHelper.Location newLocation);
    }
}
