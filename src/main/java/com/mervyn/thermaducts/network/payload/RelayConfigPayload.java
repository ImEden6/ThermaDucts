package com.mervyn.thermaducts.network.payload;

import com.mervyn.thermaducts.ThermaDucts;
import com.mervyn.thermaducts.attachment.relay.Relay;
import com.mervyn.thermaducts.blockentity.DuctBlockEntity;
import com.mervyn.thermaducts.menu.RelayMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RelayConfigPayload(BlockPos pos, int side, int action, int value)
    implements CustomPacketPayload {

  public static final int ACTION_SET_TYPE = 0;
  public static final int ACTION_SET_INVERT = 1;
  public static final int ACTION_SET_COLOR = 2;
  public static final int ACTION_SET_THRESHOLD = 3;

  public static final Type<RelayConfigPayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath(ThermaDucts.MODID, "relay_config"));

  public static final StreamCodec<FriendlyByteBuf, RelayConfigPayload> STREAM_CODEC =
      StreamCodec.of(RelayConfigPayload::write, RelayConfigPayload::read);

  private static void write(FriendlyByteBuf buf, RelayConfigPayload payload) {
    buf.writeBlockPos(payload.pos);
    buf.writeByte(payload.side);
    buf.writeByte(payload.action);
    buf.writeVarInt(payload.value);
  }

  private static RelayConfigPayload read(FriendlyByteBuf buf) {
    return new RelayConfigPayload(
        buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readVarInt());
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handle(RelayConfigPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(
        () -> {
          var player = ctx.player();
          // Only act on the relay whose menu this player has open and is still near.
          if (!(player.containerMenu instanceof RelayMenu menu) || !menu.stillValid(player)) {
            return;
          }
          Relay relay = menu.getRelay();
          DuctBlockEntity ductBE = relay.getParent();
          if (!ductBE.getBlockPos().equals(payload.pos)
              || relay.getSide().ordinal() != payload.side) {
            return;
          }

          switch (payload.action) {
            case ACTION_SET_TYPE -> relay.setType(payload.value);
            case ACTION_SET_INVERT -> relay.setInvertMode(payload.value);
            case ACTION_SET_COLOR -> relay.setColor(payload.value);
            case ACTION_SET_THRESHOLD -> relay.setThreshold(payload.value);
            default -> {
              return;
            }
          }

          ductBE.setChanged();
          player
              .level()
              .sendBlockUpdated(payload.pos, ductBE.getBlockState(), ductBE.getBlockState(), 3);
        });
  }
}
