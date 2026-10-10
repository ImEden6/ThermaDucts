package com.mervyn.thermaducts.network.payload;

import com.mervyn.thermaducts.ThermaDucts;
import com.mervyn.thermaducts.blockentity.DuctBlockEntity;
import com.mervyn.thermaducts.core.duct.DuctToken;
import com.mervyn.thermaducts.duct.transport.TransportDuctUnit;
import com.mervyn.thermaducts.menu.TransportMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TransportRequestPayload(BlockPos origin, BlockPos destination)
    implements CustomPacketPayload {

  public static final Type<TransportRequestPayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath(ThermaDucts.MODID, "transport_request"));

  public static final StreamCodec<FriendlyByteBuf, TransportRequestPayload> STREAM_CODEC =
      StreamCodec.of(TransportRequestPayload::write, TransportRequestPayload::read);

  private static void write(FriendlyByteBuf buf, TransportRequestPayload payload) {
    buf.writeBlockPos(payload.origin);
    buf.writeBlockPos(payload.destination);
  }

  private static TransportRequestPayload read(FriendlyByteBuf buf) {
    return new TransportRequestPayload(buf.readBlockPos(), buf.readBlockPos());
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handle(TransportRequestPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(
        () -> {
          var player = ctx.player();
          if (player.isPassenger()) return;
          // Only depart from the endpoint whose menu this player has open and is standing at.
          if (!(player.containerMenu instanceof TransportMenu menu)
              || !menu.stillValid(player)
              || !menu.getDuctPos().equals(payload.origin)) {
            return;
          }
          if (player.level().getBlockEntity(payload.origin) instanceof DuctBlockEntity ductBE) {
            if (ductBE.getDuctUnit(DuctToken.TRANSPORT) instanceof TransportDuctUnit unit) {
              if (unit.isEndpoint()) {
                unit.sendPlayerToDest(player, payload.destination);
                player.closeContainer();
              }
            }
          }
        });
  }
}
