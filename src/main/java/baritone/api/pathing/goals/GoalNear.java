package baritone.api.pathing.goals;

import net.minecraft.util.math.BlockPos;

public class GoalNear implements Goal {
    private final BlockPos pos;
    private final int radius;

    public GoalNear(BlockPos pos, int radius) {
        this.pos = pos.toImmutable();
        this.radius = radius;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getRadius() {
        return this.radius;
    }
}
