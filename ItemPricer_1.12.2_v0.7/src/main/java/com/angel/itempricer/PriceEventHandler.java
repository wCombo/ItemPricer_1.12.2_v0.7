package com.angel.itempricer;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.Map;

public class PriceEventHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onGuiRender(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiContainer)) return;
        GuiContainer gui = (GuiContainer) event.getGui();
        
        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            double precioReal = calcularTotal(stack);
            double precioAH = extraerPrecioUnicoins(stack);

            if (precioAH > 0 && precioReal > 0) {
                double diff = precioAH - precioReal;
                int color = 0x80FFD700; // Dorado

                if (diff <= -4.0) color = 0xA000FF00;      // Verde
                else if (diff >= 4.0) color = 0xA0FF0000;  // Rojo

                int x = gui.getGuiLeft() + slot.xPos;
                int y = gui.getGuiTop() + slot.yPos;
                
                // Esquina superior izquierda (5x5 pixeles)
                Gui.drawRect(x, y, x + 5, y + 5, color);
            }
        }
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        double precioTotal = calcularTotal(stack);
        double precioAH = extraerPrecioUnicoins(stack);

        if (precioTotal <= 0) {
            event.getToolTip().add(TextFormatting.RED + "Sin precio en config");
        } else {
            event.getToolTip().add(TextFormatting.GRAY + "Precio: " + TextFormatting.WHITE + "$" + String.format("%.2f", precioTotal));
        }

        if (precioAH > 0 && precioTotal > 0) {
            double diff = precioAH - precioTotal;
            if (diff <= -4.0) {
                event.getToolTip().add(TextFormatting.GREEN + "Precio barato");
            } else if (diff >= 4.0) {
                event.getToolTip().add(TextFormatting.RED + "Precio caro");
            } else {
                event.getToolTip().add(TextFormatting.GOLD + "Precio justo");
            }
        }
    }

    private double calcularTotal(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        double valor = calcularItemYEnchants(stack);
        if (stack.getItem().getRegistryName().toString().contains("shulker_box")) {
            valor += calcularContenidoShulker(stack);
        }
        return valor;
    }

    private double calcularItemYEnchants(ItemStack s) {
        double base = Precios.getPrecioBase(s);
        double total = (base > 0) ? (base * s.getCount()) : 0;
        
        Map<net.minecraft.enchantment.Enchantment, Integer> enchants = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(s);
        for (Map.Entry<net.minecraft.enchantment.Enchantment, Integer> e : enchants.entrySet()) {
            if (e.getKey() == null || e.getKey().getRegistryName() == null) continue;
            String name = e.getKey().getRegistryName().getResourcePath();
            int level = e.getValue();
            
            String keyNivel = "enchant:" + name + ":" + level;
            if (Precios.VALORES.containsKey(keyNivel)) {
                total += Precios.VALORES.get(keyNivel);
            } else {
                total += Precios.VALORES.getOrDefault("enchant:" + name, 0.0) * level;
            }
        }
        return total;
    }

    private double calcularContenidoShulker(ItemStack shulker) {
        double suma = 0;
        if (shulker.hasTagCompound() && shulker.getTagCompound().hasKey("BlockEntityTag", 10)) {
            NBTTagList items = shulker.getTagCompound().getCompoundTag("BlockEntityTag").getTagList("Items", 10);
            for (int i = 0; i < items.tagCount(); i++) {
                suma += calcularTotal(new ItemStack(items.getCompoundTagAt(i)));
            }
        }
        return suma;
    }

    private double extraerPrecioUnicoins(ItemStack stack) {
        if (!stack.hasTagCompound()) return -1;
        try {
            NBTTagList lore = stack.getTagCompound().getCompoundTag("display").getTagList("Lore", 8);
            for (int i = 0; i < lore.tagCount(); i++) {
                String limpia = lore.getStringTagAt(i).replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
                if (limpia.contains("Unicoins:")) {
                    String n = limpia.substring(limpia.indexOf("$") + 1).replace(",", "").trim();
                    return Double.parseDouble(n);
                }
            }
        } catch (Exception e) { return -1; }
        return -1;
    }
}