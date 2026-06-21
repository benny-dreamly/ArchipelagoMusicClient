package app.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

public class SlotDataUtils {

    private SlotDataUtils() {} // helper class

    public static Map<String, Object> parseSlotData(JsonElement json) {
        return new Gson().fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    public static boolean parseBooleanSlot(Map<String, Object> slotMap, String key) {
        if (!slotMap.containsKey(key)) return false;
        Object val = slotMap.get(key);
        if (val instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(val.toString());
    }
}
