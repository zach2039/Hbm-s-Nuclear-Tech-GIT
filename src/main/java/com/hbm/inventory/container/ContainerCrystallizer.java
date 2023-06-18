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

	private TileEntityMachineCrystallizer machineCrystallizer;

	public ContainerCrystallizer(InventoryPlayer invPlayer, TileEntityMachineCrystallizer tedf) {
		machineCrystallizer = tedf;

		this.addSlotToContainer(new SlotItemHandler(tedf.inventory, TileEntityMachineCrystallizer.SLOT_INPUT, 62, 45));
		this.addSlotToContainer(new SlotItemHandler(tedf.inventory, TileEntityMachineCrystallizer.SLOT_BATTERY, 152, 72));
		this.addSlotToContainer(new SlotMachineOutput(tedf.inventory, TileEntityMachineCrystallizer.SLOT_OUTPUT, 113, 45));
		this.addSlotToContainer(new SlotItemHandler(tedf.inventory, TileEntityMachineCrystallizer.SLOT_FLUID_INPUT, 17, 18));
		this.addSlotToContainer(new SlotMachineOutput(tedf.inventory, TileEntityMachineCrystallizer.SLOT_FLUID_OUTPUT, 17, 54));
		this.addSlotToContainer(new SlotUpgrade(tedf.inventory, TileEntityMachineCrystallizer.SLOT_UPGRADE_0, 80, 18));
		this.addSlotToContainer(new SlotUpgrade(tedf.inventory, TileEntityMachineCrystallizer.SLOT_UPGRADE_1, 98, 18));
		this.addSlotToContainer(new SlotItemHandler(tedf.inventory, TileEntityMachineCrystallizer.SLOT_FLUID_IDENTIFIER, 35, 72));

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
			
            if (par2 <= machineCrystallizer.inventory.getSlots() - 1) {
				if (!this.mergeItemStack(var5, machineCrystallizer.inventory.getSlots(), this.inventorySlots.size(), true))
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
		return machineCrystallizer.isUseableByPlayer(player);
	}
}