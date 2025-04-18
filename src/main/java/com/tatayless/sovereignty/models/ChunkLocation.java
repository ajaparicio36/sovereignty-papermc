package com.tatayless.sovereignty.models;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Objects;

public class ChunkLocation {
    private final int x;
    private final int z;
    private final String worldName;

    public ChunkLocation(int x, int z, String worldName) {
        this.x = x;
        this.z = z;
        this.worldName = worldName;
    }

    public ChunkLocation(Chunk chunk) {
        this.x = chunk.getX();
        this.z = chunk.getZ();
        this.worldName = chunk.getWorld().getName();
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public String getWorldName() {
        return worldName;
    }

    public Chunk toChunk() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World '" + worldName + "' does not exist!");
        }
        return world.getChunkAt(x, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ChunkLocation that = (ChunkLocation) o;
        return x == that.x && z == that.z && Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z, worldName);
    }

    @Override
    public String toString() {
        return worldName + ":" + x + "," + z;
    }

    public static ChunkLocation fromString(String str) {
        String[] parts = str.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid chunk location format: " + str);
        }

        String worldName = parts[0];
        String[] coords = parts[1].split(",");
        if (coords.length != 2) {
            throw new IllegalArgumentException("Invalid chunk coordinates format: " + parts[1]);
        }

        try {
            int x = Integer.parseInt(coords[0]);
            int z = Integer.parseInt(coords[1]);
            return new ChunkLocation(x, z, worldName);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid chunk coordinates: " + parts[1], e);
        }
    }
}
