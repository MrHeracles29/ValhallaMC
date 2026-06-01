package com.valhallamc.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class ValhallaMC extends JavaPlugin implements CommandExecutor {

    private File outputFolder;
    private boolean isConverting = false;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        outputFolder = new File(getDataFolder(), "output");
        if (!outputFolder.exists()) outputFolder.mkdirs();
        if (getCommand("iaconvert") != null) getCommand("iaconvert").setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("iaconvert")) {
            if (isConverting) { sender.sendMessage(ChatColor.RED + "Ya procesando..."); return true; }
            
            isConverting = true;
            sender.sendMessage(ChatColor.YELLOW + "[ValhallaMC] Buscando el ZIP en plugins/ItemsAdder/output...");
            
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    File outputDir = new File("plugins/ItemsAdder/output");
                    File[] files = outputDir.listFiles((dir, name) -> name.endsWith(".zip"));
                    
                    if (files == null || files.length == 0) {
                        sender.sendMessage(ChatColor.RED + "Error: No encuentro ningún archivo .zip en plugins/ItemsAdder/output");
                        isConverting = false; return;
                    }

                    File zipFile = files[0];
                    File tempExtractDir = new File(getDataFolder(), "unzipped_pack");
                    if (tempExtractDir.exists()) deleteDirectory(tempExtractDir);
                    tempExtractDir.mkdirs();

                    // Descomprimir el ZIP
                    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            File newFile = new File(tempExtractDir, entry.getName());
                            if (entry.isDirectory()) newFile.mkdirs();
                            else {
                                newFile.getParentFile().mkdirs();
                                Files.copy(zis, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }

                    // Ahora procesar como hacíamos antes apuntando a la carpeta extraída
                    File assetsDir = new File(tempExtractDir, "assets");
                    if (!assetsDir.exists()) {
                        sender.sendMessage(ChatColor.RED + "Error: El ZIP no contiene una carpeta 'assets'.");
                        isConverting = false; return;
                    }

                    File tempBedrockDir = new File(getDataFolder(), "temp_bedrock_pack");
                    if (tempBedrockDir.exists()) deleteDirectory(tempBedrockDir);
                    tempBedrockDir.mkdirs();
                    File bedrockItemsDir = new File(tempBedrockDir, "textures/items");
                    bedrockItemsDir.mkdirs();

                    JsonObject textureData = new JsonObject();
                    processAssets(assetsDir, bedrockItemsDir, textureData);

                    JsonObject itemTextureJson = new JsonObject();
                    itemTextureJson.addProperty("resource_pack_name", "ValhallaMC Pack");
                    itemTextureJson.addProperty("texture_name", "atlas.items");
                    itemTextureJson.add("texture_data", textureData);

                    File texConfig = new File(tempBedrockDir, "textures");
                    texConfig.mkdirs();
                    try (FileWriter w = new FileWriter(new File(texConfig, "item_texture.json"))) { w.write(itemTextureJson.toString()); }
                    
                    generateManifest(tempBedrockDir);
                    zipFolder(tempBedrockDir, new File(outputFolder, "Valhalla_Bedrock_Pack.mcpack"));
                    
                    deleteDirectory(tempExtractDir);
                    deleteDirectory(tempBedrockDir);

                    sender.sendMessage(ChatColor.GREEN + "¡Éxito! Pack generado en: plugins/ValhallaMC/output/");
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
                    e.printStackTrace();
                } finally { isConverting = false; }
            });
            return true;
        }
        return false;
    }

    private void processAssets(File source, File dest, JsonObject textureData) {
        File[] files = source.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) processAssets(f, dest, textureData);
            else if (f.getName().endsWith(".png")) {
                String name = f.getName().substring(0, f.getName().lastIndexOf('.'));
                try {
                    Files.copy(f.toPath(), new File(dest, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    JsonObject obj = new JsonObject();
                    obj.addProperty("textures", "textures/items/" + name);
                    textureData.add(name.toLowerCase().replaceAll("[^a-z0-9_]", "_"), obj);
                } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }

    private void generateManifest(File folder) throws IOException {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);
        JsonObject header = new JsonObject();
        header.addProperty("name", "ValhallaMC Bedrock");
        header.addProperty("uuid", UUID.randomUUID().toString());
        JsonArray v = new JsonArray(); v.add(1); v.add(0); v.add(0);
        header.add("version", v);
        header.add("min_engine_version", v);
        manifest.add("header", header);
        JsonArray modules = new JsonArray();
        JsonObject mod = new JsonObject();
        mod.addProperty("type", "resources");
        mod.addProperty("uuid", UUID.randomUUID().toString());
        mod.add("version", v);
        modules.add(mod);
        manifest.add("modules", modules);
        try (FileWriter w = new FileWriter(new File(folder, "manifest.json"))) { w.write(manifest.toString()); }
    }

    private void zipFolder(File src, File dest) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(dest))) {
            Files.walk(src.toPath()).filter(p -> !Files.isDirectory(p)).forEach(p -> {
                try {
                    zos.putNextEntry(new ZipEntry(src.toPath().relativize(p).toString()));
                    Files.copy(p, zos);
                    zos.closeEntry();
                } catch (IOException e) { e.printStackTrace(); }
            });
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) deleteDirectory(f);
        dir.delete();
    }
}
