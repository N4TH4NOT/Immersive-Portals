package qouteall.imm_ptl.core.compat;

import io.github.n4th4not.immersiveportals.protocol.GravityState;
import io.github.n4th4not.gravity.fabric.GravityPlatform;
import io.github.n4th4not.gravity.fabric.GravityAdapter;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.my_util.DQuaternion;

public class GravityChangerInterface {
    public static Invoker invoker = new Invoker();

    public static void init() {
        GravityPlatform.init();
        if (GravityPlatform.adapter().isGravityChangerPresent()) {
            invoker = new qouteall.imm_ptl.core.compat.gravity.OnGravityChangerPresent();
            qouteall.q_misc_util.Helper.log("Gravity Changer 26.2 portal integration enabled");
        }
    }

    public static class Invoker {
        private static final GravityAdapter VANILLA = new GravityAdapter();

        protected GravityAdapter adapter() { return VANILLA; }

        public boolean isGravityChangerPresent() {
            return adapter().isGravityChangerPresent();
        }

        public Vec3 getEyeOffset(Entity entity) {
            return adapter().getEyeOffset(entity);
        }

        public Direction getGravityDirection(Entity entity) {
            return adapter().getGravityDirection(entity);
        }

        public Direction getBaseGravityDirection(Entity entity) {
            return adapter().getBaseGravityDirection(entity);
        }

        public Direction getGravityDirectionWithBase(Entity entity, Direction baseDirection) {
            return adapter().getGravityDirectionWithBase(entity, baseDirection);
        }

        public GravityState getState(Entity entity) {
            return adapter().getState(entity);
        }

        public void applyState(Entity entity, GravityState state, boolean snapVisualRotation) {
            adapter().applyState(entity, state, snapVisualRotation);
        }

        public void setClientPlayerGravityDirection(Player player, Direction direction) {
            if (isGravityChangerPresent()) adapter().setClientPlayerGravityDirection(player, direction);
            else warnGravityChangerNotPresent();
        }

        public void setBaseGravityDirectionServer(Entity entity, Direction direction) {
            adapter().setBaseGravityDirectionServer(entity, direction);
        }

        @Nullable
        public DQuaternion getExtraCameraRotation(Direction gravityDirection) {
            var rotation = adapter().getExtraCameraRotation(gravityDirection);
            return rotation == null ? null : DQuaternion.fromMcQuaternion(rotation);
        }

        public Vec3 getWorldVelocity(Entity entity) {
            return adapter().getWorldVelocity(entity);
        }

        public void setWorldVelocity(Entity entity, Vec3 newVelocity) {
            adapter().setWorldVelocity(entity, newVelocity);
        }

        public Vec3 transformPlayerToWorld(Direction gravity, Vec3 vec3d) {
            return adapter().transformPlayerToWorld(gravity, vec3d);
        }

        public Vec3 transformWorldToPlayer(Direction gravity, Vec3 vec3d) {
            return adapter().transformWorldToPlayer(gravity, vec3d);
        }

        public Direction transformDirPlayerToWorld(Direction gravity, Direction direction) {
            return adapter().transformDirPlayerToWorld(gravity, direction);
        }

        public Direction transformDirWorldToPlayer(Direction gravity, Direction direction) {
            return adapter().transformDirWorldToPlayer(gravity, direction);
        }
    }

    private static boolean warned = false;

    @Environment(EnvType.CLIENT)
    private static void warnGravityChangerNotPresent() {
        if (!warned) {
            warned = true;
            CHelper.printChat(
                    Component.translatable("imm_ptl.missing_gravity_changer")
                            .append(
                                    McHelper.getLinkText(
                                            "https://modrinth.com/mod/gravity-changer-unofficial-port")));
        }
    }
}
