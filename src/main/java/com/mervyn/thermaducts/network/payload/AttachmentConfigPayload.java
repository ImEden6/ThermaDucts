package com.mervyn.thermaducts.network.payload;

import com.mervyn.thermaducts.ThermaDucts;
import com.mervyn.thermaducts.blockentity.DuctBlockEntity;
import com.mervyn.thermaducts.core.attachment.ConnectionBase;
import com.mervyn.thermaducts.core.attachment.RedstoneMode;
import com.mervyn.thermaducts.menu.AttachmentMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AttachmentConfigPayload(BlockPos pos, int side, int action, int value)
    implements CustomPacketPayload {

  public static final int ACTION_SET_WHITELIST = 0;
  public static final int ACTION_SET_MATCH_COMPONENTS = 1;
  public static final int ACTION_SET_MATCH_MOD = 2;
  public static final int ACTION_SET_REDSTONE_MODE = 3;
  public static final int ACTION_SET_MAX_STOCK = 4;
  public static final int ACTION_SET_ROUTE_TYPE = 5;

  public static final Type<AttachmentConfigPayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath(ThermaDucts.MODID, "attachment_config"));

  public static final StreamCodec<FriendlyByteBuf, AttachmentConfigPayload> STREAM_CODEC =
      StreamCodec.of(AttachmentConfigPayload::write, AttachmentConfigPayload::read);

  private static void write(FriendlyByteBuf buf, AttachmentConfigPayload payload) {
    buf.writeBlockPos(payload.pos);
    buf.writeByte(payload.side);
    buf.writeByte(payload.action);
    buf.writeVarInt(payload.value);
  }

  private static AttachmentConfigPayload read(FriendlyByteBuf buf) {
    return new AttachmentConfigPayload(
        buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readVarInt());
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handle(AttachmentConfigPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(
        () -> {
          var player = ctx.player();
          // Only act on the attachment whose menu this player has open and is still near.
          if (!(player.containerMenu instanceof AttachmentMenu menu) || !menu.stillValid(player)) {
            return;
          }
          ConnectionBase conn = menu.getAttachment();
          DuctBlockEntity ductBE = conn.getParent();
          if (!ductBE.getBlockPos().equals(payload.pos)
              || menu.getSide().ordinal() != payload.side) {
            return;
          }
          switch (payload.action) {
            case ACTION_SET_WHITELIST -> conn.getFilter().setWhitelist(payload.value == 1);
            case ACTION_SET_MATCH_COMPONENTS ->
                conn.getFilter().setMatchComponents(payload.value == 1);
            case ACTION_SET_MATCH_MOD -> conn.getFilter().setMatchModId(payload.value == 1);
            case ACTION_SET_REDSTONE_MODE -> {
              RedstoneMode[] modes = RedstoneMode.values();
              if (payload.value < 0 || payload.value >= modes.length) {
                return;
              }
              conn.setRedstoneMode(modes[payload.value]);
            }
            case ACTION_SET_MAX_STOCK -> conn.getFilter().setMaxStock(payload.value);
            case ACTION_SET_ROUTE_TYPE -> conn.getFilter().setRouteType(payload.value);
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
