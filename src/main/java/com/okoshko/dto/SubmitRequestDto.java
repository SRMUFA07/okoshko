package com.okoshko.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SubmitRequestDto(List<Long> groupIds, LocalDateTime desiredDateTime) {
}
