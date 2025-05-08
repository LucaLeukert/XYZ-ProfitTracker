package net.leukert.config;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Button;
import cc.polyfrost.oneconfig.config.annotations.Color;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.Slider;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import net.leukert.xyz.XYZSolver;
import net.leukert.hud.XYZProfitTrackerHud;

public class XYZConfig extends Config {

    @HUD(name = "XYZ Profit Tracker", category = "XYZ")
    public static XYZProfitTrackerHud hud = new XYZProfitTrackerHud();

    @Slider(name = "crosshair offset for XYZ Solver", min = 0.5f, max = 2f)
    public static float CROSSHAIR_OFFSET = 1.3f;

    @Slider(name = "width for XYZ solver line", min = 1, max = 5)
    public static int PATH_SEGMENT_WIDTH = 2;

    @Slider(name = "width for XYZ solver (wee) line", min = 1, max = 5)
    public static int ZEE_LINE_WIDTH = 4;

    @Color(name = "Next step color")
    public static OneColor NEXT_STEP_COLOR = new OneColor(java.awt.Color.BLUE);

    @Color(name = "Exe color")
    public static OneColor EXE_COLOR = new OneColor(java.awt.Color.GREEN);

    @Color(name = "Wai color")
    public static OneColor WAI_COLOR = new OneColor(java.awt.Color.RED);

    @Color(name = "Zee color")
    public static OneColor ZEE_COLOR = new OneColor(90, 0, 165);

    @Color(name = "Charmed color")
    public static OneColor CHARMED_COLOR = new OneColor(java.awt.Color.MAGENTA);

    @Button(name = "Reset Solver", text = "Reset Solver", description = "Resets the XYZ solver")
    protected Runnable reset = XYZSolver.instance::reset;

    public XYZConfig() {
        super(new Mod("XYZ-Profit Tracker", ModType.HYPIXEL), "config.json");
        initialize();
    }
}
