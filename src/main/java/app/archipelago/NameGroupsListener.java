/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.archipelago;

import com.google.gson.reflect.TypeToken;
import io.github.archipelagomw.events.ArchipelagoEventListener;
import io.github.archipelagomw.events.RetrievedEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class NameGroupsListener {

    private static final String ITEM_GROUPS_PREFIX = "_read_item_name_groups_";
    private static final String LOC_GROUPS_PREFIX = "_read_location_name_groups_";

    /**
     * Groups decoded from the server plus a flag per key saying whether the key
     * was actually present in the retrieved data. A present key may still map to
     * an empty group set; the consumer decides what an absence means.
     */
    public record Result(
            Map<String, List<String>> itemGroups,
            boolean itemGroupsPresent,
            Map<String, List<String>> locationGroups,
            boolean locationGroupsPresent) {
    }

    private final Consumer<Result> onGroupsRetrieved;

    public NameGroupsListener(Consumer<Result> onGroupsRetrieved) {
        this.onGroupsRetrieved = onGroupsRetrieved;
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onRetrieved(RetrievedEvent event) {
        Map<String, List<String>> itemGroups = Collections.emptyMap();
        Map<String, List<String>> locationGroups = Collections.emptyMap();
        boolean itemGroupsPresent = false;
        boolean locationGroupsPresent = false;

        for (String key : event.data.keySet()) {
            if (key.startsWith(ITEM_GROUPS_PREFIX)) {
                Map<String, List<String>> decoded = event.getValueAsObject(key,
                        new TypeToken<Map<String, List<String>>>() {}.getType());
                itemGroups = decoded != null ? decoded : Collections.emptyMap();
                itemGroupsPresent = true;
            } else if (key.startsWith(LOC_GROUPS_PREFIX)) {
                Map<String, List<String>> decoded = event.getValueAsObject(key,
                        new TypeToken<Map<String, List<String>>>() {}.getType());
                locationGroups = decoded != null ? decoded : Collections.emptyMap();
                locationGroupsPresent = true;
            }
        }

        if (itemGroupsPresent || locationGroupsPresent) {
            onGroupsRetrieved.accept(new Result(itemGroups, itemGroupsPresent,
                    locationGroups, locationGroupsPresent));
        }
    }
}