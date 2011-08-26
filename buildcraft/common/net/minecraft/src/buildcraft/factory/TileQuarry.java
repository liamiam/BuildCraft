package net.minecraft.src.buildcraft.factory;

import net.minecraft.src.Block;
import net.minecraft.src.BuildCraftBlockUtil;
import net.minecraft.src.BuildCraftCore;
import net.minecraft.src.BuildCraftFactory;
import net.minecraft.src.EntityItem;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.Packet230ModLoader;
import net.minecraft.src.buildcraft.api.APIProxy;
import net.minecraft.src.buildcraft.api.IAreaProvider;
import net.minecraft.src.buildcraft.api.IPowerReceptor;
import net.minecraft.src.buildcraft.api.LaserKind;
import net.minecraft.src.buildcraft.api.Orientations;
import net.minecraft.src.buildcraft.api.PowerProvider;
import net.minecraft.src.buildcraft.core.BlockContents;
import net.minecraft.src.buildcraft.core.BlockIndex;
import net.minecraft.src.buildcraft.core.BluePrint;
import net.minecraft.src.buildcraft.core.BluePrintBuilder;
import net.minecraft.src.buildcraft.core.DefaultAreaProvider;
import net.minecraft.src.buildcraft.core.EntityBlock;
import net.minecraft.src.buildcraft.core.IMachine;
import net.minecraft.src.buildcraft.core.PacketIds;
import net.minecraft.src.buildcraft.core.StackUtil;
import net.minecraft.src.buildcraft.core.TileBuildCraft;
import net.minecraft.src.buildcraft.core.TileNetworkData;
import net.minecraft.src.buildcraft.core.Utils;

public class TileQuarry extends TileBuildCraft implements IArmListener,
		IMachine, IPowerReceptor {
	
	BlockContents nextBlockForBluePrint = null;
	boolean isDigging = false;
	
	public @TileNetworkData boolean inProcess = false;		
	public @TileNetworkData (packetFilter = {PacketIds.TileDescription}) int xMin = -1, zMin = -1;
	public @TileNetworkData (packetFilter = {PacketIds.TileDescription}) int xSize = -1, ySize = -1, zSize = -1;
	
	// TODO instead of synchronizing the arm, which can be null, synchronize
	// data and pass them to the arm if not null
	public @TileNetworkData	EntityMechanicalArm arm;	
	
	boolean loadArm = false;
	
	int targetX, targetY, targetZ;
	EntityBlock [] lasers;
	
	BluePrintBuilder bluePrintBuilder;
	
	public @TileNetworkData PowerProvider powerProvider;
	
	public static int MAX_ENERGY = 7000;
	
	public TileQuarry() {
		powerProvider = BuildCraftCore.powerFramework.createPowerProvider();
		powerProvider.configure(20, 25, 25, 25, MAX_ENERGY);
	}
	
    public void createUtilsIfNeeded () {
    	if (bluePrintBuilder == null) {
    		if (xSize == -1) {
    			setBoundaries(loadDefaultBoundaries);
    		}
    		    
    		initializeBluePrintBuilder();
    	}    	
    	
    	nextBlockForBluePrint = bluePrintBuilder.findNextBlock(worldObj);
    	
    	if (bluePrintBuilder.done) {    	
    		deleteLasers ();
    		
    		if (arm == null) {
    			createArm ();
    		}

    		if (loadArm) {
    			arm.joinToWorld(worldObj);
    			loadArm = false;
    			
    			if (findTarget(false)) {    				
    	    		isDigging = true;
    	    	}
    		}
    	} else {    		
    		createLasers();    		
    		isDigging = true;
    	}
    }
	
	private boolean loadDefaultBoundaries = false;
	
	private void createArm () {
		arm = new EntityMechanicalArm(worldObj, xMin + Utils.pipeMaxPos,
				yCoord + bluePrintBuilder.bluePrint.sizeY - 1
						+ Utils.pipeMinPos, zMin + Utils.pipeMaxPos,
				bluePrintBuilder.bluePrint.sizeX - 2 + Utils.pipeMinPos * 2,
				bluePrintBuilder.bluePrint.sizeZ - 2 + Utils.pipeMinPos * 2);

		arm.listener = this;
		loadArm = true;
	}
	
	private void createLasers () {
		if (!APIProxy.isServerSide()) {
			if (lasers == null) {				
				lasers = Utils.createLaserBox(worldObj, xMin, yCoord, zMin,
						xMin + xSize - 1, yCoord + ySize - 1, zMin + zSize - 1,
						LaserKind.Stripes);
			}
		}
	}
	
	private void deleteLasers () {
		if (lasers != null) {
			for (EntityBlock l : lasers) {
				APIProxy.removeEntity(l);
			}
			
			lasers = null;
		}
	}

	@Override
	public void updateEntity () {
		super.updateEntity();
		
		if (inProcess && arm != null) {
			arm.speed = 0;
			int energyToUse = 2 + powerProvider.energyStored / 1000;
			
			int energy = powerProvider
			.useEnergy(energyToUse, energyToUse, true);
						
			if (energy > 0) {
				arm.doMove(0.015 + (float) energy / 200F);
				return;
			}
		}		
		
	}
	
	public void doWork() {				
		if (APIProxy.isClient(worldObj)) {
			return;
		}
		
		if (inProcess) {			
			return;
		}		
		
		if (!isDigging) {
			return;
		}
		
	    createUtilsIfNeeded();
	    
	    if (bluePrintBuilder == null) {
	    	return;
	    }	    	    
	    
    	if (bluePrintBuilder.done && nextBlockForBluePrint != null) {
    		// In this case, the Quarry has been broken. Repair it.
    		bluePrintBuilder.done = false;
    		
    		createLasers();
    	}
	    
		if (!bluePrintBuilder.done) {
			// configuration for building phase
			powerProvider.configure(20, 25, 25, 25, MAX_ENERGY);
			
			if (powerProvider.useEnergy(25, 25, true) != 25) {
		    	return;
		    }
			
			powerProvider.timeTracker.markTime(worldObj);
			BlockContents contents = bluePrintBuilder.findNextBlock(worldObj);
			
			int blockId = worldObj.getBlockId(contents.x, contents.y, contents.z);
						
			if (contents != null) {
				if (!Utils.softBlock(blockId)) {
					Utils.breakBlock (worldObj, contents.x, contents.y, contents.z);
				} else if (contents.blockId != 0) {
					worldObj.setBlockWithNotify(contents.x, contents.y, contents.z,
							contents.blockId);
				}				
			}
			
			return;
		} 	  	
		
		// configuration for digging phase
		powerProvider.configure(20, 30, 200, 50, MAX_ENERGY);
		
		if (!findTarget(true)) {
			arm.setTarget (xMin + arm.sizeX / 2, yCoord + 2, zMin + arm.sizeX / 2);
						
			isDigging = false;			
		}
		
		inProcess = true;
		
		if (APIProxy.isServerSide()) {
			sendNetworkUpdate ();
		}
	}

	public boolean findTarget (boolean doSet) {
		boolean[][] blockedColumns = new boolean[bluePrintBuilder.bluePrint.sizeX - 2][bluePrintBuilder.bluePrint.sizeZ - 2];
		
		for (int searchX = 0; searchX < bluePrintBuilder.bluePrint.sizeX - 2; ++searchX) {
			for (int searchZ = 0; searchZ < bluePrintBuilder.bluePrint.sizeZ - 2; ++searchZ) {
				blockedColumns [searchX][searchZ] = false;
			}
		}
		
		for (int searchY = yCoord + 3; searchY >= 0; --searchY) {
			int startX, endX, incX;
			
			if (searchY % 2 == 0) {
				startX = 0;
				endX = bluePrintBuilder.bluePrint.sizeX - 2;
				incX = 1;
			} else {
				startX = bluePrintBuilder.bluePrint.sizeX - 3;
				endX = -1;
				incX = -1;
			}
			
			for (int searchX = startX; searchX != endX; searchX += incX) {
				int startZ, endZ, incZ;
				
				if (searchX % 2 == searchY % 2) {
					startZ = 0;
					endZ = bluePrintBuilder.bluePrint.sizeZ - 2;
					incZ = 1;
				} else {
					startZ = bluePrintBuilder.bluePrint.sizeZ - 3;
					endZ = -1;
					incZ = -1;
				}
								
				for (int searchZ = startZ; searchZ != endZ; searchZ += incZ) {
					if (!blockedColumns [searchX][searchZ]) {
						int bx = xMin + searchX + 1, by = searchY, bz = zMin + searchZ + 1;
						
						int blockId = worldObj.getBlockId(bx, by, bz);
						
						if (blockDig (blockId)) {		
							blockedColumns [searchX][searchZ] = true;						
						} else if (canDig(blockId)) {
							if (doSet) {
								arm.setTarget (bx, by + 1, bz);

								targetX = bx;
								targetY = by;
								targetZ = bz;
							}
							
							return true;
						}
					}
				}
			}
		}

		return false;
	}
	
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		
		BuildCraftCore.powerFramework.loadPowerProvider(this, nbttagcompound);

		if (nbttagcompound.hasKey("xSize")) {
			xMin = nbttagcompound.getInteger("xMin");
			zMin = nbttagcompound.getInteger("zMin");

			xSize = nbttagcompound.getInteger("xSize");
			ySize = nbttagcompound.getInteger("ySize");
			zSize = nbttagcompound.getInteger("zSize");
			
			loadDefaultBoundaries = false;
		} else {
			// This is a legacy save, compute boundaries
			
			loadDefaultBoundaries = true;
		}				
		
		targetX = nbttagcompound.getInteger("targetX");
		targetY = nbttagcompound.getInteger("targetY");
		targetZ = nbttagcompound.getInteger("targetZ");
		
		if (nbttagcompound.getBoolean("hasArm")) {
			NBTTagCompound armStore = nbttagcompound.getCompoundTag("arm");
			arm = new EntityMechanicalArm(worldObj);
			arm.readFromNBT(armStore);
			arm.listener = this;

			loadArm = true;
		}
	}

	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);		
		
		BuildCraftCore.powerFramework.savePowerProvider(this, nbttagcompound);
		
		nbttagcompound.setInteger("xMin", xMin);
		nbttagcompound.setInteger("zMin", zMin);
		
		nbttagcompound.setInteger("xSize", xSize);
		nbttagcompound.setInteger("ySize", ySize);
		nbttagcompound.setInteger("zSize", zSize);
		
		nbttagcompound.setInteger("targetX", targetX);
		nbttagcompound.setInteger("targetY", targetY);
		nbttagcompound.setInteger("targetZ", targetZ);
		nbttagcompound.setBoolean("hasArm", arm != null);
		
		if (arm != null) {
			NBTTagCompound armStore = new NBTTagCompound();
			nbttagcompound.setTag("arm", armStore);
			arm.writeToNBT(armStore);
		}
	}
	
	
	@Override
	public void positionReached(EntityMechanicalArm arm) {
		inProcess = false;
		
		if (APIProxy.isClient(worldObj)) {
			return;
		}
		
		int i = targetX;
		int j = targetY;
		int k = targetZ;				
		
		int blockId = worldObj.getBlockId((int) i, (int) j, (int) k);
		
		if (canDig(blockId)) {
			powerProvider.timeTracker.markTime(worldObj);
			
			// Share this with mining well!			
			
			ItemStack stack = BuildCraftBlockUtil.getItemStackFromBlock(
					worldObj, i, j, k);

			if (stack != null) {
				boolean added = false;

				// First, try to add to a nearby chest

				StackUtil stackUtils = new StackUtil(stack);
				
				added = stackUtils.addToRandomInventory(this,
						Orientations.Unknown);

				if (!added || stackUtils.items.stackSize > 0) {
					added = Utils.addToRandomPipeEntry(this,
							Orientations.Unknown, stackUtils.items);
				}

				// Last, throw the object away

				if (!added) {
					float f = worldObj.rand.nextFloat() * 0.8F + 0.1F;
					float f1 = worldObj.rand.nextFloat() * 0.8F + 0.1F;
					float f2 = worldObj.rand.nextFloat() * 0.8F + 0.1F;

					EntityItem entityitem = new EntityItem(worldObj,
							(float) xCoord + f, (float) yCoord + f1 + 0.5F,
							(float) zCoord + f2, stackUtils.items);

					float f3 = 0.05F;
					entityitem.motionX = (float) worldObj.rand
					.nextGaussian() * f3;
					entityitem.motionY = (float) worldObj.rand
					.nextGaussian() * f3 + 1.0F;
					entityitem.motionZ = (float) worldObj.rand
					.nextGaussian() * f3;
					worldObj.entityJoinedWorld(entityitem);
				}				
			}
					
			worldObj.setBlockWithNotify((int) i, (int) j, (int) k, 0);
		}		
	}
	
	private boolean blockDig (int blockID) {
		return blockID == Block.bedrock.blockID
				|| blockID == Block.lavaStill.blockID
				|| blockID == Block.lavaMoving.blockID;
	}
	
	private boolean canDig(int blockID) {
		return !blockDig(blockID) 
				&& !Utils.softBlock(blockID)
				&& blockID != Block.snow.blockID;
	}
	
	@Override
	public void destroy () {
		if (arm != null) {
			arm.setEntityDead ();
		}
		
		deleteLasers();
	}

	@Override
	public boolean isActive() {
		return isDigging;
	}
	
	private void setBoundaries (boolean useDefault) {
		IAreaProvider a = null;
		
		if (!useDefault) {
			a = Utils.getNearbyAreaProvider(worldObj, xCoord, yCoord,
				zCoord);
		}
		
		if (a == null) {
			a = new DefaultAreaProvider (1, 1, 1, 11, 5, 11);
			
			useDefault = true;
		}
		
		xSize = a.xMax() - a.xMin() + 1;
		ySize = a.yMax() - a.yMin() + 1;
		zSize = a.zMax() - a.zMin() + 1;
		
		if (xSize < 3 || zSize < 3) {
			a = new DefaultAreaProvider (1, 1, 1, 11, 5, 11);
			
			useDefault = true;
		}
		
		xSize = a.xMax() - a.xMin() + 1;
		ySize = a.yMax() - a.yMin() + 1;
		zSize = a.zMax() - a.zMin() + 1;
		
		if (ySize < 5) {
			ySize = 5;
		}
		
		if (useDefault) {
			Orientations o = Orientations.values()[worldObj.getBlockMetadata(
					xCoord, yCoord, zCoord)].reverse();

			switch (o) {
			case XPos:
				xMin = xCoord + 1;
				zMin = zCoord - 4 - 1;
				break;
			case XNeg:
				xMin = xCoord - 9 - 2;
				zMin = zCoord - 4 - 1;
				break;
			case ZPos:
				xMin = xCoord - 4 - 1;
				zMin = zCoord + 1;
				break;
			case ZNeg:
				xMin = xCoord - 4 - 1;
				zMin = zCoord - 9 - 2;
				break;
			}
		} else {
			xMin = a.xMin();
			zMin = a.zMin();
		}
		
		a.removeFromWorld();
	}
	
	private void initializeBluePrintBuilder () {
		BluePrint bluePrint = new BluePrint(xSize, ySize, zSize);	
	
		for (int i = 0; i < bluePrint.sizeX; ++i) {
			for (int j = 0; j < bluePrint.sizeY; ++j) {
				for (int k = 0; k < bluePrint.sizeZ; ++k) {
					bluePrint.setBlockId(i, j, k, 0);
				}
			}
		}

		for (int it = 0; it < 2; it++) {
			for (int i = 0; i < bluePrint.sizeX; ++i) {
				bluePrint.setBlockId(i, it * (ySize - 1), 0,
						BuildCraftFactory.frameBlock.blockID);
				bluePrint.setBlockId(i, it * (ySize - 1), bluePrint.sizeZ - 1,
						BuildCraftFactory.frameBlock.blockID);
			}

			for (int k = 0; k < bluePrint.sizeZ; ++k) {
				bluePrint.setBlockId(0, it * (ySize - 1), k,
						BuildCraftFactory.frameBlock.blockID);
				bluePrint.setBlockId(bluePrint.sizeX - 1, it * (ySize - 1), k,
						BuildCraftFactory.frameBlock.blockID);

			}
		}

		for (int h = 1; h < ySize; ++h) {
			bluePrint.setBlockId(0, h, 0,
					BuildCraftFactory.frameBlock.blockID);
			bluePrint.setBlockId(0, h, bluePrint.sizeZ - 1,
					BuildCraftFactory.frameBlock.blockID);
			bluePrint.setBlockId(bluePrint.sizeX - 1, h, 0,
					BuildCraftFactory.frameBlock.blockID);
			bluePrint.setBlockId(bluePrint.sizeX - 1, h,
					bluePrint.sizeZ - 1,
					BuildCraftFactory.frameBlock.blockID);
		}
		
		bluePrintBuilder = new BluePrintBuilder(bluePrint, xMin, yCoord, zMin);		
	}
	
	@Override
	public void handleUpdatePacket (Packet230ModLoader packet) {
		createUtilsIfNeeded();
		
		super.handleUpdatePacket(packet);
		
		if (arm != null) {
			arm.refresh ();
		}
	}
	
	@Override
	public void handleDescriptionPacket (Packet230ModLoader packet) {
		createUtilsIfNeeded();
		
		super.handleDescriptionPacket(packet);
		
		if (arm != null) {
			arm.refresh ();
		}
	}
	
	public void initialize () {
		super.initialize();
	
		BlockIndex index = new BlockIndex(xCoord, yCoord, zCoord);		
		
		if (BuildCraftCore.bufferedDescriptions.containsKey(index)) {
			
			Packet230ModLoader packet = BuildCraftCore.bufferedDescriptions.get(index);
			BuildCraftCore.bufferedDescriptions.remove(index);
			
			handleDescriptionPacket(packet);
		} else if (!APIProxy.isClient(worldObj)) {
			createUtilsIfNeeded ();			
		}
	}

	@Override
	public void setPowerProvider(PowerProvider provider) {
		provider = powerProvider;
		
	}

	@Override
	public PowerProvider getPowerProvider() {
		return powerProvider;
	}
}
