/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.BitSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.filler.IFilledTemplate;

import buildcraft.lib.misc.VecUtil;

public class Template extends Snapshot {
    public BitSet data;

    @Override
    public Template copy() {
        Template template = new Template();
        template.size = size;
        template.facing = facing;
        template.offset = offset;
        template.data = (BitSet) data.clone();
        template.computeKey();
        return template;
    }

    public IFilledTemplate getFilledTemplate() {
        BlockPos max = size.subtract(VecUtil.POS_ONE);
        // requires #posToIndex to have continuous X values
        return new IFilledTemplate() {
            private void checkPos(int x, int y, int z) {
                if (x < 0 || y < 0 || z < 0 || x >= size.getX() || y >= size.getY() || z >= size.getZ()) {
                    throw new IllegalArgumentException("Size: " + size + ", pos: " + new BlockPos(x, y, z));
                }
            }

            @Override
            public BlockPos getSize() {
                return size;
            }

            @Override
            public BlockPos getMax() {
                return max;
            }

            @Override
            public void set(int x, int y, int z, boolean value) {
                checkPos(x, y, z);
                data.set(posToIndex(x, y, z), value);
            }

            @Override
            public boolean get(int x, int y, int z) {
                checkPos(x, y, z);
                return data.get(posToIndex(x, y, z));
            }

            @Override
            public void setAreaXY(int fromX, int toX, int fromY, int toY, int z, boolean value) {
                checkPos(fromX, fromY, z);
                checkPos(toX, toY, z);
                for (int y = fromY; y <= toY; y++) {
                    setLineX(fromX, toX, y, z, true);
                }
            }

            @Override
            public void setAreaYZ(int x, int fromY, int toY, int fromZ, int toZ, boolean value) {
                checkPos(x, fromY, fromZ);
                checkPos(x, toY, toZ);
                for (int z = fromZ; z <= toZ; z++) {
                    setLineY(x, fromY, toY, z, value);
                }
            }

            @Override
            public void setAreaXZ(int fromX, int toX, int y, int fromZ, int toZ, boolean value) {
                checkPos(fromX, y, fromZ);
                checkPos(toX, y, toZ);
                for (int z = fromZ; z <= toZ; z++) {
                    setLineX(fromX, toX, y, z, true);
                }
            }

            @Override
            public void setLineX(int fromX, int toX, int y, int z, boolean value) {
                checkPos(fromX, y, z);
                checkPos(toX, y, z);
                data.set(posToIndex(fromX, y, z), posToIndex(toX, y, z) + 1, value);
            }

            @Override
            public void setLineY(int x, int fromY, int toY, int z, boolean value) {
                checkPos(x, fromY, z);
                checkPos(x, toY, z);
                for (int y = fromY; y <= toY; y++) {
                    set(x, y, z, value);
                }
            }

            @Override
            public void setLineZ(int x, int y, int fromZ, int toZ, boolean value) {
                checkPos(x, y, fromZ);
                checkPos(x, y, toZ);
                for (int z = fromZ; z <= toZ; z++) {
                    set(x, y, z, value);
                }
            }

            @Override
            public void setAll(boolean value) {
                data.set(0, getDataSize(), value);
            }

            @Override
            public String toString() {
                return createString();
            }
        };
    }

    public void invert() {
        data.flip(0, getDataSize());
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = super.serializeNBT();
        nbt.setByteArray("data", data.toByteArray());
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) throws InvalidInputDataException {
        super.deserializeNBT(nbt);
        data = BitSet.valueOf(nbt.getByteArray("data"));
        if (data.length() > getDataSize()) {
            throw new InvalidInputDataException(
                "Serialized data has length of " + data.length() +
                    ", but we expected at most " +
                    getDataSize() + " (" + size.toString() + ")"
            );
        }
    }

    @Override
    public EnumSnapshotType getType() {
        return EnumSnapshotType.TEMPLATE;
    }

    public class BuildingInfo extends Snapshot.BuildingInfo {
        public BuildingInfo(BlockPos basePos, Rotation rotation) {
            super(basePos, rotation);
        }

        @Override
        public Template getSnapshot() {
            return Template.this;
        }
    }
}
