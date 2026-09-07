package com.lisovskyi.core_service.deadline_processor;

import org.jspecify.annotations.NonNull;

import java.time.LocalDate;

// Проміжок, на який зупиняється перебіг строку (напр. зупинення провадження у справі) —
// обидва кінці включно: from — день зупинення, to — останній день, коли строк ще не тече;
// з дня, наступного за to, перебіг вважається поновленим. Немає власної сутності/таблиці —
// SEN-27 дає лише розрахунок, збереження причини й дат зупинення/поновлення — окрема задача.
public record SuspensionPeriod(LocalDate from, LocalDate to) {

    public SuspensionPeriod {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to (%s) must not be before from (%s)".formatted(to, from));
        }
    }

    boolean contains(@NonNull LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
