package baritone.api;

import baritone.api.pathing.goals.Goal;
import net.minecraft.util.math.BlockPos;

public interface IBaritone {
    PathingBehavior getPathingBehavior();

    CustomGoalProcess getCustomGoalProcess();

    MineProcess getMineProcess();

    default BuilderProcess getBuilderProcess() {
        return new BuilderProcess() {
            @Override
            public void onLostControl() {
            }
        };
    }

    default SelectionManager getSelectionManager() {
        return new SelectionManager() {
            @Override
            public void removeAllSelections() {
            }
        };
    }

    default CommandManager getCommandManager() {
        return new CommandManager() {
            @Override
            public void execute(String command) {
            }
        };
    }

    interface PathingBehavior {
        boolean hasPath();

        default boolean isPathing() {
            return hasPath();
        }

        void cancelEverything();

        void requestPause();
    }

    interface CustomGoalProcess {
        default boolean isActive() {
            return false;
        }

        void setGoalAndPath(Goal goal);
    }

    interface MineProcess {
        boolean isActive();

        default void cancel() {
        }

        void minePositions(net.minecraft.item.Item item, Iterable<BlockPos> positions);

        java.util.Set<BlockPos> getBlacklist();
    }

    interface BuilderProcess {
        void onLostControl();
    }

    interface SelectionManager {
        void removeAllSelections();
    }

    interface CommandManager {
        void execute(String command);
    }
}
