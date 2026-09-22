/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.archipelago;

import io.github.archipelagomw.Print.APPrintColor;
import io.github.archipelagomw.Print.APPrintPart;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.EnumMap;
import java.util.Map;

/**
 * Colors for PrintJSON output, matching the upstream Archipelago client
 * (NetUtils.py JSONtoTextParser color_codes and _handle_item_name).
 */
public final class ChatColors {

    public static final Paint DEFAULT = Color.WHITE;

    private static final Paint CYAN = hex("00EEEE");
    private static final Paint PLUM = hex("AF99EF");
    private static final Paint SLATEBLUE = hex("6D8BE8");
    private static final Paint SALMON = hex("FA8072");
    private static final Paint YELLOW = hex("FAFAD2");
    private static final Paint GREEN = hex("00FF7F");
    private static final Paint BLUE = hex("6495ED");
    private static final Paint MAGENTA = hex("EE00EE");

    private static final Map<APPrintColor, Paint> COLORS = new EnumMap<>(APPrintColor.class);

    static {
        COLORS.put(APPrintColor.none, DEFAULT);
        COLORS.put(APPrintColor.black, hex("000000"));
        COLORS.put(APPrintColor.red, hex("EE0000"));
        COLORS.put(APPrintColor.green, hex("00FF7F"));
        COLORS.put(APPrintColor.yellow, hex("FAFAD2"));
        COLORS.put(APPrintColor.blue, hex("6495ED"));
        COLORS.put(APPrintColor.magenta, hex("EE00EE"));
        COLORS.put(APPrintColor.cyan, hex("00EEEE"));
        COLORS.put(APPrintColor.white, hex("FFFFFF"));
        COLORS.put(APPrintColor.gold, hex("FFD700"));
        COLORS.put(APPrintColor.black_bg, hex("000000"));
        COLORS.put(APPrintColor.red_bg, hex("EE0000"));
        COLORS.put(APPrintColor.green_bg, hex("00FF7F"));
        COLORS.put(APPrintColor.yellow_bg, hex("FAFAD2"));
        COLORS.put(APPrintColor.blue_bg, hex("6495ED"));
        COLORS.put(APPrintColor.magenta_bg, hex("EE00EE"));
        COLORS.put(APPrintColor.cyan_bg, hex("00EEEE"));
        COLORS.put(APPrintColor.white_bg, hex("FFFFFF"));
        COLORS.put(APPrintColor.purple_bg, hex("AF99EF"));
    }

    private ChatColors() {
    }

    public static Paint forPart(APPrintPart part, int mySlot) {
        if (part.color != APPrintColor.none) {
            return COLORS.getOrDefault(part.color, DEFAULT);
        }
        return switch (part.type) {
            case itemID, itemName -> itemColor(part.flags);
            case playerID -> part.player == mySlot ? MAGENTA : YELLOW;
            case playerName -> YELLOW;
            case locationID, locationName -> GREEN;
            case entranceName -> BLUE;
            default -> DEFAULT;
        };
    }

    public static boolean isBold(APPrintPart part) {
        return part.color == APPrintColor.bold;
    }

    public static boolean isUnderline(APPrintPart part) {
        return part.color == APPrintColor.underline;
    }

    public static Paint itemColor(int flags) {
        if (flags == 0) {
            return CYAN;
        }
        if ((flags & 0b001) != 0) { // advancement
            return PLUM;
        }
        if ((flags & 0b010) != 0) { // useful
            return SLATEBLUE;
        }
        if ((flags & 0b100) != 0) { // trap
            return SALMON;
        }
        return CYAN;
    }

    private static Paint hex(String value) {
        return Color.web("#" + value);
    }
}