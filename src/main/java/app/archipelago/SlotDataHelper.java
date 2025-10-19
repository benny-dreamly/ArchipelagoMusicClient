package app.archipelago;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class SlotDataHelper {

    private static Map<String, SlotOption> slotOptions = Collections.emptyMap();

    public static void loadSlotOptions(File configDir) {
        File file = new File(configDir, "slot_data.json");
        if (!file.exists()) {
            System.err.println("No slot_data.json found in " + configDir.getAbsolutePath());
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, SlotOption>>() {}.getType();
            slotOptions = new Gson().fromJson(reader, type);
            System.out.println("Loaded slot_data.json with " + slotOptions.size() + " keys.");
        } catch (IOException e) {
            e.printStackTrace();
            slotOptions = Collections.emptyMap();
        }
    }

    public static Set<String> getEnabledAlbums(Map<String, Object> slotData) {
        Set<String> enabled = new HashSet<>();
        if (slotOptions.isEmpty() || slotData == null) return enabled;

        for (Map.Entry<String, Object> entry : slotData.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            // ignore unknown keys
            if (!slotOptions.containsKey(key)) continue;

            SlotOption option = slotOptions.get(key);
            if (option == null) continue;

            if (val instanceof Number && ((Number) val).intValue() == 1) {
                if ("album".equals(option.type)) {
                    enabled.add(option.display_name);
                }
            }
        }

        return enabled;
    }

    public static Set<String> getEnabledCategories(Map<String, Object> slotData) {
        Set<String> enabled = new HashSet<>();
        if (slotOptions.isEmpty() || slotData == null) return enabled;

        for (Map.Entry<String, Object> entry : slotData.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            // ignore unknown keys
            if (!slotOptions.containsKey(key)) continue;

            SlotOption option = slotOptions.get(key);
            if (option == null) continue;

            if (val instanceof Number && ((Number) val).intValue() == 1) {
                if ("song_category".equals(option.type)) {
                    enabled.add(option.display_name);
                }
            }
        }

        return enabled;
    }

    public static class SlotOption {
        public String type;
        public String display_name;
    }
}