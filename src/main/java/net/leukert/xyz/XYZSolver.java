package net.leukert.xyz;

import com.google.common.collect.ImmutableList;
import net.leukert.Main;
import net.leukert.client.render.RenderUtils;
import net.leukert.config.XYZConfig;
import net.leukert.util.MathUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.StringUtils;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XYZSolver {
    public static final XYZSolver instance = new XYZSolver();
    // regex for the name-stand (only loaded nearby)
    public static final Pattern ATOM_NAME = Pattern.compile("^\\[Lv(\\d+)] (Exe|Wai|Zee) (\\d+)/(\\d+)❤$");
    private static final double MOVE_THRESHOLD = 1.0;
    // the UUIDs baked into Hypixel for each skull
    private static final UUID EXE_ID = UUID.fromString("25f7c8b4-3cc7-33df-a0c8-33f5e309036f");
    private static final UUID WAI_ID = UUID.fromString("954e4eac-fbed-3a5a-8ab7-091cb189cac1");
    private static final UUID ZEE_ID = UUID.fromString("df890e75-67c6-3119-be04-9a3175c2455d");
    // how high above the block the lines will render
    private static final double LINE_Y_OFFSET = 1;
    // tracked by *skull* stand entityId
    public final Map<Integer, AtomInfo> atoms = new HashMap<>();

    // The comprehensive plan of steps
    public ImmutableList<PlanStep> solvePlan = ImmutableList.of();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent evt) {
        if (Main.mc.theWorld == null || Main.mc.thePlayer == null) return;

        Set<Integer> seen = new HashSet<>();

        // 1) SCAN FOR SKULL STANDS (always loaded)
        for (Entity ent : Main.mc.theWorld.loadedEntityList) {
            if (!(ent instanceof EntityArmorStand)) continue;
            EntityArmorStand stand = (EntityArmorStand) ent;

            ItemStack head = stand.getEquipmentInSlot(4);
            if (head == null || !(head.getItem() instanceof ItemSkull)) continue;
            if (!head.hasTagCompound()) continue;

            NBTTagCompound skullTag = head.getTagCompound().getCompoundTag("SkullOwner");
            if (!skullTag.hasKey("Id", Constants.NBT.TAG_STRING)) continue;

            UUID ownerId;
            try {
                ownerId = UUID.fromString(skullTag.getString("Id"));
            } catch (IllegalArgumentException e) {
                continue;
            }

            AtomType type = AtomType.fromId(ownerId);
            if (type == null) continue;

            int eid = stand.getEntityId();
            seen.add(eid);

            AtomInfo info = atoms.get(eid);
            if (info == null) {
                // new skull stand
                info = new AtomInfo(stand, type);
                atoms.put(eid, info);
            } else {
                // refresh stand & type
                info.stand = stand;
                info.type = type;
            }
        }

        // 2) EVICT DESPAWNED
        Iterator<Map.Entry<Integer, AtomInfo>> it = atoms.entrySet().iterator();
        while (it.hasNext()) {
            AtomInfo ai = it.next().getValue();
            if (ai.stand.isDead || !seen.contains(ai.stand.getEntityId())) {
                it.remove();
            }
        }

        // 3) WHEN NAME-STAND LOADED, PAIR & READ HP
        for (Entity ent : Main.mc.theWorld.loadedEntityList) {
            if (!(ent instanceof EntityArmorStand)) continue;
            EntityArmorStand nameStand = (EntityArmorStand) ent;
            if (!nameStand.hasCustomName()) continue;

            String raw = StringUtils.stripControlCodes(nameStand.getCustomNameTag());
            Matcher m = ATOM_NAME.matcher(raw);
            if (!m.matches()) continue;

            // find nearest skull stand within 1 block
            EntityArmorStand best = null;
            double bestDist = Double.MAX_VALUE;
            for (AtomInfo ai : atoms.values()) {
                double d = ai.stand.getDistanceToEntity(nameStand);
                if (d < bestDist) {
                    bestDist = d;
                    best = ai.stand;
                }
            }
            if (best != null && bestDist < 1.0) {
                AtomInfo ai = atoms.get(best.getEntityId());
                ai.curHP = Integer.parseInt(m.group(3));
                ai.maxHP = Integer.parseInt(m.group(4));
            }
        }

        // 4) RECOMPUTE PLAN
        Optional<ImmutableList<PlanStep>> planSteps = this.computePlan();
        planSteps.ifPresent(steps -> solvePlan = steps);
    }

    public Optional<ImmutableList<PlanStep>> computePlan() {
        List<AtomInfo> charmExe = new ArrayList<>();
        List<AtomInfo> charmWai = new ArrayList<>();
        List<AtomInfo> unExe = new ArrayList<>();
        List<AtomInfo> unWai = new ArrayList<>();
        List<AtomInfo> zees = new ArrayList<>();

        for (AtomInfo ai : atoms.values()) {
            boolean charmed = ai.checkFollowing();

            if (ai.type == AtomType.Exe) {
                if (charmed) charmExe.add(ai);
                else unExe.add(ai);
            } else if (ai.type == AtomType.Wai) {
                if (charmed) charmWai.add(ai);
                else unWai.add(ai);
            } else if (ai.type == AtomType.Zee) {
                zees.add(ai);
            }
        }

        if (!zees.isEmpty()) {
            solvePlan = ImmutableList.of();
            return Optional.empty();
        }

        int totalExe = charmExe.size() + unExe.size();
        int totalWai = charmWai.size() + unWai.size();

        List<List<PlanStep>> strategies = new ArrayList<>();

        if (totalWai >= 2) {
            List<PlanStep> plan = new ArrayList<>();

            // If no charmed Wai, charm one
            if (charmWai.isEmpty() && !unWai.isEmpty()) {
                AtomInfo waiToCharm = findClosest(unWai);
                if (waiToCharm != null) {
                    plan.add(PlanStep.charm(waiToCharm));

                    // Pick another Wai to combine with
                    List<AtomInfo> remainingWai = new ArrayList<>(unWai);
                    remainingWai.remove(waiToCharm);
                    if (!remainingWai.isEmpty()) {
                        AtomInfo targetWai = findClosest(remainingWai);
                        if (targetWai != null) {
                            plan.add(PlanStep.combine(waiToCharm, targetWai, AtomType.Zee));
                        }
                    }
                }
            } else if (!charmWai.isEmpty() && !unWai.isEmpty()) {
                // Already have charmed Wai, just combine with uncharmed Wai
                AtomInfo targetWai = findClosest(unWai);
                if (targetWai != null) {
                    plan.add(PlanStep.combine(charmWai.get(0), targetWai, AtomType.Zee));
                }
            }

            if (!plan.isEmpty()) {
                strategies.add(plan);
            }
        }

        // Strategy 2: Build Wai from Exe, then Zee
        if (totalExe + totalWai > 0) {
            List<PlanStep> plan = new ArrayList<>();

            // Copy lists for local modification
            List<AtomInfo> availCharmExe = new ArrayList<>(charmExe);
            List<AtomInfo> availUnExe = new ArrayList<>(unExe);
            List<AtomInfo> availCharmWai = new ArrayList<>(charmWai);
            List<AtomInfo> availUnWai = new ArrayList<>(unWai);

            // First check if we already have some Wai
            if (availCharmWai.size() + availUnWai.size() >= 2) {
                // We already have enough Wai, just need to make sure one is charmed
                if (availCharmWai.isEmpty() && !availUnWai.isEmpty()) {
                    // Charm a Wai
                    AtomInfo waiToCharm = findClosest(availUnWai);
                    if (waiToCharm != null) {
                        plan.add(PlanStep.charm(waiToCharm));
                        availCharmWai.add(waiToCharm);
                        availUnWai.remove(waiToCharm);
                    }
                }

                // Combine to make Zee
                if (!availCharmWai.isEmpty() && !availUnWai.isEmpty()) {
                    AtomInfo targetWai = findClosest(availUnWai);
                    if (targetWai != null) {
                        plan.add(PlanStep.combine(availCharmWai.get(0), targetWai, AtomType.Zee));
                    }
                }
            } else {
                // Need to create some Wai first
                int waiNeeded = 2 - (availCharmWai.size() + availUnWai.size());

                // For each Wai we need to create
                for (int i = 0; i < waiNeeded && !availUnExe.isEmpty(); i++) {
                    // If no charmed Exe, get one
                    if (availCharmExe.isEmpty()) {
                        AtomInfo exeToCharm = findClosest(availUnExe);
                        if (exeToCharm != null) {
                            plan.add(PlanStep.charm(exeToCharm));
                            availCharmExe.add(exeToCharm);
                            availUnExe.remove(exeToCharm);
                        }
                    }

                    // If we have charmed Exe and uncharmed Exe, combine to make Wai
                    if (!availCharmExe.isEmpty() && !availUnExe.isEmpty()) {
                        AtomInfo targetExe = findClosest(availUnExe);
                        AtomInfo sourceExe = availCharmExe.get(0);

                        if (targetExe != null) {
                            plan.add(PlanStep.combine(sourceExe, targetExe, AtomType.Wai));

                            // Will there be enough Wai after this?
                            AtomInfo virtualWai = new AtomInfo(targetExe.stand, AtomType.Wai);

                            if (availCharmWai.isEmpty() && availUnWai.isEmpty() && i == 0) {
                                // First Wai created - we want to charm it
                                plan.add(PlanStep.charm(virtualWai));
                                availCharmWai.add(virtualWai);
                            } else {
                                // Second Wai - don't need to charm
                                availUnWai.add(virtualWai);
                            }

                            availCharmExe.remove(sourceExe);
                            availUnExe.remove(targetExe);
                        }
                    }
                }

                // After creating Wai, combine to make Zee if we have enough
                if (!availCharmWai.isEmpty() && !availUnWai.isEmpty()) {
                    plan.add(PlanStep.combine(availCharmWai.get(0), availUnWai.get(0), AtomType.Zee));
                }
            }

            if (!plan.isEmpty()) {
                strategies.add(plan);
            }
        }

        // Choose the most efficient strategy (fewest steps)
        List<PlanStep> bestPlan = Collections.emptyList();
        if (!strategies.isEmpty()) {
            bestPlan = strategies.stream()
                    .min(Comparator.comparingInt(List::size))
                    .orElse(Collections.emptyList());
        }

        return Optional.of(ImmutableList.copyOf(bestPlan));
    }


    // Helper to find closest atom to player
    private AtomInfo findClosest(List<AtomInfo> atoms) {
        if (atoms.isEmpty()) return null;
        Vec3 playerPos = Main.mc.thePlayer.getPositionVector();
        AtomInfo closest = null;
        double closestDist = Double.MAX_VALUE;

        for (AtomInfo ai : atoms) {
            Vec3 atomPos = ai.stand.getPositionVector();
            double dist = playerPos.distanceTo(atomPos);
            if (dist < closestDist) {
                closestDist = dist;
                closest = ai;
            }
        }
        return closest;
    }

    /**
     * @param start      the player's current position
     * @param candidates all uncharmed AtomInfos (Exe then Wai)
     * @param targetE    how many Exe to visit
     * @param targetW    how many Wai to visit
     * @return the optimal visit order (subset + sequence)
     */
    private List<AtomInfo> solveBestSubsetAndOrder(
            Vec3 start,
            List<AtomInfo> candidates,
            int targetE,
            int targetW
    ) {
        // This stays unchanged
        // ... original method contents ...
        int n = candidates.size();
        if (n == 0) return Collections.emptyList();

        // 1) precompute distances
        double[] distStart = new double[n];
        double[][] dist = new double[n][n];
        int[] typeMap = new int[n]; // 1=Exe, 2=Wai
        int exeMask = 0, waiMask = 0;

        for (int i = 0; i < n; i++) {
            AtomInfo ai = candidates.get(i);
            typeMap[i] = (ai.type == AtomType.Exe ? 1 : 2);
            Vec3 pi = ai.stand.getPositionVector();
            distStart[i] = start.distanceTo(pi);
            if (typeMap[i] == 1) exeMask |= 1 << i;
            else waiMask |= 1 << i;
        }
        for (int i = 0; i < n; i++) {
            Vec3 pi = candidates.get(i).stand.getPositionVector();
            for (int j = 0; j < n; j++) {
                Vec3 pj = candidates.get(j).stand.getPositionVector();
                dist[i][j] = pi.distanceTo(pj);
            }
        }

        final int FULL = 1 << n;
        double INF = Double.POSITIVE_INFINITY;
        // dp[mask][last] = best cost to visit 'mask' of nodes, ending at 'last'
        double[][] dp = new double[FULL][n];
        int[][] parent = new int[FULL][n];
        for (int m = 0; m < FULL; m++)
            Arrays.fill(dp[m], INF);

        // 2) base cases
        for (int i = 0; i < n; i++) {
            int m = 1 << i;
            dp[m][i] = distStart[i];
            parent[m][i] = -1;
        }

        // 3) fill DP
        for (int mask = 1; mask < FULL; mask++) {
            for (int last = 0; last < n; last++) {
                if ((mask & (1 << last)) == 0) continue;
                int pm = mask ^ (1 << last);
                if (pm == 0) continue;
                double best = dp[mask][last];
                int pbest = parent[mask][last];
                for (int prev = 0; prev < n; prev++) {
                    if ((pm & (1 << prev)) == 0) continue;
                    double cost = dp[pm][prev] + dist[prev][last];
                    if (cost < best) {
                        best = cost;
                        pbest = prev;
                    }
                }
                dp[mask][last] = best;
                parent[mask][last] = pbest;
            }
        }

        // 4) choose the best mask that meets visitE & visitW
        double bestCost = INF;
        int bestMask = 0;
        int bestLast = -1;
        for (int mask = 1; mask < FULL; mask++) {
            int cntE = Integer.bitCount(mask & exeMask);
            int cntW = Integer.bitCount(mask & waiMask);
            if (cntE < targetE || cntW < targetW) continue;
            for (int last = 0; last < n; last++) {
                if ((mask & (1 << last)) == 0) continue;
                double cost = dp[mask][last];
                if (cost < bestCost) {
                    bestCost = cost;
                    bestMask = mask;
                    bestLast = last;
                }
            }
        }
        if (bestLast < 0) return Collections.emptyList();

        // 5) reconstruct path
        LinkedList<AtomInfo> path = new LinkedList<>();
        int mask = bestMask, cur = bestLast;
        while (cur >= 0) {
            path.addFirst(candidates.get(cur));
            int p = parent[mask][cur];
            mask ^= 1 << cur;
            cur = p;
        }
        return path;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent evt) {
        float partialTicks = evt.partialTicks;

        // 1) compute the camera position in world‐coords
        double camX = Main.mc.getRenderManager().viewerPosX;
        double camY = Main.mc.getRenderManager().viewerPosY;
        double camZ = Main.mc.getRenderManager().viewerPosZ;
        Vec3 camera = new Vec3(camX, camY, camZ);

        // 2) get the look vector (direction your crosshair is pointing)
        Vec3 look = Main.mc.thePlayer.getLook(partialTicks);

        float CROSSHAIR_OFFSET = XYZConfig.CROSSHAIR_OFFSET;
        Vec3 origin = camera
                .addVector(look.xCoord * CROSSHAIR_OFFSET,
                        look.yCoord * CROSSHAIR_OFFSET,
                        look.zCoord * CROSSHAIR_OFFSET)
                .addVector(0, LINE_Y_OFFSET, 0);

        for (AtomInfo ai : atoms.values()) {
            AxisAlignedBB bb = ai.stand.getEntityBoundingBox();
            bb = bb.expand(0, -0.7, 0);
            RenderUtils.drawOutlinedBoundingBox(bb, ai.getColor(), partialTicks);

            if (ai.type == AtomType.Zee) {
                Vec3 zeePos = MathUtils.interpVec3(ai.stand, partialTicks, LINE_Y_OFFSET);
                RenderUtils.draw3DLine(
                        origin, zeePos,
                        Color.CYAN,
                        XYZConfig.ZEE_LINE_WIDTH,
                        false,
                        partialTicks
                );
            }
        }

        if (solvePlan.isEmpty()) return;

        // 5) draw lines for the plan steps
        Vec3 prev = origin;
        int stepIdx = 0;
        for (PlanStep step : solvePlan) {
            if (step.target != null) {
                Vec3 next = MathUtils.interpVec3(step.target.stand, partialTicks, LINE_Y_OFFSET);

                if (stepIdx == 0 && step.type.equals(StepType.COMBINE) && step.source.checkFollowing()) {
                    RenderUtils.draw3DLine(step.source.stand.getPositionVector().addVector(0, LINE_Y_OFFSET, 0),
                            step.target.stand.getPositionVector().addVector(0, LINE_Y_OFFSET, 0),
                            Color.MAGENTA,
                            XYZConfig.PATH_SEGMENT_WIDTH,
                            true,
                            partialTicks);
                }

                Color color = stepIdx == 0 ? XYZConfig.NEXT_STEP_COLOR.toJavaColor() : step.target.getColor();

                RenderUtils.draw3DLine(prev,
                        next,
                        color,
                        XYZConfig.PATH_SEGMENT_WIDTH,
                        true,
                        partialTicks);
                prev = next;
                stepIdx++;
            }
        }
    }

    public void reset() {
        // reset the solver
        atoms.clear();
        solvePlan = ImmutableList.of();
    }

    public enum StepType {CHARM, COMBINE}

    public enum AtomType {
        Exe(EXE_ID), Wai(WAI_ID), Zee(ZEE_ID);
        final UUID id;

        AtomType(UUID id) {
            this.id = id;
        }

        static AtomType fromId(UUID u) {
            for (AtomType t : values()) if (t.id.equals(u)) return t;
            return null;
        }
    }

    public static class PlanStep {
        public final StepType type;
        public final AtomInfo target;     // for CHARM/VISIT
        final AtomInfo source;     // for COMBINE (the charmed one we walk into target)
        final AtomType result;     // for COMBINE (what we get)

        private PlanStep(StepType t, AtomInfo target, AtomInfo source, AtomType result) {
            this.type = t;
            this.target = target;
            this.source = source;
            this.result = result;
        }

        static PlanStep charm(AtomInfo ai) {
            return new PlanStep(StepType.CHARM, ai, null, null);
        }

        static PlanStep combine(AtomInfo charmed, AtomInfo uncharmed, AtomType result) {
            return new PlanStep(StepType.COMBINE, uncharmed, charmed, result);
        }

        @Override
        public String toString() {
            switch (type) {
                case CHARM:
                    Vec3 p = target.stand.getPositionVector();
                    return String.format("Charm %s at (%.1f,%.1f,%.1f)",
                            target.type, p.xCoord, p.yCoord, p.zCoord);
                case COMBINE:
                    Vec3 c = target.stand.getPositionVector();
                    return String.format("Walk %s into %s at (%.1f,%.1f,%.1f) → %s",
                            source.type, target.type, c.xCoord, c.yCoord, c.zCoord, result);
            }
            return "";
        }
    }

    // --------------------------------------------------------------
    // atom‐info + type enum
    // --------------------------------------------------------------
    public static class AtomInfo {
        final double sx, sy, sz;
        public EntityArmorStand stand;
        AtomType type;
        Integer curHP, maxHP;   // null until a name-stand is in range

        AtomInfo(EntityArmorStand s, AtomType t) {
            this.stand = s;
            this.type = t;
            this.sx = s.posX;
            this.sy = s.posY;
            this.sz = s.posZ;
        }

        Color getColor() {
            if(checkFollowing()) {
                return Color.MAGENTA;
            }

            switch (type) {
                case Exe:
                    return XYZConfig.EXE_COLOR.toJavaColor();
                case Wai:
                    return XYZConfig.WAI_COLOR.toJavaColor();
                case Zee:
                    return XYZConfig.ZEE_COLOR.toJavaColor();
            }
            return Color.WHITE;
        }

        boolean checkFollowing() {
            // ignore vertical bobbing
            double dx = stand.posX - sx, dz = stand.posZ - sz;
            return Math.sqrt(dx * dx + dz * dz) > MOVE_THRESHOLD;
        }
    }
}
