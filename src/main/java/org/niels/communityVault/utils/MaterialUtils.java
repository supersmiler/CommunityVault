package org.niels.communityVault.utils;


import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class MaterialUtils {

    // Utility method to get all materials that match a certain pattern (e.g., "_WOOL" for all wool colors)
    public static Material[] getMaterialsByPattern(String pattern) {
        List<Material> matchingMaterials = new ArrayList<>();

        for (Material material : Material.values()) {
            if (material.name().endsWith(pattern)) {
                matchingMaterials.add(material);
            }
        }

        return matchingMaterials.toArray(new Material[0]);
    }

    // Utility method to get materials by multiple patterns (e.g., "_WOOL" and "_CARPET")
    public static Material[] getMaterialsByPatterns(String... patterns) {
        List<Material> matchingMaterials = new ArrayList<>();

        for (Material material : Material.values()) {
            for (String pattern : patterns) {
                if (material.name().endsWith(pattern)) {
                    matchingMaterials.add(material);
                }
            }
        }

        return matchingMaterials.toArray(new Material[0]);
    }

    // Helper method to get all materials not in existing categories
    public static Material[] getAllRemainingMaterials(Material[]... excludedCategories) {
        List<Material> remainingMaterials = new ArrayList<>(List.of(Material.values()));

        // Remove all materials that are in the excluded categories
        for (Material[] category : excludedCategories) {
            remainingMaterials.removeAll(List.of(category));
        }

        return remainingMaterials.toArray(new Material[0]);
    }
}


