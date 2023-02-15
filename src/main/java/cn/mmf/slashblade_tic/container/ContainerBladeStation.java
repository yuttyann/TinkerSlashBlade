package cn.mmf.slashblade_tic.container;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.StringUtils;
import net.minecraft.world.WorldServer;
import slimeknights.mantle.inventory.BaseContainer;
import slimeknights.mantle.util.ItemStackList;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.Sounds;
import slimeknights.tconstruct.common.TinkerNetwork;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.events.TinkerCraftingEvent;
import slimeknights.tconstruct.library.modifiers.TinkerGuiException;
import slimeknights.tconstruct.library.tinkering.IModifyable;
import slimeknights.tconstruct.library.tinkering.IRepairable;
import slimeknights.tconstruct.library.tinkering.PartMaterialType;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.tools.common.inventory.ContainerTinkerStation;

import java.util.List;
import java.util.Set;

import cn.mmf.slashblade_tic.blade.SlashBladeCore;
import cn.mmf.slashblade_tic.blade.SlashBladeTICBasic;
import cn.mmf.slashblade_tic.blade.TinkerSlashBladeRegistry;
import cn.mmf.slashblade_tic.block.tileentity.TileBladeStation;
import cn.mmf.slashblade_tic.client.gui.GuiBladeStation;
import cn.mmf.slashblade_tic.packet.BladeStationSelectionPacket;
import cn.mmf.slashblade_tic.packet.BladeStationTextPacket;
import cn.mmf.slashblade_tic.util.SlashBladeBuilder;
import cn.mmf.slashblade_tic.util.SlashBladeHelper;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;

public class ContainerBladeStation extends ContainerTinkerStation<TileBladeStation> {

    private final EntityPlayer player;
    protected SlotBladeStationOut out;
    protected SlashBladeCore selectedTool;
    protected int activeSlots;
    public String toolName;
    
    public ContainerBladeStation(InventoryPlayer playerInventory, TileBladeStation tile) {
        super(tile);
        this.player = playerInventory.player;
        int i;
        for(i = 0; i < tile.getSizeInventory(); i++) {
            addSlotToContainer(new SlotBladeStationIn(tile, i, 0, 0, this));
        }
        out = new SlotBladeStationOut(i, 124, 38, this);
        addSlotToContainer(out);
        addPlayerInventory(playerInventory, 8, 92);
        onCraftMatrixChanged(playerInventory);
    }
    
    public boolean isForge() {
        return false;
    }
    
    public ItemStack getResult() {
        return out.getStack();
    }
    
    @Override
    protected void syncNewContainer(EntityPlayerMP player) {
        this.activeSlots = tile.getSizeInventory();
        TinkerNetwork.sendTo(new BladeStationSelectionPacket(null, tile.getSizeInventory()), player);
    }
    
    @Override
    protected void syncWithOtherContainer(BaseContainer<TileBladeStation> otherContainer, EntityPlayerMP player) {
        syncWithOtherContainer((ContainerBladeStation) otherContainer, player);
    }
    
    protected void syncWithOtherContainer(ContainerBladeStation otherContainer, EntityPlayerMP player) {
        setToolSelection(otherContainer.selectedTool, otherContainer.activeSlots);
        setToolName(otherContainer.toolName);
        TinkerNetwork.sendTo(new BladeStationSelectionPacket(otherContainer.selectedTool, otherContainer.activeSlots), player);
        if (otherContainer.toolName != null && !otherContainer.toolName.isEmpty()) {
            TinkerNetwork.sendTo(new BladeStationTextPacket(otherContainer.toolName), player);
        }
    }
    
    public void setToolSelection(SlashBladeCore tool, int activeSlots) {
        if (activeSlots > tile.getSizeInventory()) {
            activeSlots = tile.getSizeInventory();
        }
        this.activeSlots = activeSlots;
        this.selectedTool = tool;
        for (int i = 0; i < tile.getSizeInventory(); i++) {
            Slot slot = inventorySlots.get(i);
            if (slot instanceof SlotBladeStationIn) {
                SlotBladeStationIn slotToolPart = (SlotBladeStationIn) slot;
                slotToolPart.setRestriction(null);
                if (i >= activeSlots) {
                    slotToolPart.deactivate();
                } else {
                    slotToolPart.activate();
                    if (tool != null) {
                        List<PartMaterialType> pmts = tool.getToolBuildComponents();
                        if (i < pmts.size()) {
                            slotToolPart.setRestriction(pmts.get(i));
                        }
                    } 
                } 
                if (world.isRemote) {
                    slotToolPart.updateIcon();
                }
            }
        } 
    }
    
    public void setToolName(String name) {
        this.toolName = name;
        if (world.isRemote) {
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (screen instanceof GuiBladeStation) {
                ((GuiBladeStation) screen).textField.setText(name);
            } 
        }
        onCraftMatrixChanged(tile);
        if (out.getHasStack()) {
            if (name != null && !name.isEmpty()) {
                out.inventory.getStackInSlot(0).setStackDisplayName(name);
            } else {
                out.inventory.getStackInSlot(0).clearCustomName();
            }
        }
    }
    
    @Override
    public void onCraftMatrixChanged(IInventory inventoryIn) {
        updateGUI();
        try {
            ItemStack result = repairTool(false);
            if (result.isEmpty()) {
                result = replaceToolParts(false);
            }
            if (result.isEmpty()) {
                result = modifyTool(false);
            }
            if (result.isEmpty()) {
                result = renameTool();
            } 
            if (result.isEmpty()) {
                result = buildTool();
            }
            out.inventory.setInventorySlotContents(0, result);
            updateGUI();
        } catch (TinkerGuiException e) {
            out.inventory.setInventorySlotContents(0, ItemStack.EMPTY);
            error(e.getMessage());
        } 
        if (!world.isRemote) {
            WorldServer server = (WorldServer) world;
            for (EntityPlayer player : server.playerEntities) {
                if (player.openContainer == this) {
                    continue;
                }
                if (player.openContainer instanceof ContainerBladeStation && sameGui((ContainerBladeStation) player.openContainer)) {
                    ((ContainerBladeStation) player.openContainer).out.inventory.setInventorySlotContents(0, out.getStack());
                }
            }
        } 
    }
    
    public void onResultTaken(EntityPlayer playerIn, ItemStack stack) {
        boolean resultTaken = false, rename = false;
        try {
            resultTaken = !repairTool(true).isEmpty() || !replaceToolParts(true).isEmpty() || !modifyTool(true).isEmpty() || (rename = !renameTool().isEmpty());
        } catch (TinkerGuiException e) {
            e.printStackTrace();
        }
        if (resultTaken) {
            updateSlotsAfterToolAction();
            if (isForge() && !rename) {
                NBTTagCompound nbt = ItemSlashBlade.getItemTagCompound(stack);
                ItemSlashBlade.RepairCount.set(nbt, Integer.valueOf(ItemSlashBlade.RepairCount.get(nbt).intValue() + 1));
            }
        } else {
            try {
                ItemStack tool = buildTool();
                if (!tool.isEmpty()) {
                    for (int i = 0; i < tile.getSizeInventory(); i++) {
                        tile.decrStackSize(i, 1);
                    }
                    setToolName("");
                } 
            } catch (TinkerGuiException e) {
                e.printStackTrace();
            }
        } 
        onCraftMatrixChanged(null);
        playCraftSound(playerIn);
    }
    
    protected void playCraftSound(EntityPlayer player) {
        Sounds.playSoundForAll(player, Sounds.saw, 0.8F, 0.8F + 0.4F * TConstruct.random.nextFloat());
    }
    
    private ItemStack repairTool(boolean remove) {
        ItemStack repairable = getToolStack();
        if (repairable.isEmpty() || !(repairable.getItem() instanceof IRepairable)) {
            return ItemStack.EMPTY;
        }
        return SlashBladeBuilder.tryRepairTool(getInputs(), repairable, remove);
    }
    
    private ItemStack replaceToolParts(boolean remove) throws TinkerGuiException {
        ItemStack tool = getToolStack();
        if (tool.isEmpty() || !(tool.getItem() instanceof SlashBladeTICBasic)) {
            return ItemStack.EMPTY;
        }
        NonNullList<ItemStack> inputs = getInputs();
        ItemStack result = SlashBladeBuilder.tryReplaceToolParts(tool, inputs, remove);
        if (!result.isEmpty()) {
            TinkerCraftingEvent.ToolPartReplaceEvent.fireEvent(result, this.player, inputs);
            NBTTagCompound nbt = ItemSlashBlade.getItemTagCompound(result);
            float attack = SlashBladeHelper.getActualAttack(result);
            ItemSlashBlade.setBaseAttackModifier(nbt, attack);
        } 
        return result;
    }
    
    private ItemStack modifyTool(boolean remove) throws TinkerGuiException {
        ItemStack modifyable = getToolStack();
        if (modifyable.isEmpty() || !(modifyable.getItem() instanceof IModifyable)) {
            return ItemStack.EMPTY;
        }
        ItemStack result = SlashBladeBuilder.tryModifyTool(getInputs(), modifyable, remove);
        if (!result.isEmpty()) {
            TinkerCraftingEvent.ToolModifyEvent.fireEvent(result, this.player, modifyable.copy());
            NBTTagCompound nbt = ItemSlashBlade.getItemTagCompound(result);
            float attack = SlashBladeHelper.getActualAttack(result);
            ItemSlashBlade.setBaseAttackModifier(nbt, attack);
        } 
        return result;
    }
    
    private ItemStack renameTool() throws TinkerGuiException {
        ItemStack tool = getToolStack();
        if (tool.isEmpty() || !(tool.getItem() instanceof SlashBladeTICBasic) ||  StringUtils.isNullOrEmpty(this.toolName) || tool.getDisplayName().equals(this.toolName)) {
            return ItemStack.EMPTY;
        }
        ItemStack result = tool.copy();
        if (TagUtil.getNoRenameFlag(result)) {
            throw new TinkerGuiException(Util.translate("gui.error.no_rename", new Object[0]));
        }
        result.setStackDisplayName(this.toolName);
        return result;
    }
    
    private ItemStack buildTool() throws TinkerGuiException {
        ItemStackList itemStackList = ItemStackList.withSize(tile.getSizeInventory());
        for (int i = 0; i < itemStackList.size(); i++) {
            itemStackList.set(i, tile.getStackInSlot(i));
        }
        ItemStack result = SlashBladeBuilder.tryBuildTool(itemStackList, this.toolName, getBuildableTools());
        if (!result.isEmpty()) {
            TinkerCraftingEvent.ToolCraftingEvent.fireEvent(result, this.player, itemStackList);
        }
        return result;
    }
    
    protected Set<SlashBladeCore> getBuildableTools() {
        return TinkerSlashBladeRegistry.getToolStationCrafting();
    }
    
    private ItemStack getToolStack() {
        return inventorySlots.get(0).getStack();
    }
    
    private void updateSlotsAfterToolAction() {
        tile.setInventorySlotContents(0, ItemStack.EMPTY);
        for (int i = 1; i < tile.getSizeInventory(); i++) {
            if (!tile.getStackInSlot(i).isEmpty() && tile.getStackInSlot(i).getCount() == 0) {
                tile.setInventorySlotContents(i, ItemStack.EMPTY); 
            }
        } 
    }
    
    private NonNullList<ItemStack> getInputs() {
        NonNullList<ItemStack> input = NonNullList.withSize(tile.getSizeInventory() - 1, ItemStack.EMPTY);
        for (int i = 1; i < tile.getSizeInventory(); i++) {
            input.set(i - 1, tile.getStackInSlot(i)); 
        }
        return input;
    }
    
    @Override
    public boolean canMergeSlot(ItemStack stack, Slot slot) {
        return slot != out && super.canMergeSlot(stack, slot);
    }
}
