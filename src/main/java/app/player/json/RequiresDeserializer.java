package app.player.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RequiresDeserializer implements JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        List<String> list = new ArrayList<>();

        if (json == null || json.isJsonNull()) {
            return list; // empty list if null
        }

        if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) {
                list.add(e.getAsString());
            }
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            list.add(json.getAsString()); // wrap single string in list
        }

        return list;
    }
}