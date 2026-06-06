package com.nutritrust.dto;

public record PositiveSignal(
        String name,
        String level,
        String value,
        String explanation
) {
}
