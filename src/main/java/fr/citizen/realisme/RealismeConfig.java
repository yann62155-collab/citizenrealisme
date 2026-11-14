package fr.citizen.realisme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Réglages modifiables à chaud : config/citizenrealisme/realisme.json puis /realisme admin recharger.
 */
public final class RealismeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static RealismeConfig current = new RealismeConfig();

    public Poids poids = new Poids();
    public Inventaire inventaire = new Inventaire();
    public Craft craft = new Craft();
    public Enchantement enchantement = new Enchantement();
    public Affichage equipementVisible = new Affichage();

    public static final class Poids {
        public boolean actif = true;
        public double capaciteBase = 60.0;
        public double bonusSacoche = 20.0;
        public double bonusSacADos = 45.0;
        /** Au-delà de 100 % : -0.8 % de vitesse par % de surcharge, plafonné à ralentissementMax. */
        public double ralentissementParSurcharge = 0.8;
        public double ralentissementMax = 0.6;
        /** Charge (en part de la capacité) à partir de laquelle on ne peut plus sprinter / sauter. */
        public double seuilSansSprint = 1.25;
        public double seuilSansSaut = 1.5;
        /** Poids d'un objet (kg) par identifiant : remplace le calcul automatique. */
        public Map<String, Double> objets = new LinkedHashMap<>(Map.of(
                "minecraft:anvil", 25.0,
                "minecraft:obsidian", 3.0,
                "minecraft:water_bucket", 4.0,
                "minecraft:lava_bucket", 4.0,
                "minecraft:feather", 0.01,
                "minecraft:paper", 0.01));
        /** Poids par tag d'objets (sans #), vérifiés dans l'ordre. */
        public Map<String, Double> tags = defaultTags();

        private static Map<String, Double> defaultTags() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("c:storage_blocks", 6.0);
            m.put("c:ores", 2.5);
            m.put("c:raw_materials", 0.6);
            m.put("c:ingots", 0.5);
            m.put("c:nuggets", 0.05);
            m.put("c:gems", 0.1);
            m.put("c:dusts", 0.05);
            m.put("minecraft:logs", 1.2);
            m.put("minecraft:planks", 0.5);
            m.put("minecraft:wool", 0.3);
            m.put("minecraft:wool_carpets", 0.1);
            m.put("minecraft:leaves", 0.1);
            m.put("minecraft:saplings", 0.05);
            m.put("minecraft:flowers", 0.02);
            m.put("c:glass_blocks", 0.5);
            m.put("c:sands", 1.3);
            m.put("minecraft:dirt", 1.2);
            m.put("c:stones", 1.5);
            m.put("c:cobblestones", 1.5);
            m.put("c:chests", 3.0);
            m.put("c:barrels", 3.0);
            return m;
        }
    }

    public static final class Inventaire {
        public boolean actif = true;
        /** Rangées de l'inventaire principal (au-dessus de la barre rapide) ouvertes sans sac. */
        public int rangeesDeBase = 1;
        public int rangeesSacoche = 1;
        public int rangeesSacADos = 2;
        public boolean ignorerCreatif = true;
    }

    public static final class Craft {
        public boolean actif = true;
        /** Crafts avec au plus ce nombre de cases remplies et sans outil : instantanés et sans échec. */
        public int casesInstantanees = 2;
        public int ticksParCase = 8;
        public int ticksAvecOutil = 20;
        public int penaliteParCase = 2;
        public int penaliteParIngredient = 3;
        public int bonusOutilEnBonEtat = 5;
        public int reussiteMin = 70;
        public int reussiteMax = 99;
        /** Chance (en %) de perdre chaque ingrédient quand le craft rate. */
        public int pertePourcent = 50;
        public boolean annulerSiBouge = true;
        public double distanceMax = 0.6;
        /** Outil imposé pour un objet : "minecraft:shield": "marteau". */
        public Map<String, String> outilParObjet = new LinkedHashMap<>(Map.of("minecraft:shield", "marteau"));
        /** Objets qui ne demandent jamais d'outil. */
        public List<String> sansOutil = new ArrayList<>(List.of("minecraft:crafting_table"));
    }

    public static final class Enchantement {
        public boolean actif = true;
        /** Taux de réussite de base des 3 options de la table (en %). */
        public int[] reussiteBase = {92, 78, 62};
        public double bonusParBibliotheque = 1.0;
        public int reussiteMin = 5;
        public int reussiteMax = 98;
        /** Sur un échec : chance (en %) que l'objet reçoive une malédiction. */
        public int chanceMalediction = 35;
        public Map<String, Integer> catalyseursOption1 = new LinkedHashMap<>(Map.of("minecraft:amethyst_shard", 1));
        public Map<String, Integer> catalyseursOption2 = new LinkedHashMap<>(Map.of("minecraft:amethyst_shard", 2, "minecraft:glowstone_dust", 1));
        public Map<String, Integer> catalyseursOption3 = new LinkedHashMap<>(Map.of("minecraft:amethyst_shard", 3, "minecraft:ender_pearl", 1));

        public Map<String, Integer> catalysts(int option) {
            return switch (option) {
                case 0 -> catalyseursOption1;
                case 1 -> catalyseursOption2;
                default -> catalyseursOption3;
            };
        }
    }

    public static final class Affichage {
        public boolean actif = true;
        public boolean sac = true;
        public boolean armes = true;
        public boolean outils = true;
    }

    public static RealismeConfig get() {
        return current;
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("citizenrealisme").resolve("realisme.json");
    }

    /** Charge le fichier (le crée avec les valeurs par défaut s'il manque). Renvoie un message d'erreur ou null. */
    public static synchronized String load() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    RealismeConfig loaded = GSON.fromJson(reader, RealismeConfig.class);
                    if (loaded != null) {
                        loaded.fillMissing();
                        current = loaded;
                    }
                }
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(current, writer);
            }
            Weights.clearCache();
            return null;
        } catch (IOException | RuntimeException e) {
            CitizenRealisme.LOGGER.error("[Citizen Réalisme] Réglages illisibles ({}), valeurs précédentes conservées", path, e);
            return e.getMessage();
        }
    }

    private void fillMissing() {
        if (poids == null) poids = new Poids();
        if (poids.objets == null) poids.objets = new LinkedHashMap<>();
        if (poids.tags == null) poids.tags = new LinkedHashMap<>();
        if (inventaire == null) inventaire = new Inventaire();
        if (craft == null) craft = new Craft();
        if (craft.outilParObjet == null) craft.outilParObjet = new LinkedHashMap<>();
        if (craft.sansOutil == null) craft.sansOutil = new ArrayList<>();
        if (enchantement == null) enchantement = new Enchantement();
        if (enchantement.reussiteBase == null || enchantement.reussiteBase.length < 3) enchantement.reussiteBase = new int[]{92, 78, 62};
        if (enchantement.catalyseursOption1 == null) enchantement.catalyseursOption1 = new LinkedHashMap<>();
        if (enchantement.catalyseursOption2 == null) enchantement.catalyseursOption2 = new LinkedHashMap<>();
        if (enchantement.catalyseursOption3 == null) enchantement.catalyseursOption3 = new LinkedHashMap<>();
        if (equipementVisible == null) equipementVisible = new Affichage();
    }
}
