package net.leukert;

import net.leukert.client.GameStateHelper;
import net.leukert.config.XYZConfig;
import net.leukert.xyz.ProfitTracker;
import net.leukert.xyz.XYZSolver;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StringUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Matcher;

@Mod(
        modid = Main.MODID,
        name = Main.NAME,
        version = Main.VERSION
)
public class Main {
    public static final String MODID = "xyz-helper";
    public static final String NAME = "XYZ Helper";
    public static final String VERSION = "1.0.0";
    public static final Minecraft mc = Minecraft.getMinecraft();
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static XYZConfig CONFIG;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        CONFIG = new XYZConfig();
        MinecraftForge.EVENT_BUS.register(ProfitTracker.instance);
        MinecraftForge.EVENT_BUS.register(GameStateHelper.instance);
        MinecraftForge.EVENT_BUS.register(XYZSolver.instance);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent evt) {
        if (!(evt.target instanceof EntityArmorStand)) return;
        EntityArmorStand stand = (EntityArmorStand) evt.target;
        if (!stand.getEntityData().getBoolean("isAtomSkull") &&
                !stand.getEntityData().getBoolean("isAtomName")) {
            return;
        }

        // locate the name-stand
        EntityArmorStand nameStand = null;
        for (Object o : stand.worldObj.loadedEntityList) {
            if (!(o instanceof EntityArmorStand)) continue;
            EntityArmorStand as = (EntityArmorStand) o;
            if (!as.getEntityData().getBoolean("isAtomName")) continue;
            if (as.getDistanceToEntity(stand) < 1.0) {
                nameStand = as;
                break;
            }
        }
        if (nameStand == null) return;

        String raw = StringUtils.stripControlCodes(nameStand.getCustomNameTag());
        Matcher m = XYZSolver.ATOM_NAME.matcher(raw);
        if (!m.matches()) return;

        int lvl = Integer.parseInt(m.group(1));
        String type = m.group(2);
        int cur = Integer.parseInt(m.group(3));
        int max = Integer.parseInt(m.group(4));

        // halve, but keep at least 1
        int newCur = Math.max(1, cur / 2);

        nameStand.setCustomNameTag(String.format(
                "[Lv%d] %s %d/%d❤", lvl, type, newCur, max
        ));

        evt.entityPlayer.addChatMessage(new ChatComponentText(
                "➤ " + type + " HP halved: " + newCur + "/" + max
        ));

        evt.setCanceled(true);
    }
}
