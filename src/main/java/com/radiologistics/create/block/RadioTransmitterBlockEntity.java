package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import com.radiologistics.create.radio.RadioNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RadioTransmitterBlockEntity extends BlockEntity {
    private String channel = "0";
    private String message = "signal";
    private boolean wasPowered = false;

    private BlockPos linkedAntennaPos = null;

    public RadioTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO_TRANSMITTER.get(), pos, state);
    }

    public String getChannel() { return channel; }
    public void setChannel(String channel) {
        this.channel = com.radiologistics.create.radio.RadioNetworkManager.sanitizeChannel(channel);
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public void setLinkedAntennaPos(BlockPos pos) {
        this.linkedAntennaPos = pos;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public BlockPos getLinkedAntennaPos() {
        return linkedAntennaPos;
    }

    public void updateRedstone(boolean isPowered) {
        if (isPowered && !wasPowered) {
            transmit(message);
            if (level != null && !level.isClientSide()) {
                level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
            }
        } else if (!isPowered && wasPowered) {
            transmit("");
        }
        wasPowered = isPowered;
        setChanged();
    }

    public int getAttachedAntennaHeight() {
        if (level == null) return 0;
        if (linkedAntennaPos != null) {
            BlockEntity be = level.getBlockEntity(linkedAntennaPos);
            if (be instanceof AntennaBlockEntity antenna) {
                return antenna.getAntennaHeight();
            }
        }
        // Fallback to adjacent search if not linked via target
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos adjacent = worldPosition.relative(dir);
            if (level.getBlockState(adjacent).getBlock() instanceof AntennaBlock) {
                BlockEntity be = level.getBlockEntity(adjacent);
                if (be instanceof AntennaBlockEntity antenna) {
                    return antenna.getAntennaHeight();
                }
            }
        }
        return 0;
    }

    public void transmit(String msg) {
        if (level != null && !level.isClientSide() && level.getServer() != null) {
            int antHeight = getAttachedAntennaHeight();
            RadioNetworkManager.broadcast(level.getServer(), channel, msg, level.dimension(), worldPosition, antHeight);
        }
    }

    /** Convenience: transmits the currently stored message. */
    public void transmit() {
        transmit(message);
    }


    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("channel", channel);
        tag.putString("message", message);
        tag.putBoolean("wasPowered", wasPowered);
        if (linkedAntennaPos != null) {
            CompoundTag offsetTag = new CompoundTag();
            offsetTag.putInt("dx", linkedAntennaPos.getX() - worldPosition.getX());
            offsetTag.putInt("dy", linkedAntennaPos.getY() - worldPosition.getY());
            offsetTag.putInt("dz", linkedAntennaPos.getZ() - worldPosition.getZ());
            tag.put("antennaPosRelative", offsetTag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = tag.getString("channel");
        message = tag.getString("message");
        wasPowered = tag.getBoolean("wasPowered");
        if (tag.contains("antennaPosRelative")) {
            CompoundTag offsetTag = tag.getCompound("antennaPosRelative");
            int dx = offsetTag.getInt("dx");
            int dy = offsetTag.getInt("dy");
            int dz = offsetTag.getInt("dz");
            linkedAntennaPos = new BlockPos(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
        } else {
            linkedAntennaPos = null;
        }
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
        }
    }

    // Client-only fields for Morse playback
    private java.util.List<Boolean> morseTimeline = new java.util.ArrayList<>();
    private int timelineIndex = -1;
    private float currentLeverRotation = 0f;
    private int soundCooldown = 0;

    public float getCurrentLeverRotation() {
        return currentLeverRotation;
    }

    public void startMorsePlayback() {
        morseTimeline.clear();
        timelineIndex = 0;
        
        String msg = this.message;
        if (msg == null || msg.isEmpty()) {
            msg = "signal";
        }
        
        for (int i = 0; i < msg.length(); i++) {
            char c = Character.toLowerCase(msg.charAt(i));
            String morse = getMorseCode(c);
            if (morse != null) {
                for (int j = 0; j < morse.length(); j++) {
                    char symbol = morse.charAt(j);
                    if (symbol == '.') {
                        for (int k = 0; k < 3; k++) morseTimeline.add(true);
                        for (int k = 0; k < 3; k++) morseTimeline.add(false);
                    } else if (symbol == '-') {
                        for (int k = 0; k < 9; k++) morseTimeline.add(true);
                        for (int k = 0; k < 3; k++) morseTimeline.add(false);
                    }
                }
                for (int k = 0; k < 3; k++) morseTimeline.add(false); // letter gap
            } else if (c == ' ') {
                for (int k = 0; k < 9; k++) morseTimeline.add(false); // word gap
            }
        }
    }

    private static String getMorseCode(char c) {
        return switch (c) {
            case 'a' -> ".-"; case 'b' -> "-..."; case 'c' -> "-.-."; case 'd' -> "-..";
            case 'e' -> "."; case 'f' -> "..-."; case 'g' -> "--."; case 'h' -> "....";
            case 'i' -> ".."; case 'j' -> ".---"; case 'k' -> "-.-"; case 'l' -> ".-..";
            case 'm' -> "--"; case 'n' -> "-."; case 'o' -> "---"; case 'p' -> ".--.";
            case 'q' -> "--.-"; case 'r' -> ".-."; case 's' -> "..."; case 't' -> "-";
            case 'u' -> "..-"; case 'v' -> "...-"; case 'w' -> ".--"; case 'x' -> "-..-";
            case 'y' -> "-.--"; case 'z' -> "--..";
            case '1' -> ".----"; case '2' -> "..---"; case '3' -> "...--"; case '4' -> "....-";
            case '5' -> "....."; case '6' -> "-...."; case '7' -> "--..."; case '8' -> "---..";
            case '9' -> "----."; case '0' -> "-----";
            default -> null;
        };
    }

    public void clientTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        if (timelineIndex >= 0 && timelineIndex < morseTimeline.size()) {
            boolean active = morseTimeline.get(timelineIndex);
            if (active) {
                currentLeverRotation = Math.min(10f, currentLeverRotation + 3.33f);
                if (soundCooldown <= 0) {
                    level.playLocalSound(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HARP.value(),
                        net.minecraft.sounds.SoundSource.BLOCKS,
                        0.5f, 1.6f, false
                    );
                    soundCooldown = 3;
                }
            } else {
                currentLeverRotation = Math.max(0f, currentLeverRotation - 3.33f);
            }
            if (soundCooldown > 0) {
                soundCooldown--;
            }
            timelineIndex++;
            if (timelineIndex >= morseTimeline.size()) {
                timelineIndex = -1;
            }
        } else {
            currentLeverRotation = Math.max(0f, currentLeverRotation - 3.33f);
            soundCooldown = 0;
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == 1) {
            if (level != null && level.isClientSide()) {
                startMorsePlayback();
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }
}
