package com.okoshko.dto;

import java.time.LocalDateTime;

public record ChangeGroupHomeworkRequest(String name, String description, LocalDateTime dueDate) {
}
