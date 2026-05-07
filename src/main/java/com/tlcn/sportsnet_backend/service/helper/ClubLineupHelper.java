package com.tlcn.sportsnet_backend.service.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tlcn.sportsnet_backend.dto.tournament.TeamMatchFormatDTO;
import com.tlcn.sportsnet_backend.enums.ClubLineTypeEnum;
import com.tlcn.sportsnet_backend.error.InvalidDataException;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper xử lý conventions cho position trong roster + parse teamMatchFormat.
 *
 * <p>Position format:</p>
 * <ul>
 *   <li>Singles: <code>SINGLES_{idx}</code> — vd <code>SINGLES_1</code></li>
 *   <li>Doubles: <code>{TYPE}_{idx}_P{slot}</code> — vd <code>MEN_DOUBLES_1_P1</code></li>
 * </ul>
 */
public final class ClubLineupHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ClubLineupHelper() {}

    /**
     * Parse JSON từ Tournament.teamMatchFormat. Throw nếu invalid.
     * <p>Hỗ trợ cả camelCase và snake_case (vd {@code menDoubles} / {@code men_doubles}) để tránh lệch
     * payload FE/seed DB.</p>
     * <p>Default về 1 SINGLES nếu null/empty hoặc tất cả số ván = 0 (chưa cấu hình).</p>
     */
    public static TeamMatchFormatDTO parseFormat(String json) {
        if (json == null || json.isBlank()) {
            return defaultSinglesFormat();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isObject()) {
                return defaultSinglesFormat();
            }

            int singles = readIntField(root, "singles", "singles_count");
            int menDoubles = readIntField(root, "menDoubles", "men_doubles");
            int womenDoubles = readIntField(root, "womenDoubles", "women_doubles");
            int mixedDoubles = readIntField(root, "mixedDoubles", "mixed_doubles");

            TeamMatchFormatDTO built = TeamMatchFormatDTO.builder()
                    .singles(Math.max(0, singles))
                    .menDoubles(Math.max(0, menDoubles))
                    .womenDoubles(Math.max(0, womenDoubles))
                    .mixedDoubles(Math.max(0, mixedDoubles))
                    .build();

            if (built.getTotalMatches() == 0) {
                return defaultSinglesFormat();
            }
            return built;
        } catch (Exception e) {
            throw new InvalidDataException("teamMatchFormat không hợp lệ: " + e.getMessage());
        }
    }

    /**
     * Lấy số nguyên >= 0 từ object JSON; thử lần lượt các tên key (i18n / typo / snake_case).
     */
    private static int readIntField(JsonNode root, String... keys) {
        for (String key : keys) {
            if (!root.has(key)) continue;
            JsonNode n = root.get(key);
            if (n == null || n.isNull()) continue;
            if (n.isNumber()) return n.intValue();
            if (n.isTextual()) {
                try {
                    String t = n.asText().trim();
                    if (t.isEmpty()) continue;
                    return Integer.parseInt(t);
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return 0;
    }

    /** Giống tournament chưa cấu format: ít nhất 1 ván đơn để có slot lineup. */
    private static TeamMatchFormatDTO defaultSinglesFormat() {
        return TeamMatchFormatDTO.builder()
                .singles(1)
                .menDoubles(0)
                .womenDoubles(0)
                .mixedDoubles(0)
                .build();
    }

    /** Sinh danh sách tất cả position theo format. */
    public static List<PositionSpec> buildPositions(TeamMatchFormatDTO fmt) {
        List<PositionSpec> result = new ArrayList<>();
        int singles = fmt.getSingles() == null ? 0 : fmt.getSingles();
        int menDoubles = fmt.getMenDoubles() == null ? 0 : fmt.getMenDoubles();
        int womenDoubles = fmt.getWomenDoubles() == null ? 0 : fmt.getWomenDoubles();
        int mixedDoubles = fmt.getMixedDoubles() == null ? 0 : fmt.getMixedDoubles();

        for (int i = 1; i <= singles; i++) {
            result.add(new PositionSpec(formatPosition(ClubLineTypeEnum.SINGLES, i, null),
                    ClubLineTypeEnum.SINGLES, i, null));
        }
        for (ClubLineTypeEnum type : new ClubLineTypeEnum[]{
                ClubLineTypeEnum.MEN_DOUBLES,
                ClubLineTypeEnum.WOMEN_DOUBLES,
                ClubLineTypeEnum.MIXED_DOUBLES
        }) {
            int count = switch (type) {
                case MEN_DOUBLES -> menDoubles;
                case WOMEN_DOUBLES -> womenDoubles;
                case MIXED_DOUBLES -> mixedDoubles;
                default -> 0;
            };
            for (int i = 1; i <= count; i++) {
                result.add(new PositionSpec(formatPosition(type, i, 1), type, i, 1));
                result.add(new PositionSpec(formatPosition(type, i, 2), type, i, 2));
            }
        }
        return result;
    }

    public static String formatPosition(ClubLineTypeEnum type, int lineIndex, Integer playerSlot) {
        if (type == ClubLineTypeEnum.SINGLES) {
            return "SINGLES_" + lineIndex;
        }
        return type.name() + "_" + lineIndex + "_P" + playerSlot;
    }

    /** Số rubber tổng. */
    public static int totalRubbers(TeamMatchFormatDTO fmt) {
        return (fmt.getSingles() == null ? 0 : fmt.getSingles())
                + (fmt.getMenDoubles() == null ? 0 : fmt.getMenDoubles())
                + (fmt.getWomenDoubles() == null ? 0 : fmt.getWomenDoubles())
                + (fmt.getMixedDoubles() == null ? 0 : fmt.getMixedDoubles());
    }

    public record PositionSpec(
            String position,
            ClubLineTypeEnum lineType,
            int lineIndex,
            Integer playerSlot
    ) {}
}
