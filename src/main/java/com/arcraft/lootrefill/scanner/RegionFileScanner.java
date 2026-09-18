package com.arcraft.lootrefill.scanner;

import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegionFileScanner {

    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");

    public record ChunkCoord(int x, int z) {}

    /**
     * Extrae todas las coordenadas de chunks generados leyendo las cabeceras de los archivos MCA de la región.
     * Esta operación es 100% segura para ejecutarse de forma ASÍNCRONA.
     */
    public static List<ChunkCoord> findGeneratedChunks(World world) {
        List<ChunkCoord> coords = new ArrayList<>();
        if (world == null) return coords;

        File worldFolder = world.getWorldFolder();
        File regionFolder = new File(worldFolder, "region");

        // Soporte para dimensiones Nether / End si corresponde
        if (!regionFolder.exists()) {
            File dim1 = new File(worldFolder, "DIM-1/region");
            if (dim1.exists()) regionFolder = dim1;
            else {
                File dim2 = new File(worldFolder, "DIM1/region");
                if (dim2.exists()) regionFolder = dim2;
            }
        }

        if (!regionFolder.exists() || !regionFolder.isDirectory()) {
            return coords;
        }

        File[] regionFiles = regionFolder.listFiles((dir, name) -> name.endsWith(".mca"));
        if (regionFiles == null) return coords;

        byte[] headerBuffer = new byte[4096];

        for (File regionFile : regionFiles) {
            Matcher matcher = REGION_FILE_PATTERN.matcher(regionFile.getName());
            if (!matcher.matches()) continue;

            try {
                int regionX = Integer.parseInt(matcher.group(1));
                int regionZ = Integer.parseInt(matcher.group(2));

                try (FileInputStream fis = new FileInputStream(regionFile)) {
                    int bytesRead = fis.read(headerBuffer);
                    if (bytesRead < 4096) continue;

                    ByteBuffer buffer = ByteBuffer.wrap(headerBuffer);
                    for (int i = 0; i < 1024; i++) {
                        int entry = buffer.getInt();
                        // Si el offset del sector es distinto de 0, el chunk existe y ha sido generado
                        if (entry != 0) {
                            int localX = i % 32;
                            int localZ = i / 32;
                            int chunkX = (regionX << 5) + localX;
                            int chunkZ = (regionZ << 5) + localZ;
                            coords.add(new ChunkCoord(chunkX, chunkZ));
                        }
                    }
                }
            } catch (IOException | NumberFormatException ignored) {
            }
        }

        return coords;
    }
}
