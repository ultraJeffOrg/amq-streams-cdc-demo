package com.redhat.demo.producer;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

/**
 * Generates random product data for testing CDC pipelines.
 */
@ApplicationScoped
public class ProductGenerator {

    private static final List<String> ADJECTIVES = List.of(
            "Premium", "Deluxe", "Ultra", "Super", "Mega", "Pro",
            "Elite", "Classic", "Modern", "Vintage", "Smart", "Eco",
            "Compact", "Portable", "Heavy-Duty", "Lightweight"
    );

    private static final List<String> NOUNS = List.of(
            "Widget", "Gadget", "Device", "Tool", "Component", "Module",
            "Unit", "System", "Kit", "Pack", "Set", "Bundle",
            "Accessory", "Adapter", "Connector", "Interface"
    );

    private static final List<String> CATEGORIES = List.of(
            "Electronics", "Hardware", "Software", "Networking",
            "Storage", "Computing", "Audio", "Video"
    );

    private final Random random = new Random();

    /**
     * Generates a random product with realistic-looking data.
     */
    public Product generateProduct() {
        String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String noun = NOUNS.get(random.nextInt(NOUNS.size()));
        String category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
        int modelNumber = random.nextInt(9000) + 1000;

        String name = String.format("%s %s %d", adjective, noun, modelNumber);
        String description = String.format("High-quality %s %s for %s applications. Model: %s-%d",
                adjective.toLowerCase(), noun.toLowerCase(), category.toLowerCase(), 
                noun.substring(0, 3).toUpperCase(), modelNumber);

        // Generate weight between 0.1 and 50.0 kg
        BigDecimal weight = BigDecimal.valueOf(random.nextDouble() * 49.9 + 0.1)
                .setScale(2, RoundingMode.HALF_UP);

        return new Product(name, description, weight);
    }
}

