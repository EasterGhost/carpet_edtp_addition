package org.edtp.carpet_edtp_addition;

import carpet.api.settings.CarpetRule;
import carpet.api.settings.SettingsManager;
import carpet.CarpetServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.TradeOffers;
import net.minecraft.item.Items;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class CarpetEdtpAdditionSettings {
    
    public static final EdtpCarpetRule softObsidian = new EdtpCarpetRule(
        "softObsidian", 
        false, 
        "Set the hardness of obsidian to be the same as end stone"
    );
    
    public static final EdtpCarpetRule unPushableArmorStands = new EdtpCarpetRule(
        "unPushableArmorStands",
        false,
        "Armor stands won't be pushed by attacks, explosions or flowing fluids"
    );
    
    public static final EdtpCarpetRule safeTeleport = new EdtpCarpetRule(
        "safeTeleport",
        false,
        "Prevents teleportation to unsafe locations (void, suffocation)"
    );
    
    public static final EdtpCarpetRule tickCommandForAll = new EdtpCarpetRule(
        "tickCommandForAll",
        false,
        "Allows non-op players to use the /tick command"
    );
    
    public static final EdtpCarpetRule noFurnaceAsh = new EdtpCarpetRule(
        "noFurnaceAsh",
        false,
        "Items without recipes pass through furnaces instantly, preventing ash waste"
    );
    
    public static final EdtpCarpetRule noPlayerPortals = new EdtpCarpetRule(
        "noPlayerPortals",
        false,
        "Prevents players from using portals"
    );
    
    public static final EdtpCarpetRule strongerBundle = new EdtpCarpetRule(
        "strongerBundle",
        false,
        "Allows shulker boxes to be inserted into bundles (max 8), prevents bundles in shulker boxes"
    );
    
    public static final EdtpCarpetRule toughArmorStands = new EdtpCarpetRule(
        "toughArmorStands",
        false,
        "Armor stands won't take damage from attacks"
    );
    
    public static final EdtpCarpetRule toughSlimeBlocks = new EdtpCarpetRule(
        "toughSlimeBlocks",
        false,
        "Set the hardness of slime blocks and honey blocks to be the same as end stone"
    );

    public static final EdtpCarpetIntRule boostTradeEnchants = new EdtpCarpetIntRule(
        "boostTradeEnchants",
        0,
        "Boosts villager trade enchant levels and adds enchanted diamond hoe trade for journeyman toolsmith"
    );

    public static final EdtpCarpetStringRule beesDimCurfew = new EdtpCarpetStringRule(
        "beesDimCurfew",
        "false",
        "In the Nether/End, forces bees to enter hives and prevents them from leaving. Options: false, nether, end, true"
    );

    private static final int TOOLSMITH_JOURNEYMAN_LEVEL = 3;
    private static volatile TradeOffers.Factory cachedHoeListing;

    public static void register() {
        try {
            for (Field field : CarpetEdtpAdditionSettings.class.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) && 
                    Modifier.isStatic(modifiers) && 
                    Modifier.isFinal(modifiers) &&
                    CarpetRule.class.isAssignableFrom(field.getType())) {
                    
                    CarpetRule<?> rule = (CarpetRule<?>) field.get(null);
                    CarpetServer.settingsManager.addCarpetRule(rule);
                }
            }
            refreshBoostTradeEnchantsTrades();
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to register Carpet rules via reflection", e);
        }
    }

    public static boolean isBeesDimCurfewEnabled(World world) {
        if (world == null) {
            return false;
        }
        String value = beesDimCurfew.value();
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("both")) {
            return world.getRegistryKey() == World.NETHER || world.getRegistryKey() == World.END;
        }
        if (normalized.equals("false")) {
            return false;
        }
        if (normalized.equals("nether") || normalized.equals("the_nether")) {
            return world.getRegistryKey() == World.NETHER;
        }
        if (normalized.equals("end") || normalized.equals("the_end")) {
            return world.getRegistryKey() == World.END;
        }
        return false;
    }

    public static int getBoostTradeEnchantsLevel() {
        Integer level = boostTradeEnchants.value();
        return level == null ? 0 : level;
    }

    public static void refreshBoostTradeEnchantsTrades() {
        try {
            Map<?, ?> tradesByProfession = findTradesByProfession();
            if (tradesByProfession == null) {
                return;
            }
            Object toolsmithTrades = tradesByProfession.get(VillagerProfession.TOOLSMITH);
            if (toolsmithTrades == null) {
                return;
            }
            TradeOffers.Factory[] levelTrades = getLevelTrades(toolsmithTrades, TOOLSMITH_JOURNEYMAN_LEVEL);
            if (levelTrades == null) {
                return;
            }
            TradeOffers.Factory hoeListing = getOrCreateHoeListing();
            if (hoeListing == null) {
                return;
            }

            boolean contains = false;
            for (TradeOffers.Factory listing : levelTrades) {
                if (listing == hoeListing) {
                    contains = true;
                    break;
                }
            }

            int level = getBoostTradeEnchantsLevel();
            if (level >= 1) {
                if (!contains) {
                    TradeOffers.Factory[] updated = Arrays.copyOf(levelTrades, levelTrades.length + 1);
                    updated[levelTrades.length] = hoeListing;
                    setLevelTrades(toolsmithTrades, TOOLSMITH_JOURNEYMAN_LEVEL, updated);
                }
                return;
            }

            if (contains) {
                TradeOffers.Factory[] updated = new TradeOffers.Factory[levelTrades.length - 1];
                int index = 0;
                for (TradeOffers.Factory listing : levelTrades) {
                    if (listing != hoeListing) {
                        updated[index++] = listing;
                    }
                }
                setLevelTrades(toolsmithTrades, TOOLSMITH_JOURNEYMAN_LEVEL, updated);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Map<?, ?> findTradesByProfession() throws IllegalAccessException {
        for (Field field : TradeOffers.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers)) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map && map.containsKey(VillagerProfession.TOOLSMITH)) {
                return map;
            }
        }
        return null;
    }

    private static TradeOffers.Factory[] getLevelTrades(Object toolsmithTrades, int level) {
        if (toolsmithTrades instanceof Int2ObjectMap<?> int2ObjectMap) {
            Object result = int2ObjectMap.get(level);
            return castListings(result);
        }
        if (toolsmithTrades instanceof Map<?, ?> map) {
            Object result = map.get(level);
            return castListings(result);
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setLevelTrades(Object toolsmithTrades, int level, TradeOffers.Factory[] listings) {
        if (toolsmithTrades instanceof Int2ObjectMap int2ObjectMap) {
            int2ObjectMap.put(level, listings);
        } else if (toolsmithTrades instanceof Map map) {
            map.put(level, listings);
        }
    }

    private static TradeOffers.Factory[] castListings(Object result) {
        if (result instanceof TradeOffers.Factory[] listings) {
            return listings;
        }
        return null;
    }

    private static TradeOffers.Factory getOrCreateHoeListing() {
        if (cachedHoeListing != null) {
            return cachedHoeListing;
        }
        cachedHoeListing = new TradeOffers.SellEnchantedToolFactory(Items.DIAMOND_HOE, 12, 3, 10);
        return cachedHoeListing;
    }
    
    public static class EdtpCarpetRule implements CarpetRule<Boolean> {
        private final String name;
        private final String description;
        private Boolean value;
        private final Boolean defaultValue;
        
        public EdtpCarpetRule(String name, Boolean defaultValue, String description) {
            this.name = name;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.description = description;
        }
        
        @Override
        public String name() {
            return name;
        }
        
        @Override
        public List<Text> extraInfo() {
            return List.of();
        }
        
        @Override
        public Collection<String> categories() {
            return List.of("SURVIVAL", "EDTP");
        }
        
        @Override
        public Collection<String> suggestions() {
            return List.of("true", "false");
        }
        
        @Override
        public SettingsManager settingsManager() {
            return CarpetServer.settingsManager;
        }
        
        @Override
        public Boolean value() {
            return value;
        }
        
        @Override
        public boolean canBeToggledClientSide() {
            return false;
        }
        
        @Override
        public Class<Boolean> type() {
            return Boolean.class;
        }
        
        @Override
        public Boolean defaultValue() {
            return defaultValue;
        }
        
        @Override
        public boolean strict() {
            return true;
        }
        
        @Override
        public void set(ServerCommandSource source, String value) {
            this.value = Boolean.parseBoolean(value);
            if (source != null) {
                settingsManager().notifyRuleChanged(source, this, value);
            }
        }
        
        @Override
        public void set(ServerCommandSource source, Boolean value) {
            this.value = value;
            if (source != null) {
                settingsManager().notifyRuleChanged(source, this, value.toString());
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class EdtpCarpetStringRule implements CarpetRule<String> {
        private final String name;
        private final String description;
        private String value;
        private final String defaultValue;

        public EdtpCarpetStringRule(String name, String defaultValue, String description) {
            this.name = name;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.description = description;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<Text> extraInfo() {
            return List.of();
        }

        @Override
        public Collection<String> categories() {
            return List.of("SURVIVAL", "EDTP");
        }

        @Override
        public Collection<String> suggestions() {
            return List.of("false", "nether", "end", "true");
        }

        @Override
        public SettingsManager settingsManager() {
            return CarpetServer.settingsManager;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public boolean canBeToggledClientSide() {
            return false;
        }

        @Override
        public Class<String> type() {
            return String.class;
        }

        @Override
        public String defaultValue() {
            return defaultValue;
        }

        @Override
        public boolean strict() {
            return true;
        }

        @Override
        public void set(ServerCommandSource source, String value) {
            this.value = value;
            if (source != null) {
                settingsManager().notifyRuleChanged(source, this, value);
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class EdtpCarpetIntRule implements CarpetRule<Integer> {
        private final String name;
        private final String description;
        private Integer value;
        private final Integer defaultValue;

        public EdtpCarpetIntRule(String name, Integer defaultValue, String description) {
            this.name = name;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.description = description;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<Text> extraInfo() {
            return List.of();
        }

        @Override
        public Collection<String> categories() {
            return List.of("SURVIVAL", "EDTP");
        }

        @Override
        public Collection<String> suggestions() {
            return List.of("0", "1", "2", "3");
        }

        @Override
        public SettingsManager settingsManager() {
            return CarpetServer.settingsManager;
        }

        @Override
        public Integer value() {
            return value;
        }

        @Override
        public boolean canBeToggledClientSide() {
            return false;
        }

        @Override
        public Class<Integer> type() {
            return Integer.class;
        }

        @Override
        public Integer defaultValue() {
            return defaultValue;
        }

        @Override
        public boolean strict() {
            return true;
        }

        @Override
        public void set(ServerCommandSource source, String value) {
            this.value = Integer.parseInt(value);
            if (source != null) {
                settingsManager().notifyRuleChanged(source, this, value);
            }
            if (CarpetEdtpAdditionSettings.boostTradeEnchants.name().equals(this.name)) {
                CarpetEdtpAdditionSettings.refreshBoostTradeEnchantsTrades();
            }
        }

        @Override
        public void set(ServerCommandSource source, Integer value) {
            this.value = value;
            if (source != null) {
                settingsManager().notifyRuleChanged(source, this, value.toString());
            }
            if (CarpetEdtpAdditionSettings.boostTradeEnchants.name().equals(this.name)) {
                CarpetEdtpAdditionSettings.refreshBoostTradeEnchantsTrades();
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }
}