package ch.alexmansour.metta;

import ch.alexmansour.metta.entity.MettaUser;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TelegramBotHelpersTest {

    private TelegramBot botMock() {
        // Avoid real constructor; call real methods for helpers only
        return mock(TelegramBot.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
    }

    @Test
    void getUserNameOrFirstName_mettaUser_withUsernameUsesHandle() {
        TelegramBot bot = botMock();
        MettaUser mu = new MettaUser(1L, "Alice", "alice", LocalDateTime.now(), false);

        String handle = bot.getUserNameOrFirstName(mu);

        assertEquals("@alice", handle);
    }

    @Test
    void getUserNameOrFirstName_mettaUser_withoutUsernameUsesFirstName() {
        TelegramBot bot = botMock();
        MettaUser mu = new MettaUser(2L, "Bob", null, LocalDateTime.now(), null);

        String handle = bot.getUserNameOrFirstName(mu);

        assertEquals("Bob", handle);
    }

    @Test
    void composeWelcomeMessage_includesDisplayHandle() {
        TelegramBot bot = botMock();
        MettaUser mu = new MettaUser(3L, "Cara", "cara", LocalDateTime.now(), false);

        String msg = bot.composeWelcomeMessage(mu);

        assertTrue(msg.contains("@cara"));
    }

    @Test
    void composeReminderMessageUser_containsHandleAndFormattedDate() {
        TelegramBot bot = botMock();
        LocalDateTime joined = LocalDateTime.of(2024, 1, 2, 3, 4, 0, 0);
        MettaUser mu = new MettaUser(4L, "Dan", null, joined, null);

        String msg = bot.composeReminderMessageUser(mu);

        assertTrue(msg.contains("Dan")); // No username present; first name used
        assertTrue(msg.contains("02.01.2024 03:04"));
    }

    @Test
    void composeReminderMessageReminder_usesFirstName() {
        TelegramBot bot = botMock();
        MettaUser mu = new MettaUser(5L, "Eve", "eve", LocalDateTime.now(), true);

        String msg = bot.composeReminderMessageReminder(mu);

        assertTrue(msg.contains("Eve"));
    }

    @Test
    void getUserNameOrFirstName_telegramUser_nullIsUnknown() {
        TelegramBot bot = botMock();

        String handle = bot.getUserNameOrFirstName((User) null);

        assertEquals("unknown", handle);
    }

    @Test
    void getUserNameOrFirstName_telegramUser_usernamePreferredOverFirstName() {
        TelegramBot bot = botMock();
        User tgUser = mock(User.class);
        when(tgUser.getUserName()).thenReturn("john");
        when(tgUser.getFirstName()).thenReturn("John");

        String handle = bot.getUserNameOrFirstName(tgUser);

        assertEquals("@john", handle);
    }

    @Test
    void getUserNameOrFirstName_telegramUser_fallsBackToFirstName() {
        TelegramBot bot = botMock();
        User tgUser = mock(User.class);
        when(tgUser.getUserName()).thenReturn(null);
        when(tgUser.getFirstName()).thenReturn("Lina");

        String handle = bot.getUserNameOrFirstName(tgUser);

        assertEquals("Lina", handle);
    }
}
