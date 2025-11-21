package ch.alexmansour.metta;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.telegram.telegrambots.meta.api.objects.Message;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramBotStatusUpdateRequestedTest {

    private TelegramBot botMock() {
        // Avoid running the real constructor; call real methods for logic
        return mock(TelegramBot.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
    }

    private Message msgWithText(String text) {
        Message m = mock(Message.class);
        when(m.getText()).thenReturn(text);
        return m;
    }

    @Test
    void returnsTrue_whenAdminRequestsStatus_caseInsensitive() {
        TelegramBot bot = botMock();
        // In a partial mock, final long fields default to 0; pass 0L to simulate an allowed admin ID.
        Long adminId = 0L;

        assertTrue(bot.statusUpdateRequested(msgWithText("status"), adminId));
        assertTrue(bot.statusUpdateRequested(msgWithText("STATUS"), adminId));
    }

    @Test
    void returnsFalse_whenNonAdminRequestsStatus() {
        TelegramBot bot = botMock();
        Long nonAdminId = 123L; // not matching default admin fields

        assertFalse(bot.statusUpdateRequested(msgWithText("status"), nonAdminId));
    }

    @Test
    void returnsFalse_whenAdminSendsDifferentMessage() {
        TelegramBot bot = botMock();
        Long adminId = 0L; // matches default admin fields in the mock

        assertFalse(bot.statusUpdateRequested(msgWithText("hello"), adminId));
    }

    @Test
    void returnsFalse_whenMessageTextIsNull() {
        TelegramBot bot = botMock();
        Long adminId = 0L;

        assertFalse(bot.statusUpdateRequested(msgWithText(null), adminId));
    }
}
