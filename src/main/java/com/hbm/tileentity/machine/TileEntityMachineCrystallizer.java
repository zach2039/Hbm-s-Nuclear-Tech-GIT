package com.hbm.tileentity.machine;

import com.hbm.blocks.BlockDummyable;
import com.hbm.forgefluid.FFUtils;
import com.hbm.forgefluid.ModForgeFluids;
import com.hbm.interfaces.ITankPacketAcceptor;
import com.hbm.inventory.CrystallizerRecipes;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemForgeFluidIdentifier;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.lib.HBMSoundHandler;
import com.hbm.lib.Library;
import com.hbm.lib.ForgeDirection;
import com.hbm.packet.FluidTankPacket;
import com.hbm.packet.PacketDispatcher;
import com.hbm.tileentity.TileEntityMachineBase;

import api.hbm.energy.IEnergyUser;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public class TileEntityMachineCrystallizer extends TileEntityMachineBase implements ITickable, IEnergyUser, IFluidHandler, ITankPacketAcceptor {

	public enum CrystallizerSlot {
		INPUT,
		BATTERY,
		OUTPUT,
		FLUID_INPUT,
		FLUID_OUTPUT,
		UPGRADE_0,
		UPGRADE_1,
		FLUID_IDENTIFIER
		;

		public int get() {
			return this.ordinal();
		}
	}


	public long power;
	public static final long maxPower = 1000000;
	public static final int demand = 1000;
	public int acidRequired = 500;
	public short progress;
	public static final short duration = 600;

	public float angle;
	public float prevAngle;

	public FluidTank tank;

	public TileEntityMachineCrystallizer() {
		super(CrystallizerSlot.values().length);

		inventory = new ItemStackHandler(CrystallizerSlot.values().length) {

			@Override
			protected void onContentsChanged(int slot) {
				super.onContentsChanged(slot);
				markDirty();
			}

			@Override
			public void setStackInSlot(int slot, ItemStack stack) {
				super.setStackInSlot(slot, stack);

				// Play sound for upgrade slotting
				if (stack != null) {
					if (slot == CrystallizerSlot.UPGRADE_0.get() || slot == CrystallizerSlot.UPGRADE_1.get() && stack.getItem() instanceof ItemMachineUpgrade) {
						world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, HBMSoundHandler.upgradePlug, SoundCategory.BLOCKS, 1.0F, 1.0F);
					}
				}
			}
		};

		tank = new FluidTank(16000);
	}

	@Override
	public String getName() {
		return "container.crystallizer";
	}

	private void updateConnections() {

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);

		if(dir == ForgeDirection.NORTH || dir == ForgeDirection.SOUTH) {
			this.trySubscribe(world, pos.add(2, 5, 0), ForgeDirection.EAST);
			this.trySubscribe(world, pos.add(-2, 5, 0), ForgeDirection.WEST);
		} else if(dir == ForgeDirection.EAST || dir == ForgeDirection.WEST) {
			this.trySubscribe(world, pos.add(0, 5, 2), ForgeDirection.SOUTH);
			this.trySubscribe(world, pos.add(0, 5, -2), ForgeDirection.NORTH);
		}
	}

	@Override
	public void update() {
		if(!world.isRemote) {

			this.updateConnections();

			// Prevent crash from old ore acidizers that have incorrect num of slots in inv
			if (inventory.getSlots() < CrystallizerSlot.values().length) {
				inventory.setSize(CrystallizerSlot.values().length);
			}

			// Allow swapping of input fluid type using fluid identifier
			if(inventory.getStackInSlot(CrystallizerSlot.FLUID_IDENTIFIER.get()).getItem() == ModItems.forge_fluid_identifier){
				Fluid fluidFromIdentifier = ItemForgeFluidIdentifier.getType(inventory.getStackInSlot(CrystallizerSlot.FLUID_IDENTIFIER.get()));
				if (isValidFluidForTank(fluidFromIdentifier)) {
					setTankType(fluidFromIdentifier);
				}
			}

			power = Library.chargeTEFromItems(inventory, 1, power, maxPower);
			if(inputValidForTank(CrystallizerSlot.FLUID_INPUT.get()) && tank.getFluidAmount() < tank.getCapacity()){
				FFUtils.fillFromFluidContainer(inventory, tank, CrystallizerSlot.FLUID_INPUT.get(), CrystallizerSlot.FLUID_OUTPUT.get());
			}

			for(int i = 0; i < getCycleCount(); i++) {

				if(canProcess()) {

					progress++;
					power -= getPowerRequired();

					if(progress > getDuration()) {
						progress = 0;
						tank.drain(getRequiredAcid(), true);
						processItem();

						this.markDirty();
					}

				} else {
					progress = 0;
				}
			}

			PacketDispatcher.wrapper.sendToAllAround(new FluidTankPacket(pos, new FluidTank[]{tank}), new TargetPoint(world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 10));

			NBTTagCompound data = new NBTTagCompound();
			data.setShort("progress", progress);
			data.setLong("power", power);
			this.networkPack(data, 25);
		} else {

			prevAngle = angle;

			if(progress > 0) {
				angle += 5F * this.getCycleCount();

				if(angle >= 360) {
					angle -= 360;
					prevAngle -= 360;
				}
			}
		}
	}

	protected boolean inputValidForTank(int slot){
		
		if(!inventory.getStackInSlot(slot).isEmpty()){
			FluidStack containerFluid = FluidUtil.getFluidContained(inventory.getStackInSlot(slot));
			if(containerFluid != null){
				if(CrystallizerRecipes.isAllowedFluid(containerFluid.getFluid())){
					setTankType(containerFluid.getFluid());
					return true;
				}
			}
		}
		return false;
	}

	private boolean isValidFluidForTank(Fluid stack) {
		if(stack == null)
			return false;

		return CrystallizerRecipes.isAllowedFluid(stack);
	}

	public void setTankType(Fluid f){
		if(f != null && (tank.getFluid() == null || (tank.getFluid() != null && tank.getFluid().getFluid() != f))){
			tank.setFluid(new FluidStack(f, 0));
		}
	}

	@Override
	public void networkUnpack(NBTTagCompound data) {
		this.power = data.getLong("power");
		this.progress = data.getShort("progress");
	}

	private void processItem() {

		ItemStack result = CrystallizerRecipes.getOutputItem(inventory.getStackInSlot(CrystallizerSlot.INPUT.get()));

		if(result == null) //never happens but you can't be sure enough
			return;

		if(inventory.getStackInSlot(CrystallizerSlot.OUTPUT.get()).isEmpty()) {
			inventory.setStackInSlot(CrystallizerSlot.OUTPUT.get(), result);
		} else if(inventory.getStackInSlot(CrystallizerSlot.OUTPUT.get()).getCount() + result.getCount() <= inventory.getStackInSlot(CrystallizerSlot.OUTPUT.get()).getMaxStackSize()) {
			inventory.getStackInSlot(CrystallizerSlot.OUTPUT.get()).grow(result.getCount());
		}

		//Drillgon200: I think this does the same thing as decrStackSize?
		float freeChance = this.getFreeChance();

		if(freeChance == 0 || freeChance < world.rand.nextFloat())
			inventory.getStackInSlot(CrystallizerSlot.INPUT.get()).shrink(1);
		//this.decrStackSize(0, 1);
	}

	private boolean canProcess() {

		//Is there no input?
		if(inventory.getStackInSlot(CrystallizerSlot.INPUT.get()).isEmpty())
			return false;

		if(power < getPowerRequired())
			return false;

		ItemStack result = CrystallizerRecipes.getOutputItem(inventory.getStackInSlot(CrystallizerSlot.INPUT.get()));

		//Or output?
		if(result == null)
			return false;

		//Does the output not match?
		if(!inventory.getStackInSlot(CrystallizerSlot.OUTPUT.get()).isEmpty() && (inventory.getStackInSlot(CrystallizerSlot.OUTPUT.get()).getItem() != result.getItem() || inventory.getStackInSlot(CrystallizerSlot.OUTPUT.get()).getItemDamage() != result.getItemDamage()))
			return false;

		//Or is the output slot already full?
		if(inventory.getStackInSlot(CrystallizerSlot.OUTPUT.get()).getCount() >= inventory.getStackInSlot(CrystallizerSlot.OUTPUT.get()).getMaxStackSize())
			return false;

		FluidStack acidFluid = CrystallizerRecipes.getOutputFluid(inventory.getStackInSlot(CrystallizerSlot.INPUT.get()));

		if(acidFluid == null)
			return false;

		if(tank.getFluid() == null)
			return false;

		if(acidFluid.getFluid() != tank.getFluid().getFluid())
			return false;

		acidRequired = acidFluid.amount;

		if(tank.getFluidAmount() < getRequiredAcid())
			return false;

		return true;
	}

	public int getRequiredAcid() {

		int extra = 0;

		for(int i = 5; i <= 6; i++) {

			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_effect_1)
				extra += acidRequired * 3;
			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_effect_2)
				extra += acidRequired * 4;
			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_effect_3)
				extra += acidRequired * 5;
		}

		return acidRequired + Math.min(extra, 500);
	}

	public float getFreeChance() {

		float chance = 0.0F;

		for(int i = 5; i <= 6; i++) {

			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_effect_1)
				chance += 0.05F;
			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_effect_2)
				chance += 0.1F;
			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_effect_3)
				chance += 0.15F;
		}

		return Math.min(chance, 0.15F);
	}

	public int getDuration() {

		float durationMod = 1;

		for(int i = 5; i <= 6; i++) {

			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_speed_1)
				durationMod -= 0.25F;
			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_speed_2)
				durationMod -= 0.5F;
			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_speed_3)
				durationMod -= 0.75F;
		}

		return (int) (duration * Math.max(durationMod, 0.7F));
	}

	public int getPowerRequired() {

		int consumption = 0;

		for(int i = 5; i <= 6; i++) {

			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_speed_1)
				consumption += 1000;
			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_speed_2)
				consumption += 2000;
			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_speed_3)
				consumption += 3000;
		}

		return (int) (demand + Math.min(consumption, 3000));
	}

	public float getCycleCount() {

		int cycles = 1;

		for(int i = 5; i <= 6; i++) {

			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_overdrive_1)
				cycles += 2;
			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_overdrive_2)
				cycles += 4;
			if(inventory.getStackInSlot(i).getItem() == ModItems.upgrade_overdrive_3)
				cycles += 6;
		}

		return Math.min(cycles, 7);
	}
	
	@Override
	public boolean canInsertItem(int slot, ItemStack itemStack, int amount) {
		return slot == CrystallizerSlot.INPUT.get() && CrystallizerRecipes.getOutputItem(itemStack) != null;
	}
	
	@Override
	public boolean canExtractItem(int slot, ItemStack itemStack, int amount) {
		return slot == CrystallizerSlot.OUTPUT.get();
	}

	@Override
	public int[] getAccessibleSlotsFromSide(EnumFacing face) {
		if (face == EnumFacing.DOWN) {
			return new int[] { CrystallizerSlot.OUTPUT.get() };
		} else {
			return new int[] { CrystallizerSlot.INPUT.get(), CrystallizerSlot.OUTPUT.get() };
		}
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		power = compound.getLong("power");
		tank.readFromNBT(compound.getCompoundTag("tank"));
		super.readFromNBT(compound);
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		compound.setLong("power", power);
		compound.setTag("tank", tank.writeToNBT(new NBTTagCompound()));
		return super.writeToNBT(compound);
	}

	public long getPowerScaled(int i) {
		return (power * i) / maxPower;
	}

	public int getProgressScaled(int i) {
		return (progress * i) / this.getDuration();
	}

	@Override
	public void setPower(long i) {
		this.power = i;
	}

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}

	@Override
	public IFluidTankProperties[] getTankProperties() {
		return tank.getTankProperties();
	}

	@Override
	public int fill(FluidStack resource, boolean doFill) {
		if(resource != null && CrystallizerRecipes.isAllowedFluid(resource.getFluid())) {
			return tank.fill(resource, doFill);
		}
		return 0;
	}

	@Override
	public FluidStack drain(FluidStack resource, boolean doDrain) {
		return null;
	}

	@Override
	public FluidStack drain(int maxDrain, boolean doDrain) {
		return null;
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY){
			return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this);
		} else {
			return super.getCapability(capability, facing);
		}
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
			return true;
		} else {
			return super.hasCapability(capability, facing);
		}
	}

	@Override
	public void recievePacket(NBTTagCompound[] tags) {
		if(tags.length == 1) {
			tank.readFromNBT(tags[0]);
		}
	}
}