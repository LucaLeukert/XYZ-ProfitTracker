package net.leukert.xyz;

import cc.polyfrost.oneconfig.utils.Multithreading;
import net.leukert.client.GameStateHelper;
import net.leukert.time.Clock;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.leukert.util.APIUtils;
import net.leukert.util.InventoryUtils;
import net.leukert.time.Timer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ProfitTracker implements GameStateHelper.LocationChangeListener {
    public static final ProfitTracker instance = new ProfitTracker();

    public final ImmutableList<BazaarItem> xyzItems = ImmutableList.of(
            new BazaarItem("_X", "X"),
            new BazaarItem("_Y", "Y"),
            new BazaarItem("_Z", "Z"));
    public final Clock updateClock = new Clock();
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Clock updateBazaarClock = new Clock();
    private final Timer timer = new Timer();
    private final NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("de", "DE"));
    public double realProfit = 0;
    public double realHourlyProfit = 0;
    private boolean cantConnectToApi = false;

    private ProfitTracker() {
        GameStateHelper.instance.addLocationChangeListener(this);
    }

    @SubscribeEvent
    public void onTickUpdateBazaarPrices(TickEvent.ClientTickEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (updateBazaarClock.passed()) {
            System.out.println("tick");
            updateBazaarClock.schedule(1000 * 60 * 5);
            System.out.println("Updating bazaar prices...");
            Multithreading.schedule(this::fetchBazaarPrices, 0, TimeUnit.MILLISECONDS);
        }
    }

    public void fetchBazaarPrices() {
        try {
            String url = "https://api.hypixel.net/skyblock/bazaar";
            String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.102 Safari/537.36";
            JsonObject request = APIUtils.readJsonFromUrl(url, "User-Agent", userAgent);
            if (request == null) {
                System.out.println("Failed to fetch bazaar prices!");
                cantConnectToApi = true;
                return;
            }
            JsonObject json = request.getAsJsonObject();
            JsonObject products = json.getAsJsonObject("products");

            for (BazaarItem item : xyzItems) {
                JsonObject json2 = products.getAsJsonObject(item.bazaarId);
                JsonArray json3 = json2.getAsJsonArray("buy_summary");
                JsonObject json4 = json3.size() > 1 ? json3.get(1).getAsJsonObject() : json3.get(0).getAsJsonObject();

                double buyPrice = json4.get("pricePerUnit").getAsDouble();
                item.pricePerUnit = new APIPrice(item.localizedName, buyPrice);
            }

            for (BazaarItem xyzItem : xyzItems) {
                System.out.println(xyzItem.localizedName + ": " + xyzItem.pricePerUnit.currentPrice);
            }

            System.out.println("Prices updated.");
            cantConnectToApi = false;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to fetch bazaar prices!");
            cantConnectToApi = true;
        }
    }

    @SubscribeEvent
    public void onTickUpdateProfit(TickEvent.ClientTickEvent event) {
        if (!GameStateHelper.instance.inCrimsonIsle()) return;

        double profit = 0;
        for (BazaarItem item : xyzItems) {
            profit += (float) (item.currentAmount * item.pricePerUnit.currentPrice);
        }

        realProfit = profit;
        realHourlyProfit = profit / (timer.getElapsedTime() / 1000f / 60 / 60);
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onReceivedChat(ClientChatReceivedEvent event) {
        if (!GameStateHelper.instance.inCrimsonIsle()) return;
        if (event.type != 0) return;

        String message = StringUtils.stripControlCodes(event.message.getUnformattedText());
        if (!message.contains("Successfully removed an orb!")) return;
        int xInInventory = InventoryUtils.getAmountOfItemInInventory("X");
        int yInInventory = InventoryUtils.getAmountOfItemInInventory("Y");
        int zInInventory = InventoryUtils.getAmountOfItemInInventory("Z");

        System.out.println("X in inventory: " + xInInventory);
        System.out.println("Y in inventory: " + yInInventory);
        System.out.println("Z in inventory: " + zInInventory);

        for (BazaarItem item : xyzItems) {
            switch (item.localizedName) {
                case "_X":
                    item.currentAmount = xInInventory;
                    break;
                case "_Y":
                    item.currentAmount = yInInventory;
                    break;
                case "_Z":
                    item.currentAmount = zInInventory;
                    break;
            }
        }
    }

    public String getRealProfitString() {
        return formatter.format(realProfit);
    }

    public String getProfitPerHourString() {
        return formatter.format(realHourlyProfit) + "/hr";
    }

    @Override
    public void onLocationChange(GameStateHelper.Location oldLocation, GameStateHelper.Location newLocation) {
        if (oldLocation == GameStateHelper.Location.CRIMSON_ISLE && newLocation != GameStateHelper.Location.CRIMSON_ISLE) {
            timer.reset();
            for (BazaarItem item : xyzItems) {
                item.currentAmount = 0;
            }
        }

        if(oldLocation != GameStateHelper.Location.CRIMSON_ISLE && newLocation == GameStateHelper.Location.CRIMSON_ISLE) {
            timer.schedule();
        }
    }

    public void reset() {
        timer.reset();
        for (BazaarItem item : xyzItems) {
            item.currentAmount = 0;
        }
        realProfit = 0;
        realHourlyProfit = 0;
        cantConnectToApi = false;
        updateClock.reset();
        updateBazaarClock.reset();
        fetchBazaarPrices();
        if(GameStateHelper.instance.inCrimsonIsle())
            timer.schedule();
    }

    public static class BazaarItem {
        public String localizedName;
        public String bazaarId;
        public String image;
        public APIPrice pricePerUnit;
        public int currentAmount = 0;

        public BazaarItem(String localizedName, String bazaarId) {
            this.localizedName = localizedName;
            this.bazaarId = bazaarId;
            this.setImage();
        }

        public void setImage() {
            System.out.println("bazarId: " + bazaarId.toLowerCase());
            this.image = "/xyzprofittracker/textures/gui/" + bazaarId.toLowerCase() + ".png";
        }
    }

    public static class APIPrice {
        public double currentPrice = 0;
        public String name;

        public APIPrice(String name, double currentPrice) {
            this.name = name;
            this.currentPrice = currentPrice;
        }
    }
}
