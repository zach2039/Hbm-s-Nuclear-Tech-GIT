package com.hbm.inventory.container;

import com.hbm.inventory.SlotMachineOutput;
import com.hbm.inventory.SlotUpgrade;
import com.hbm.tileentity.machine.TileEntityMachineCrystallizer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerCrystallizer extends Container {

	private TileEntityMachineCrystallizer diFurnace;

	public ContainerCrystallizer(InventoryPlayer invPlayer, TileEntityMachineCrystallizer tedf) {
		diFurnace = tedf;

		this.addSlotToContainer(new SlotItemHandler(tedf.inventory, TileEntityMachineCrystallizer.CrystallizerSlot.INPUT.get(), 62, 45));
		this.addSlotToContainer(new SlotItemHandler(tedf.inventory, TileEntityMachineCrystallizer.CrystallizerSlot.BATTERY.get(), 152, 72));
		this.addSlotToContainer(new SlotMachineOutput(tedf.inventory, TileEntityMachineCrystallizer.CrystallizerSlot.OUTPUT.get(), 113, 45));
		this.addSlotToContainer(new SlotItemHandler(tedf.inventory, TileEntityMachineCrystallizer.CrystallizerSlot.FLUID_INPUT.get(), 17, 18));
		this.addSlotToContainer(new SlotMachineOutput(tedf.inventory, TileEntityMachineCrystallizer.CrystallizerSlot.FLUID_OUTPUT.get(), 17, 54));
		this.addSlotToContainer(new SlotUpgrade(tedf.inventory, TileEntityMachineCrystallizer.CrystallizerSlot.UPGRADE_0.get(), 80, 18));
		this.addSlotToContainer(new SlotUpgrade(tedf.inventory, TileEntityMachineCrystallizer.CrystallizerSlot.UPGRADE_1.get(), 98, 18));

		for(int i = 0; i < 3; i++)
		{
			for(int j = 0; j < 9; j++)
			{
				this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, 8 + j * 18, 122 + i * 18));
			}
		}

		for(int i = 0; i < 9; i++)
		{
			this.addSlotToContainer(new Slot(invPlayer, i, 8 + i * 18, 180));
		}
	}

	@Override
    public ItemStack transferStackInSlot(EntityPlayer p_82846_1_, int par2)
    {
		ItemStack var3 = ItemStack.EMPTY;
		Slot var4 = (Slot) this.inventorySlots.get(par2);
		
		if (var4 != null && var4.getHasStack())
		{
			ItemStack var5 = var4.getStack();
			var3 = var5.copy();
			
            if (par2 <= diFurnace.inventory.getSlots() - 1) {
				if (!this.mergeItemStack(var5, diFurnace.inventory.getSlots(), this.inventorySlots.size(), true))
				{
					return ItemStack.EMPTY;
				}
			} else {
				
				if (!this.mergeItemStack(var5, 0, 2, false))
					if (!this.mergeItemStack(var5, 3, 4, false))
						if (!this.mergeItemStack(var5, 5, 7, false))
							return ItemStack.EMPTY;
			}
			
			if (var5.isEmpty())
			{
				var4.putStack(ItemStack.EMPTY);
			}
			else
			{
				var4.onSlotChanged();
			}
		}
		
		return var3;
    }
	
	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return diFurnace.isUseableByPlayer(player);
	}
}