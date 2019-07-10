package cn.mmf.slashblade_tic.modifiers;

import com.google.common.collect.ImmutableList;

import cn.mmf.slashblade_tic.blade.SlashBladeCore;
import cn.mmf.slashblade_tic.blade.TinkerSlashBladeRegistry;
import cn.mmf.slashblade_tic.util.SlashBladeBuilder;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import slimeknights.mantle.util.RecipeMatch;
import slimeknights.mantle.util.RecipeMatchRegistry;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.modifiers.TinkerGuiException;
import slimeknights.tconstruct.library.tools.IToolPart;
import slimeknights.tconstruct.library.traits.ITrait;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.tools.modifiers.ToolModifier;

public class ModBladeExtraTrait extends ToolModifier {

  public static final List<ItemStack> EMBOSSMENT_ITEMS = getEmbossmentItems();
  public static final String EXTRA_TRAIT_IDENTIFIER = "blade_extratrait";
  private final Material material;
  public final Set<SlashBladeCore> toolCores;
  private final Collection<ITrait> traits;


  public ModBladeExtraTrait(Material material, Collection<ITrait> traits) {
    this(material, traits, generateIdentifier(material, traits));
  }

  public ModBladeExtraTrait(Material material, Collection<ITrait> traits, String customIdentifier) {
      super(EXTRA_TRAIT_IDENTIFIER + customIdentifier, material.materialTextColor);
      
      TinkerSlashBladeRegistry.registerModifier(this);
      this.material = material;
      this.toolCores = new HashSet<>();
      this.traits = traits;
      addAspects(new ExtraTraitAspect(), new ModifierAspect.SingleAspect(this), new ModifierAspect.DataAspect(this));
  }

  public <T extends Item & IToolPart> void addCombination(SlashBladeCore toolCore, T toolPart) {
    toolCores.add(toolCore);
    ItemStack toolPartItem = toolPart.getItemstackWithMaterial(material);
    List<ItemStack> stacks = new ArrayList<>();
    stacks.add(toolPartItem);
    stacks.addAll(EMBOSSMENT_ITEMS);
    addRecipeMatch(new RecipeMatch.ItemCombination(1, stacks.toArray(new ItemStack[stacks.size()])));
  }

  public static String generateIdentifier(Material material, Collection<ITrait> traits) {
    String traitString = traits.stream().map(ITrait::getIdentifier).sorted().collect(Collectors.joining());
    return material.getIdentifier() + traitString;
  }

  @Override
  public boolean canApplyCustom(ItemStack stack) throws TinkerGuiException {
    return stack.getItem() instanceof SlashBladeCore && toolCores.contains(stack.getItem());
  }

  @Override
  public String getLocalizedName() {
    return Util.translate(LOC_Name, EXTRA_TRAIT_IDENTIFIER) + " (" + material.getLocalizedName() + ")";
  }

  @Override
  public String getLocalizedDesc() {
    return Util.translateFormatted(String.format(LOC_Desc, EXTRA_TRAIT_IDENTIFIER), material.getLocalizedName());
  }

  @Override
  public void applyEffect(NBTTagCompound rootCompound, NBTTagCompound modifierTag) {
    traits.forEach(trait -> SlashBladeBuilder.addTrait(rootCompound, trait, color));
  }

  @Override
  public boolean hasTexturePerMaterial() {
    return true;
  }

  private static class ExtraTraitAspect extends ModifierAspect {

    @Override
    public boolean canApply(ItemStack stack, ItemStack original) throws TinkerGuiException {
      NBTTagList modifierList = TagUtil.getModifiersTagList(original);
      for(int i = 0; i < modifierList.tagCount(); i++) {
        NBTTagCompound tag = modifierList.getCompoundTagAt(i);
        ModifierNBT data = ModifierNBT.readTag(tag);
        if(data.identifier.startsWith(EXTRA_TRAIT_IDENTIFIER)) {
          throw new TinkerGuiException(Util.translate("gui.error.already_has_extratrait"));
        }
      }
      return true;
    }

    @Override
    public void updateNBT(NBTTagCompound root, NBTTagCompound modifierTag) {
      // nothing to do
    }

  }

  private static List<ItemStack> getEmbossmentItems() {
    ItemStack green = TinkerCommons.matSlimeCrystalGreen;
    ItemStack blue = TinkerCommons.matSlimeCrystalBlue;
    ItemStack red = TinkerCommons.matSlimeCrystalMagma;
    ItemStack expensive = new ItemStack(Blocks.GOLD_BLOCK);

    if(green == null) {
      green = new ItemStack(Items.SLIME_BALL);
    }
    if(blue == null) {
      blue = new ItemStack(Items.SLIME_BALL);
    }
    if(red == null) {
      red = new ItemStack(Items.SLIME_BALL);
    }

    return ImmutableList.of(green, blue, red, expensive);
  }
}