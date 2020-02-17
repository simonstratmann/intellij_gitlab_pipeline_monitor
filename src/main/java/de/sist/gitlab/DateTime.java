package de.sist.gitlab;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class DateTime {

    private static final DateTimeFormatter FORMATTER_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FORMATTER_TIME = DateTimeFormatter.ofPattern("'Today' HH:mm");

    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime.isBefore(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS))) {
            return FORMATTER_DATE.format(dateTime);
        } else {
            return FORMATTER_TIME.format(dateTime);
        }
    }

}
