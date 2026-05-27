package com.ahn.settlement.converter;

import org.springframework.core.convert.converter.Converter;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public class YearMonthConverter implements Converter<String, YearMonth> {

    @Override
    public YearMonth convert(String source) {
        try {
            return YearMonth.parse(source);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("yearMonth 형식이 올바르지 않습니다. 예: 2025-03");
        }
    }
}
