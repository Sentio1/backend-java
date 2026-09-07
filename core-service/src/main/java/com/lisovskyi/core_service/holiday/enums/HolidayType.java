package com.lisovskyi.core_service.holiday.enums;

// PUBLIC_HOLIDAY — офіційне державне свято (завжди неробочий).
//
// TRANSFERRED_WORKING_DAY — робоча субота (відпрацювання за перенесенням, тобто вихідний став робочим).
//
// TRANSFERRED_NON_WORKING_DAY — додатковий вихідний (наприклад, перенесення свята з неділі на понеділок).
public enum HolidayType {
    PUBLIC_HOLIDAY,
    TRANSFERRED_WORKING_DAY,
    TRANSFERRED_NON_WORKING_DAY
}
