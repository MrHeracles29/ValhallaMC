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
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ValhallaMC extends JavaPlugin implements CommandExecutor {

    private File outputFolder;
    private boolean isConverting = false;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        outputFolder = new File(getDataFolder(), "output");
        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
        }

        if (getCommand("iaconvert") != null) {
            getCommand("iaconvert").setExecutor(this);
        }

        getLogger().info("¡Plugin ValhallaMC cargado exitosamente!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("iaconvert")) {
            
            if (isConverting) {
                sender.sendMessage(ChatColor.RED + "[ValhallaMC] Ya hay una conversion en progreso. Por favor, espera.");
                return true;
            }

            isConverting = true;
            sender.sendMessage(ChatColor.YELLOW + "[ValhallaMC] Iniciando la conversion de texturas para Bedrock...");
            
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    File itemsAdderPack = new File("plugins/ItemsAdder/data/resource_pack");
                    
                    if (!itemsAdderPack.exists()) {
                        sender.sendMessage(ChatColor.RED + "[Error] No se encontro la carpeta en: plugins/ItemsAdder/data/resource_pack");
                        isConverting = false;
                        return;
                    }

                    File tempDir = new File(getDataFolder(), "temp_bedrock_pack");
                    if (tempDir.exists()) deleteDirectory(tempDir);
                    tempDir.mkdirs();

                    File bedrockTexturesFolder = new File(tempDir, "textures/items");
                    bedrockTexturesFolder.mkdirs();

                    JsonObject itemTextureJson = new JsonObject();
                    itemTextureJson.addProperty("resource_pack_name", "ValhallaMC Bedrock Pack");
                    itemTextureJson.addProperty("texture_name", "atlas.items");
                    
                    JsonObject textureData = new JsonObject();

                    File javaAssets = new File(itemsAdderPack, "assets");
                    if (javaAssets.exists()) {
                        processAndCopyPack(javaAssets, bedrockTexturesFolder, textureData);
                    }

                    itemTextureJson.add("texture_data", textureData);

                    File texturesConfigDir = new File(tempDir, "textures");
                    File itemTextureFile = new File(texturesConfigDir, "item_texture.json");
                    try (FileWriter writer = new FileWriter(itemTextureFile)) {
                        writer.write(itemTextureJson.toString());
                    }

                    generateManifest(tempDir);

                    File zipOutput = new File(outputFolder, "Valhalla_Bedrock_Pack.mcpack");
                    zipFolder(tempDir, zipOutput);

                    deleteDirectory(tempDir);

                    sender.sendMessage(ChatColor.GREEN + "[Exito] ¡Conversion de ValhallaMC completada!");
                    sender.sendMessage(ChatColor.GREEN + "[Exito] Archivo generado en: plugins/ValhallaMC/output/Valhalla_Bedrock_Pack.mcpack");

                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "[Error] Ocurrio un fallo critico. Revisa la consola.");
                    e.printStackTrace();
                } finally {
                    isConverting = false;
                }
            });
            return true;
        }
        return false;
    }

    private void processAndCopyPack(File source, File destinationBase, JsonObject textureData) throws IOException {
        File[] files = source.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                processAndCopyPack(file, destinationBase, textureData);
            } else if (file.getName().endsWith(".png")) {
                String fileNameWithOutExt = file.getName().substring(0, file.getName().lastIndexOf('.'));
                String cleanId = fileNameWithOutExt.toLowerCase().replaceAll("[^a-z0-9_]", "_");

                File destFile = new File(destinationBase, file.getName());
                Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                JsonObject texturePathObj = new JsonObject();
                texturePathObj.addProperty("textures", "textures/items/" + fileNameWithOutExt);
                textureData.add(cleanId, texturePathObj);
            }
        }
    }

    private void generateManifest(File folder) throws IOException {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);

        JsonObject header = new JsonObject();
        header.addProperty("description", "Texturas oficiales de ValhallaMC");
        header.addProperty("name", "ValhallaMC Bedrock Pack");
        header.addProperty("uuid", UUID.randomUUID().toString());
        
        JsonArray version = new JsonArray();
        version.add(1); version.add(0); version.add(0);
        header.add("version", version);

        JsonArray minEngine = new JsonArray();
        minEngine.add(1); minEngine.add(21); minEngine.add(0);
        header.add("min_engine_version", minEngine);
        manifest.add("header", header);

        JsonArray modules = new JsonArray();
        JsonObject module = new JsonObject();
        module.addProperty("description", "Texturas oficiales de ValhallaMC");
        module.addProperty("type", "resources");
        module.addProperty("uuid", UUID.randomUUID().toString());
        module.add("version", version);
        modules.add(module);
        manifest.add("modules", modules);

        File manifestFile = new File(folder, "manifest.json");
        try (FileWriter writer = new FileWriter(manifestFile)) {
            writer.write(manifest.toString());
        }
    }

    private void zipFolder(File srcFolder, File destZipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(destZipFile);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            zipDir(srcFolder, srcFolder, zipOut);
        }
    }

    private void zipDir(File rootFolder, File srcFolder, ZipOutputStream zipOut) throws IOException {
        File[] files = srcFolder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                zipDir(rootFolder, file, zipOut);
            } else {
                String relPath = rootFolder.toURI().relativize(file.toURI()).getPath();
                zipOut.putNextEntry(new ZipEntry(relPath));
                Files.copy(file.toPath(), zipOut);
                zipOut.closeEntry();
            }
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteDirectory(file);
            }
        }
        dir.delete();
    }
}
