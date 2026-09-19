package com.angel.itempricer;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.Arrays;

@Mod(modid = "itempricer", name = "Item Pricer", version = "0.8", clientSideOnly = true)
public class ItemPricer {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Precios.init(event.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new PriceEventHandler());
        MinecraftForge.EVENT_BUS.register(new VillagerRenderHandler());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChat(ClientChatEvent event) {
        String rawMsg = event.getMessage().trim();
        if (!rawMsg.startsWith("/")) return;

        String[] parts = rawMsg.split(" ");
        String cmd = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        if (cmd.equals("/pricereload")) {
            event.setCanceled(true);
            Precios.cargarConfig();
            sendFeedback(TextFormatting.GREEN + "Precios actualizados");
        } 
        else if (cmd.equals("/checkinventory")) {
            event.setCanceled(true);
            checkInventory();
        }
        else if (cmd.equals("/setprice")) {
            event.setCanceled(true);
            if (args.length > 0) setPrice(args[0]);
            else sendFeedback(TextFormatting.RED + "Uso: /setprice <precio>");
        }
        else if (cmd.equals("/setinventoryprice")) {
            event.setCanceled(true);
            if (args.length > 0) setInventoryPrice(args[0]);
            else sendFeedback(TextFormatting.RED + "Uso: /setinventoryprice <precio>");
        }
        else if (cmd.equals("/calc")) {
            event.setCanceled(true);
            if (args.length > 0) runCalc(args);
            else sendFeedback(TextFormatting.RED + "Uso: /calc <operacion>");
        }
    }

    private void setPrice(String priceStr) {
        try {
            double price = Double.parseDouble(priceStr);
            ItemStack stack = Minecraft.getMinecraft().player.getHeldItemMainhand();
            if (stack.isEmpty()) {
                sendFeedback(TextFormatting.RED + "Mano vacia");
                return;
            }
            String key = Precios.generarKey(stack);
            Precios.VALORES.put(key, price);
            Precios.guardarConfig();
            sendFeedback(TextFormatting.GREEN + "Precio fijado: " + key + " = $" + price);
        } catch (Exception e) { sendFeedback(TextFormatting.RED + "Error al fijar precio"); }
    }

    private void setInventoryPrice(String priceStr) {
        try {
            double price = Double.parseDouble(priceStr);
            int count = 0;
            for (ItemStack stack : Minecraft.getMinecraft().player.inventory.mainInventory) {
                if (!stack.isEmpty()) {
                    String key = Precios.generarKey(stack);
                    Precios.VALORES.put(key, price);
                    count++;
                }
            }
            Precios.guardarConfig();
            sendFeedback(TextFormatting.GREEN + "Fijado precio a " + count + " items");
        } catch (Exception e) { sendFeedback(TextFormatting.RED + "Error en inventario"); }
    }

    private void runCalc(String[] args) {
        try {
            String expr = String.join("", args).replace(",", ".");
            double result = eval(expr);
            String resultStr = String.format("%.2f", result);
            
            TextComponentString msg = new TextComponentString(TextFormatting.GOLD + "Resultado: " + TextFormatting.WHITE + resultStr + " ");
            TextComponentString btn = new TextComponentString(TextFormatting.AQUA + "[FIJAR PRECIO]");
            
            btn.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/setprice " + resultStr));
            msg.appendSibling(btn);
            
            Minecraft.getMinecraft().player.sendMessage(msg);
        } catch (Exception e) { sendFeedback(TextFormatting.RED + "Error en calculo"); }
    }

    private void checkInventory() {
        sendFeedback(TextFormatting.GOLD + "--- Items sin precio ---");
        boolean found = false;
        if (Minecraft.getMinecraft().player != null) {
            for (ItemStack st : Minecraft.getMinecraft().player.inventory.mainInventory) {
                if (!st.isEmpty()) {
                    String k = Precios.generarKey(st);
                    if (!Precios.VALORES.containsKey(k) || Precios.VALORES.get(k) <= 0) {
                        sendFeedback(TextFormatting.WHITE + k);
                        found = true;
                    }
                }
            }
        }
        if (!found) sendFeedback(TextFormatting.GREEN + "Todo tiene precio");
    }

    private void sendFeedback(String text) {
        if (Minecraft.getMinecraft().player != null)
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(text));
    }

    // Evaluador matematico interno
    private double eval(final String str) {
        return new Object() {
            int pos = -1, ch;
            void nextChar() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }
            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }
            double parse() { nextChar(); double x = parseExpression(); return x; }
            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }
            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }
            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();
                double x;
                int startPos = this.pos;
                if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else { return 0; }
                return x;
            }
        }.parse();
    }
}