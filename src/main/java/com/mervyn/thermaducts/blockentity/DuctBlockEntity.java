package com.mervyn.thermaducts.blockentity;

import com.mervyn.thermaducts.block.DuctBlock;
import com.mervyn.thermaducts.core.attachment.Attachment;
import com.mervyn.thermaducts.core.attachment.AttachmentRegistry;
import com.mervyn.thermaducts.core.attachment.ConnectionBase;
import com.mervyn.thermaducts.core.duct.DuctToken;
import com.mervyn.thermaducts.core.duct.DuctUnit;
import com.mervyn.thermaducts.core.network.ConnectionType;
import com.mervyn.thermaducts.core.network.NetworkManager;
import com.mervyn.thermaducts.duct.fluid.FluidDuctUnit;
import com.mervyn.thermaducts.duct.item.ItemDuctUnit;
import com.mervyn.thermaducts.item.WrenchItem;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

public abstract class DuctBlockEntity extends BlockEntity {

  protected final Map<DuctToken, DuctUnit<?, ?, ?>> ductUnits = new EnumMap<>(DuctToken.class);
  protected final ConnectionType[] connectionTypes = new ConnectionType[6];
  protected final Attachment[] attachments = new Attachment[6];

  protected DuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
    for (int i = 0; i < 6; i++) {
      connectionTypes[i] = ConnectionType.NORMAL;
    }
  }

  protected abstract void initDuctUnits();

  public static void serverTick(Level level, BlockPos pos, BlockState state, DuctBlockEntity be) {
    for (Attachment att : be.attachments) {
      if (att != null) {
        att.tick();
      }
    }
  }

  public static void clientTick(Level level, BlockPos pos, BlockState state, DuctBlockEntity be) {
    for (DuctUnit<?, ?, ?> unit : be.ductUnits.values()) {
      if (unit instanceof ItemDuctUnit itemUnit) {
        itemUnit.tickClientTravelingItems();
      }
    }
  }

  protected void addDuctUnit(DuctUnit<?, ?, ?> unit) {
    ductUnits.put(unit.getToken(), unit);
  }

  public DuctUnit<?, ?, ?> getDuctUnit(DuctToken token) {
    return ductUnits.get(token);
  }

  public Map<DuctToken, DuctUnit<?, ?, ?>> getDuctUnits() {
    return ductUnits;
  }

  public ConnectionType getConnectionType(Direction side) {
    return connectionTypes[side.ordinal()];
  }

  public void setConnectionType(Direction side, ConnectionType type) {
    connectionTypes[side.ordinal()] = type;
    onNeighborChanged();
    setChanged();
    if (level != null && !level.isClientSide()) {
      refreshVisualState();
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  public Attachment[] getAttachments() {
    return attachments;
  }

  public Attachment getAttachment(Direction side) {
    return attachments[side.ordinal()];
  }

  public void setAttachment(Direction side, Attachment attachment) {
    attachments[side.ordinal()] = attachment;
    setChanged();
    if (level != null && !level.isClientSide()) {
      onNeighborChanged();
      refreshVisualState();
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  public void removeAttachment(Direction side) {
    attachments[side.ordinal()] = null;
    setChanged();
    if (level != null && !level.isClientSide()) {
      onNeighborChanged();
      refreshVisualState();
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  @Override
  public void onLoad() {
    super.onLoad();
    for (DuctUnit<?, ?, ?> unit : ductUnits.values()) {
      unit.updateCaches();
    }
    if (level instanceof ServerLevel serverLevel) {
      for (DuctUnit<?, ?, ?> unit : ductUnits.values()) {
        NetworkManager.get(serverLevel).scheduleFormation(unit);
      }
    }
  }

  public void onPlaced() {
    if (level instanceof ServerLevel serverLevel) {
      for (DuctUnit<?, ?, ?> unit : ductUnits.values()) {
        unit.updateCaches();
        NetworkManager.get(serverLevel).scheduleFormation(unit);
      }
    }
  }

  public void onBroken() {
    for (DuctUnit<?, ?, ?> unit : ductUnits.values()) {
      if (unit instanceof ItemDuctUnit itemUnit) {
        itemUnit.dropAllItems();
      }
      if (unit instanceof FluidDuctUnit fluidUnit) {
        fluidUnit.markBeingDestroyed();
      }
      unit.invalidate();
    }
    if (level != null && !level.isClientSide()) {
      for (int i = 0; i < attachments.length; i++) {
        if (attachments[i] != null) {
          Block.popResource(level, worldPosition, WrenchItem.getAttachmentDrop(attachments[i]));
          attachments[i] = null;
        }
      }
    }
  }

  @Override
  public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    super.preRemoveSideEffects(pos, state);
    onBroken();
  }

  public void onNeighborChanged() {
    if (level instanceof ServerLevel serverLevel) {
      boolean connectionTypesChanged = normalizeDisconnectedBlockedSides();
      for (DuctUnit<?, ?, ?> unit : ductUnits.values()) {
        unit.invalidate();
        unit.updateCaches();
        NetworkManager.get(serverLevel).scheduleFormation(unit);
      }
      boolean powered = level.hasNeighborSignal(worldPosition);
      boolean powerStateChanged = false;
      for (Attachment att : attachments) {
        if (att instanceof ConnectionBase conn && conn.updatePowerState(powered)) {
          powerStateChanged = true;
        }
      }
      level.invalidateCapabilities(worldPosition);
      if (connectionTypesChanged) {
        setChanged();
        refreshVisualState();
      }
      if (powerStateChanged) {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
      }
    }
  }

  private boolean normalizeDisconnectedBlockedSides() {
    if (level == null) {
      return false;
    }

    boolean changed = false;
    for (Direction side : Direction.values()) {
      if (connectionTypes[side.ordinal()] != ConnectionType.BLOCKED) {
        continue;
      }

      BlockPos neighborPos = worldPosition.relative(side);
      if (!level.isLoaded(neighborPos)) {
        continue;
      }

      BlockEntity neighbor = level.getBlockEntity(neighborPos);
      if (neighbor == null) {
        connectionTypes[side.ordinal()] = ConnectionType.NORMAL;
        changed = true;
      }
    }
    return changed;
  }

  private void refreshVisualState() {
    if (level == null || level.isClientSide()) {
      return;
    }
    BlockState state = getBlockState();
    if (!(state.getBlock() instanceof DuctBlock ductBlock)) {
      return;
    }
    BlockState updatedState = ductBlock.updateVisualConnections(level, worldPosition, state);
    if (updatedState != state) {
      level.setBlock(worldPosition, updatedState, Block.UPDATE_CLIENTS);
    }
  }

  @Override
  public void setRemoved() {
    for (DuctUnit<?, ?, ?> unit : ductUnits.values()) {
      unit.invalidate();
    }
    super.setRemoved();
  }

  @Override
  public void onChunkUnloaded() {
    for (DuctUnit<?, ?, ?> unit : ductUnits.values()) {
      unit.invalidate();
    }
    super.onChunkUnloaded();
  }

  @Override
  protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
    super.saveAdditional(output);

    CompoundTag tag = new CompoundTag();
    byte[] connections = new byte[6];
    for (int i = 0; i < 6; i++) {
      connections[i] = (byte) connectionTypes[i].ordinal();
    }
    tag.putByteArray("Connections", connections);

    for (var entry : ductUnits.entrySet()) {
      CompoundTag unitTag = new CompoundTag();
      entry.getValue().saveAdditional(unitTag, this.level.registryAccess());
      if (!unitTag.isEmpty()) {
        tag.put("Unit_" + entry.getKey().name(), unitTag);
      }
    }

    CompoundTag attachmentsWrapper = new CompoundTag();
    ListTag attachmentList = new ListTag();
    for (int i = 0; i < 6; i++) {
      if (attachments[i] != null) {
        attachmentList.add(attachments[i].save(this.level.registryAccess()));
      } else {
        attachmentList.add(new CompoundTag());
      }
    }
    attachmentsWrapper.put("list", attachmentList);
    tag.put("AttachmentsWrapper", attachmentsWrapper);

    output.store("thermaductsData", CompoundTag.CODEC, tag);
  }

  @Override
  @SuppressWarnings("deprecation")
  protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
    super.loadAdditional(input);

    CompoundTag tag = input.read("thermaductsData", CompoundTag.CODEC).orElse(new CompoundTag());

    if (tag.contains("Connections")) {
      byte[] connections = tag.getByteArray("Connections").orElse(new byte[0]);
      for (int i = 0; i < Math.min(6, connections.length); i++) {
        connectionTypes[i] = ConnectionType.values()[connections[i]];
      }
    }

    for (var entry : ductUnits.entrySet()) {
      String key = "Unit_" + entry.getKey().name();
      if (tag.contains(key)) {
        tag.getCompound(key)
            .ifPresent(
                unitTag -> {
                  entry.getValue().loadAdditional(unitTag, input.lookup());
                });
      }
    }

    if (tag.contains("AttachmentsWrapper")) {
      tag.getCompound("AttachmentsWrapper")
          .ifPresent(
              wrapper -> {
                wrapper
                    .getList("list")
                    .ifPresent(
                        attachmentList -> {
                          for (int i = 0; i < Math.min(6, attachmentList.size()); i++) {
                            final int index = i;
                            attachmentList
                                .getCompound(index)
                                .ifPresent(
                                    attTag -> {
                                      if (attTag.isEmpty() || !attTag.contains("Id")) {
                                        attachments[index] = null;
                                      } else {
                                        Identifier id =
                                            Identifier.parse(attTag.getString("Id").orElse(""));
                                        attachments[index] =
                                            AttachmentRegistry.create(
                                                id, this, Direction.values()[index], attTag);
                                        if (attachments[index] != null) {
                                          attachments[index].load(attTag, input.lookup());
                                        }
                                      }
                                    });
                          }
                        });
              });
    }
  }

  @Override
  public void handleUpdateTag(ValueInput input) {
    super.handleUpdateTag(input);
    if (level != null && level.isClientSide()) {
      for (DuctUnit<?, ?, ?> unit : ductUnits.values()) {
        unit.updateCaches();
      }
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  @Override
  public void onDataPacket(Connection net, ValueInput input) {
    super.onDataPacket(net, input);
    if (level != null && level.isClientSide()) {
      for (DuctUnit<?, ?, ?> unit : ductUnits.values()) {
        unit.updateCaches();
      }
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return saveWithoutMetadata(registries);
  }

  @Override
  public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
