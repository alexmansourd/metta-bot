package ch.alexmansour.metta;

import ch.alexmansour.metta.entity.MettaUser;
import ch.alexmansour.metta.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.reactions.SetMessageReaction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Behavior tests for TelegramBot covering onUpdateReceived, remindUser, and houseKeeping flows.
 * Uses a partial mock to avoid running the real constructor/registration.
 */
class TelegramBotBehaviorTest {

    private TelegramBot bot; // partial mock calling real methods
    private UserService userService;

    @BeforeEach
    void setUp() throws Exception {
        bot = mock(TelegramBot.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
        userService = mock(UserService.class);
        // Inject required fields via reflection (constructor is skipped in this partial mock)
        setField(bot, "userService", userService);
        setField(bot, "userIdLuc", 111L);
        setField(bot, "userIdAlex", 222L);
        setField(bot, "botUsername", "metta_bot");

        // Stub execute to do nothing but let us verify invocations
        // SendMessage
        doReturn(null).when(bot).execute(any(SendMessage.class));
        // SetMessageReaction
        doReturn(null).when(bot).execute(any(SetMessageReaction.class));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = TelegramBot.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Update updateWithNewMember(long chatId, User newUser) {
        Update update = new Update();
        Message msg = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(chat.getId()).thenReturn(chatId);
        when(msg.getChat()).thenReturn(chat);
        when(msg.getNewChatMembers()).thenReturn(List.of(newUser));
        // other minimal fields
        when(msg.getFrom()).thenReturn(mock(User.class));
        update.setMessage(msg);
        return update;
    }

    private static Update updateWithLeftUser(long chatId, User left) {
        Update update = new Update();
        Message msg = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(chat.getId()).thenReturn(chatId);
        when(msg.getChat()).thenReturn(chat);
        when(msg.getLeftChatMember()).thenReturn(left);
        when(msg.getFrom()).thenReturn(mock(User.class));
        update.setMessage(msg);
        return update;
    }

    private static Update statusRequestFrom(long userId, String text) {
        Update update = new Update();
        Message msg = mock(Message.class);
        User from = mock(User.class);
        when(from.getId()).thenReturn(userId);
        when(from.getUserName()).thenReturn("admin");
        when(from.getFirstName()).thenReturn("Admin");
        when(msg.getFrom()).thenReturn(from);
        when(msg.getText()).thenReturn(text);
        Chat chat = mock(Chat.class);
        when(chat.getId()).thenReturn(9999L);
        when(msg.getChat()).thenReturn(chat);
        update.setMessage(msg);
        return update;
    }

    private static Update updateWithMessageFrom(long chatId, int messageId, long fromUserId) {
        Update update = new Update();
        Message msg = mock(Message.class);
        when(msg.getMessageId()).thenReturn(messageId);
        Chat chat = mock(Chat.class);
        when(chat.getId()).thenReturn(chatId);
        when(msg.getChat()).thenReturn(chat);
        User from = mock(User.class);
        when(from.getId()).thenReturn(fromUserId);
        when(from.getUserName()).thenReturn("u" + fromUserId);
        when(from.getFirstName()).thenReturn("U" + fromUserId);
        when(msg.getFrom()).thenReturn(from);
        update.setMessage(msg);
        return update;
    }

    @Test
    void onUpdateReceived_newMember_isSavedAndWelcomed() throws Exception {
        // given
        User newUser = mock(User.class);
        when(newUser.getId()).thenReturn(777L);
        when(newUser.getFirstName()).thenReturn("Lara");
        when(newUser.getUserName()).thenReturn("lara");
        Update update = updateWithNewMember(12345L, newUser);

        // when
        bot.onUpdateReceived(update);

        // then
        verify(userService, times(1)).saveUser(any(MettaUser.class));
        // capture the text that was sent to ensure welcome flow happened
        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot, atLeastOnce()).execute(msgCap.capture());
        String text = msgCap.getAllValues().stream().map(SendMessage::getText).findFirst().orElse("");
        assertTrue(text.contains("Welcome"));
        assertTrue(text.contains("@lara"));
    }

    @Test
    void onUpdateReceived_leftUser_isDeleted() {
        // given
        User left = mock(User.class);
        when(left.getId()).thenReturn(888L);
        when(left.getFirstName()).thenReturn("Lefty");
        Update update = updateWithLeftUser(54321L, left);

        // when
        bot.onUpdateReceived(update);

        // then
        verify(userService).deleteUser(888L);
    }

    @Test
    void onUpdateReceived_adminStatusRequest_sendsComposedStatus() throws Exception {
        // given reminded and non-reminded users
        MettaUser reminded = new MettaUser(10L, "Remi", "remi", LocalDateTime.of(2024, 1, 2, 3, 4), true);
        MettaUser notReminded = new MettaUser(11L, "Nora", null, LocalDateTime.now(), false);
        when(userService.fetchAll()).thenReturn(List.of(reminded, notReminded));

        Update update = statusRequestFrom(111L, "status"); // 111L = Luc admin set in setUp()

        // when
        bot.onUpdateReceived(update);

        // then
        ArgumentCaptor<SendMessage> cap = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(cap.capture());
        String text = cap.getValue().getText();
        assertTrue(text.contains("reminded on "));
        assertTrue(text.contains("@remi"));
        assertFalse(text.contains("No open reminders"));
    }

    @Test
    void onUpdateReceived_existingUserMessage_reactsAndDeletes() throws Exception {
        // given a user that exists in store
        long userId = 333L;
        MettaUser mu = new MettaUser(userId, "Ivy", "ivy", LocalDateTime.now(), false);
        when(userService.fetchUser(userId)).thenReturn(Optional.of(mu));

        Update update = updateWithMessageFrom(1111L, 42, userId);

        // when
        bot.onUpdateReceived(update);

        // then
        // Reaction should be sent
        verify(bot).execute(any(SetMessageReaction.class));
        // User should be removed from store
        verify(userService).deleteUser(userId);
    }

    @Test
    void remindUser_sendsRemindersAndMarksReminded() throws Exception {
        MettaUser needsReminder = new MettaUser(1L, "Ava", null, LocalDateTime.now().minusHours(13), false);
        MettaUser recent = new MettaUser(2L, "Ben", null, LocalDateTime.now(), false);
        when(userService.fetchAll()).thenReturn(List.of(needsReminder, recent));

        bot.remindUser();

        // Two messages to Luc for one user (user text + reminder template)
        verify(bot, times(2)).execute(any(SendMessage.class));
        assertTrue(needsReminder.hasBeenReminded());
        verify(userService).saveUser(needsReminder);
        // No action for recent user
        verify(userService, never()).saveUser(recent);
    }

    @Test
    void houseKeeping_deletesOldRemindedUsers() {
        MettaUser oldReminded = new MettaUser(3L, "Cara", null, LocalDateTime.now().minusDays(6), true);
        MettaUser newReminded = new MettaUser(4L, "Drew", null, LocalDateTime.now().minusDays(2), true);
        MettaUser notReminded = new MettaUser(5L, "Eli", null, LocalDateTime.now().minusDays(10), false);
        when(userService.fetchAll()).thenReturn(List.of(oldReminded, newReminded, notReminded));

        bot.houseKeeping();

        verify(userService).deleteUser(3L);
        verify(userService, never()).deleteUser(4L);
        verify(userService, never()).deleteUser(5L);
    }
}
