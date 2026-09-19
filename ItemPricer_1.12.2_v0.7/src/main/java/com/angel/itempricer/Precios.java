package com.angel.itempricer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.ItemStack;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class Precios {
    public static Map<String, Double> VALORES = new HashMap<>();
    private static File configFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void init(File configDir) {
        configFile = new File(configDir, "itempricer_precios.json");
        cargarConfig();
    }

    public static void cargarConfig() {
        if (!configFile.exists()) {
            VALORES.put("minecraft:stone", 0.1);
            guardarConfig();
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            VALORES = GSON.fromJson(reader, new TypeToken<Map<String, Double>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void guardarConfig() {
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(VALORES, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double getPrecioBase(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return VALORES.getOrDefault(generarKey(stack), 0.0);
    }

    public static String generarKey(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == null || stack.getItem().getRegistryName() == null) return "unknown";
        String key = stack.getItem().getRegistryName().toString();
        if (!stack.isItemStackDamageable()) {
            key += ":" + stack.getMetadata();
        }
        return key;
    }
}