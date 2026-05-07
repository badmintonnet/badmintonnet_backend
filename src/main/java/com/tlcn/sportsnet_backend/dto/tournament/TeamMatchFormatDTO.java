package com.tlcn.sportsnet_backend.dto.tournament;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * DTO định nghĩa format thi đấu team match cho CLB tournament
 * Ví dụ: CLB A vs CLB B = 3 ván đơn nam + 2 ván đôi nam + 1 ván đôi hỗn hợp
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TeamMatchFormatDTO {
    
    Integer singles; // Số ván đơn (singles) trong một team match
    
    Integer menDoubles; // Số ván đôi nam trong một team match
    
    Integer womenDoubles; // Số ván đôi nữ trong một team match
    
    Integer mixedDoubles; // Số ván đôi hỗn hợp trong một team match
    
    /**
     * Tính tổng số ván trong một team match
     */
    public int getTotalMatches() {
        return (singles != null ? singles : 0) 
             + (menDoubles != null ? menDoubles : 0)
             + (womenDoubles != null ? womenDoubles : 0)
             + (mixedDoubles != null ? mixedDoubles : 0);
    }
}
