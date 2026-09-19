package com.angel.itempricer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VillagerRenderHandler {

    private static final Map<UUID, List<ItemStack>> infoAldeanos = new HashMap<>();
    private static UUID ultimoIdInteraccion = null;

    @SubscribeEvent
    public void alInteractuar(PlayerInteractEvent.EntityInteract event) {
        if (event.getWorld().isRemote && event.getTarget() instanceof EntityVillager) {
            ultimoIdInteraccion = event.getTarget().getUniqueID();
        }
    }

    @SubscribeEvent
    public void alHacerTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.currentScreen == null || ultimoIdInteraccion == null) return;

        if (mc.currentScreen.getClass().getName().contains("GuiMerchantOverride")) {
            escanearTradeos(mc.currentScreen);
        }
    }

    private void escanearTradeos(GuiScreen gui) {
        try {
            for (Field f : gui.getClass().getDeclaredFields()) {
                if (f.getType() == MerchantRecipeList.class) {
                    f.setAccessible(true);
                    MerchantRecipeList recipes = (MerchantRecipeList) f.get(gui);
                    if (recipes != null) {
                        actualizarListaAldeano(recipes);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void actualizarListaAldeano(MerchantRecipeList recipes) {
        List<ItemStack> itemsBloqueados = new ArrayList<>();

        for (MerchantRecipe recipe : recipes) {
            if (recipe.isRecipeDisabled() || estaBloqueado(recipe)) {
                ItemStack input1 = recipe.getItemToBuy();
                ItemStack input2 = recipe.hasSecondItemToBuy() ? recipe.getSecondItemToBuy() : ItemStack.EMPTY;
                ItemStack output = recipe.getItemToSell();
                
                ItemStack itemAEscribir = ItemStack.EMPTY;

                if (output.getItem() == Items.ENCHANTED_BOOK) {
                    itemAEscribir = output.copy();
                } else if (input1.getItem() == Items.EMERALD) {
                    if (!input2.isEmpty()) {
                        itemAEscribir = input2.copy();
                    } else {
                        itemAEscribir = output.copy();
                    }
                } else {
                    itemAEscribir = input1.copy();
                }

                if (!itemAEscribir.isEmpty() && !estaEnLista(itemsBloqueados, itemAEscribir)) {
                    itemsBloqueados.add(itemAEscribir);
                }
            }
        }

        if (itemsBloqueados.isEmpty()) {
            infoAldeanos.remove(ultimoIdInteraccion);
        } else {
            infoAldeanos.put(ultimoIdInteraccion, itemsBloqueados);
        }
    }

    private boolean estaEnLista(List<ItemStack> lista, ItemStack stack) {
        for (ItemStack s : lista) {
            if (s.getItem() == stack.getItem() && s.getMetadata() == stack.getMetadata()) {
                return true;
            }
        }
        return false;
    }

    private boolean estaBloqueado(MerchantRecipe recipe) {
        try {
            int usos = ReflectionHelper.getPrivateValue(MerchantRecipe.class, recipe, "field_77399_n", "toolUses");
            int max = ReflectionHelper.getPrivateValue(MerchantRecipe.class, recipe, "field_82783_c", "maxUses");
            return usos >= max;
        } catch (Exception e) { return false; }
    }

    @SubscribeEvent
    public void alRenderizar(RenderLivingEvent.Post<net.minecraft.entity.EntityLivingBase> event) {
        if (!(event.getEntity() instanceof EntityVillager)) return;
        UUID id = event.getEntity().getUniqueID();

        if (infoAldeanos.containsKey(id)) {
            List<ItemStack> stacks = infoAldeanos.get(id);
            float yOffset = 0;
            
            for (ItemStack stack : stacks) {
                renderItem(event.getX(), event.getY() + event.getEntity().height + 0.6 + yOffset, event.getZ(), stack);
                yOffset += 0.45F;
            }
        }
    }

    private void renderItem(double x, double y, double z, ItemStack stack) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlpha();
        mc.getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}