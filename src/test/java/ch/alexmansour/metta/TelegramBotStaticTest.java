package ch.alexmansour.metta;

import ch.alexmansour.metta.entity.MettaUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelegramBotStaticTest {

    @Test
    void getFormatedDateJoined_shouldFormat_ddMMyyyy_HHmm() {
        LocalDateTime dt = LocalDateTime.of(2023, 1, 5, 9, 7, 0, 0);
        MettaUser user = new MettaUser(100L, "Test", null, dt, false);

        String formatted = TelegramBot.getFormatedDateJoined(user);

        assertEquals("05.01.2023 09:07", formatted);
    }

    @Test
    void getFormatedDateJoined_roundTripWithTwoDigitValues() {
        LocalDateTime dt = LocalDateTime.of(2024, 12, 25, 16, 30, 0, 0);
        MettaUser user = new MettaUser(101L, "Noel", "noel", dt, true);

        String formatted = TelegramBot.getFormatedDateJoined(user);

        assertEquals("25.12.2024 16:30", formatted);
    }

    @Test
    void getFormatedDateJoined_midnight_shouldShow_0000() {
        LocalDateTime dt = LocalDateTime.of(2025, 12, 10, 0, 0, 0, 0);
        MettaUser user = new MettaUser(102L, "Mila", null, dt, false);

        String formatted = TelegramBot.getFormatedDateJoined(user);

        assertEquals("10.12.2025 00:00", formatted);
    }
}
