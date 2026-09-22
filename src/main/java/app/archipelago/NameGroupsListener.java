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
import java.util.function.BiConsumer;

public class NameGroupsListener {

    private static final String ITEM_GROUPS_PREFIX = "_read_item_name_groups_";
    private static final String LOC_GROUPS_PREFIX = "_read_location_name_groups_";

    private final BiConsumer<Map<String, List<String>>, Map<String, List<String>>> onGroupsRetrieved;

    public NameGroupsListener(BiConsumer<Map<String, List<String>>, Map<String, List<String>>> onGroupsRetrieved) {
        this.onGroupsRetrieved = onGroupsRetrieved;
    }

    @SuppressWarnings("unused")
    @ArchipelagoEventListener
    public void onRetrieved(RetrievedEvent event) {
        Map<String, List<String>> itemGroups = Collections.emptyMap();
        Map<String, List<String>> locationGroups = Collections.emptyMap();

        for (String key : event.data.keySet()) {
            if (key.startsWith(ITEM_GROUPS_PREFIX)) {
                itemGroups = event.getValueAsObject(key,
                        new TypeToken<Map<String, List<String>>>() {}.getType());
                if (itemGroups == null) itemGroups = Collections.emptyMap();
            } else if (key.startsWith(LOC_GROUPS_PREFIX)) {
                locationGroups = event.getValueAsObject(key,
                        new TypeToken<Map<String, List<String>>>() {}.getType());
                if (locationGroups == null) locationGroups = Collections.emptyMap();
            }
        }

        if (!itemGroups.isEmpty() || !locationGroups.isEmpty()) {
            onGroupsRetrieved.accept(itemGroups, locationGroups);
        }
    }
}