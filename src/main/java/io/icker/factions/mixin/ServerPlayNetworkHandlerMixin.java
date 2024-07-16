package io.icker.factions.mixin;

import io.icker.factions.api.events.PlayerEvents;
import io.icker.factions.api.persistents.User;
import io.icker.factions.core.ChatManager;
import io.icker.factions.util.Message;
import net.minecraft.network.MessageType;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.filter.TextStream;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.EntityTrackingListener;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.function.Function;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin implements EntityTrackingListener, ServerPlayPacketListener {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("HEAD"))
    public void onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        PlayerEvents.ON_MOVE.invoker().onMove(player);
    }

    @Redirect(method = "handleMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V"))
    private void broadcast(PlayerManager instance, Text serverMessage, Function<ServerPlayerEntity, Text> playerMessageFactory, MessageType type, UUID sender, TextStream.Message message) {
        ChatManager.handleMessage(instance.getPlayer(sender), message.getRaw());
    }

    @Inject(method = "handleMessage", at = @At("HEAD"), cancellable = true)
    public void handleMessage(TextStream.Message message, CallbackInfo ci){
        User user = User.get(this.player.getName().getString());
        boolean isCommandAndJailed = message.getRaw().startsWith("/") && user.getPrisoner(this.player.getServer()) != null;
        if(isCommandAndJailed){
            new Message("§cYou are in jail and unable to execute any commands!").fail().send(this.player, false);
            ci.cancel();
        }
    }
}
