package ch.alexmansour.metta;

import ch.alexmansour.metta.entity.MettaUser;
import ch.alexmansour.metta.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.UnbanChatMember;
import org.telegram.telegrambots.meta.api.methods.reactions.SetMessageReaction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
/**
 * Telegram long-polling bot for the Metta community.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Welcomes new users that join the group and persists them for follow-up reminders.</li>
 *     <li>Removes users from the persistence store when they leave the group or after workflows complete.</li>
 *     <li>Allows admins to request a short status report by sending the {@code status} command in a private chat.</li>
 *     <li>Periodically reminds admins about users who haven't introduced themselves yet.</li>
 *     <li>Performs daily housekeeping to clear outdated reminder entries.</li>
 * </ul>
 * Configuration is injected via Spring {@link Value} properties:
 * <ul>
 *     <li>{@code telegram.bot.token} – Bot token.</li>
 *     <li>{@code telegram.bot.username} – Public username of the bot.</li>
 *     <li>{@code telegram.bot.userIdLuc} – Telegram user ID of an admin (Luc).</li>
 *     <li>{@code telegram.bot.userIdAlex} – Telegram user ID of an admin (Alex).</li>
 * </ul>
 * The bot registers itself with {@link TelegramBotsApi} on construction and starts receiving updates.
 */
public class TelegramBot extends TelegramLongPollingBot {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class);
    private static final long LIMIT_FOR_REMINDER_IN_HOURS = 12;
    private static final int DAYS_FOR_REMINDER_CUTOFF = 5;
    private static final String STATUS_REQUESTED_CMD = "status";
    private static final String WELCOME_MESSAGE = """
            Welcome {0} \uD83E\uDDDA\uD83C\uDFFB\u200D♀️
            
            As we are a community of people that values real connections we would love to learn three things from you upon joining: 
            1) WHO brought you here? 
            2) WHAT about our community resonates with you?
            3) HOW are you planning on contributing?
            
            Don’t worry, we’re not looking for perfectly polished answers, but simply a little sign from you to get to know you. Please know that we’d love to read from you within 24 hours and otherwise will have to ask you to leave the group. 
            
            With ❤️, 
            The Metta Explorers""";

    private static final String REMINDER_MESSAGE_USER_PART = "Der User {0} ist am {1} in die Metta Explorers Gruppe eingeladen worden.";
    private static final String REMINDER_MESSAGE_REMINDER_PART = """
            Hello {0},
            Welcome to the Metta Community! I wanted to kindly ask you to share the introduction in the chat, the questions that were sent by the bot could be of inspiration for it. We’d really like to keep this a community where people know each other. 
            Normally we give the people one day time for it after entering the group, do you think you’d manage within the next day? 
            Wishing you a great week! Best, Luc""";

    private final String botUsername;
    private final long userIdLuc;
    private final long userIdAlex;
    private final UserService userService;

    /**
     * Creates and registers the Telegram bot instance.
     * <p>
     * The constructor invokes {@link TelegramBotsApi#registerBot}
     * which starts the long-polling session immediately.
     *
     * @param botToken    the bot token obtained from BotFather (Spring property {@code telegram.bot.token})
     * @param botUsername the bot's public username (Spring property {@code telegram.bot.username})
     * @param userIdLuc   Telegram user ID for the admin Luc (Spring property {@code telegram.bot.userIdLuc})
     * @param userIdAlex  Telegram user ID for the admin Alex (Spring property {@code telegram.bot.userIdAlex})
     * @throws TelegramApiException if the bot fails to register with Telegram API
     */
    @Autowired
    public TelegramBot(@Value("${telegram.bot.token}") String botToken,
                       @Value("${telegram.bot.username}") String botUsername,
                       @Value("${telegram.bot.userIdLuc}") long userIdLuc,
                       @Value("${telegram.bot.userIdAlex}") long userIdAlex,
                       UserService userService) throws TelegramApiException {
        super(botToken);
        this.userService = userService;
        this.userIdLuc = userIdLuc;
        this.userIdAlex = userIdAlex;
        this.botUsername = botUsername;
        // Register and start the bot
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(this);
    }

    /**
     * Handles incoming updates from Telegram.
     * <p>
     * Behavior overview:
     * <ul>
     *     <li>If new chat members joined, they are persisted and greeted with a welcome message.</li>
     *     <li>If a user left the chat, they are removed from persistence.</li>
     *     <li>If the sender is an admin and sent the {@code status} command, a status summary is DM'd to them.</li>
     *     <li>Otherwise, if the sender exists in persistence, the message is considered an introduction:
     *     the bot reacts with a heart and removes the user from persistence.</li>
     * </ul>
     *
     * @param update the incoming update payload
     */
    @Override
    public void onUpdateReceived(Update update) {
        Message msg = update.getMessage();
        if (msg == null) {
            return;
        }
        List<User> newUserList = msg.getNewChatMembers();
        User leftUser = msg.getLeftChatMember();
        Long chatID = msg.getChat().getId();
        Long userId = msg.getFrom().getId();

        if (newUserList != null && !newUserList.isEmpty()) {
            for (User newUser : newUserList) {
                MettaUser mettaUser = new MettaUser(
                        newUser.getId(), // use the joined user's id
                        newUser.getFirstName(),
                        newUser.getUserName(),
                        LocalDateTime.now(),
                        false
                );
                userService.saveUser(mettaUser);
                LOGGER.info("New user joined group. username or firstname: {}", getUserNameOrFirstName(mettaUser));
                sendText(chatID, getUserNameOrFirstName(mettaUser), composeWelcomeMessage(mettaUser));
            }
        } else if (leftUser != null) {
            LOGGER.info("User left group. firstname: {}", leftUser.getFirstName());
            userService.deleteUser(leftUser.getId());
        } else {
            if (statusUpdateRequested(msg, userId)) {
                LOGGER.info("Status update requested by: {}", getUserNameOrFirstName(msg.getFrom()));
                sendText(userId, getUserNameOrFirstName(msg.getFrom()), composeStatusMessage());
            } else {
                userService.fetchUser(userId).ifPresent(mettaUser -> {
                    SetMessageReaction setMessageReaction = new SetMessageReaction();
                    setMessageReaction.setChatId(String.valueOf(chatID));
                    setMessageReaction.setMessageId(update.getMessage().getMessageId());

                    ReactionTypeEmoji reactionTypeEmoji = new ReactionTypeEmoji();
                    reactionTypeEmoji.setEmoji("❤");
                    setMessageReaction.setReactionTypes(List.of(reactionTypeEmoji));

                    try {
                        execute(setMessageReaction);
                    } catch (TelegramApiException e) {
                        LOGGER.error("Failed to set reaction", e);
                    }

                    LOGGER.info("User answered the questions. username or firstname: {}", getUserNameOrFirstName(mettaUser));
                    userService.deleteUser(mettaUser.getUserId());
                });
            }
        }
    }

    /**
     * Builds a human-readable status message listing users that have already been reminded.
     *
     * @return a status report or {@code "No open reminders"} if none exist
     */
    private String composeStatusMessage() {
        AtomicBoolean hasAtLeastOneUser = new AtomicBoolean(false);
        StringBuilder sb = new StringBuilder();
        userService.fetchAll().forEach(mettaUser -> {
            if (mettaUser.hasBeenReminded()) {
                hasAtLeastOneUser.set(true);
                sb.append("reminded on ")
                        .append(getFormatedDateJoined(mettaUser))
                        .append(" ")
                        .append(getUserNameOrFirstName(mettaUser));
                sb.append("\n");
            }
        });
        if (!hasAtLeastOneUser.get()) {
            sb.append("No open reminders");
        }
        return sb.toString();
    }

    /**
     * Determines whether a status update was requested by an allowed admin.
     *
     * @param msg    the received message (its text is checked for the {@code status} command)
     * @param userId the Telegram user ID of the sender
     * @return {@code true} if the message is the {@code status} command and the sender is an admin; otherwise {@code false}
     */
    boolean statusUpdateRequested(Message msg, Long userId) {
        boolean statusRequested = STATUS_REQUESTED_CMD.equalsIgnoreCase(msg.getText());
        return (userIdLuc == userId || userIdAlex == userId) && statusRequested;
    }

    /**
     * Returns the bot's public username.
     *
     * @return the configured bot username
     */
    @Override
    public String getBotUsername() {
        return botUsername;
    }

    /**
     * Periodic task that reminds admins about users who haven't introduced themselves yet.
     * <p>
     * Schedule: every 3 minutes.
     * A reminder is sent if a user has been in the group for longer than {@link #LIMIT_FOR_REMINDER_IN_HOURS}
     * hours and has not been reminded before. The user entry is marked as reminded afterwards.
     */
    @Scheduled(fixedRate = 1000 * 60 * 3)
    public void remindUser() {
        for (MettaUser mettaUser : userService.fetchAll()) {
            if (mettaUser.getDateTimeJoined().isBefore(LocalDateTime.now().minusHours(LIMIT_FOR_REMINDER_IN_HOURS)) && !mettaUser.hasBeenReminded()) {
                // Text an Luc
                sendText(userIdLuc, getUserNameOrFirstName(mettaUser), composeReminderMessageUser(mettaUser));
                sendText(userIdLuc, getUserNameOrFirstName(mettaUser), composeReminderMessageReminder(mettaUser));
                mettaUser.setHasBeenReminded(true);
                userService.saveUser(mettaUser);
                LOGGER.info("Sent reminder for user: {}", getUserNameOrFirstName(mettaUser));
            }
        }
    }

    /**
     * Daily housekeeping task that cleans up outdated reminder entries.
     * <p>
     * Schedule: once every 24 hours.
     * Removes users that were already reminded and joined earlier than
     * {@link #DAYS_FOR_REMINDER_CUTOFF} days ago.
     */
    @Scheduled(fixedRate = 1000 * 60 * 60 * 24)
    public void houseKeeping() {
        LOGGER.info("Housekeeping started");
        for (MettaUser mettaUser : userService.fetchAll()) {
            if (mettaUser.hasBeenReminded() && mettaUser.getDateTimeJoined().isBefore(LocalDateTime.now().minusDays(DAYS_FOR_REMINDER_CUTOFF))) {
                userService.deleteUser(mettaUser.getUserId());
                LOGGER.info("Reminder deleted for user: {}", getUserNameOrFirstName(mettaUser));
            }
        }
        LOGGER.info("Housekeeping finished");
    }

    /**
     * Sends a plain text message to a chat/user.
     *
     * @param who      the target chat ID
     * @param username the display name used for logging
     * @param what     the text content to send
     */
    private void sendText(Long who, String username, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString()) //Who are we sending a message to
                .text(what)
                .build();
        try {
            execute(sm);
            LOGGER.info("Sent text. to {}", username);
        } catch (TelegramApiException e) {
            LOGGER.error("Failed to send message to {}", username, e);
        }
    }

    /**
     * Bans and immediately unbans a user to effectively kick them from the group.
     * <p>
     * Deprecated and not used at the moment.
     *
     * @param chatId the chat/group ID
     * @param user   the user to remove
     */
    @Deprecated(forRemoval = true)
    private void banUser(Long chatId, MettaUser user) {
        LocalDate oneDayInTheFuture = LocalDate.now().plusDays(1);
        int epochMilliSecondsAtDate = Math.toIntExact(oneDayInTheFuture.toEpochDay());

        BanChatMember banChatMember = new BanChatMember();
        banChatMember.setChatId(chatId);
        banChatMember.setUserId(user.getUserId());
        banChatMember.setUntilDate(epochMilliSecondsAtDate);

        UnbanChatMember unbanChatMember = new UnbanChatMember();
        unbanChatMember.setChatId(String.valueOf(chatId));
        unbanChatMember.setUserId(user.getUserId());
        // This parameter ensures that if the user wasn't banned, no action is taken.
        unbanChatMember.setOnlyIfBanned(true);

        try {
            execute(banChatMember);
            execute(unbanChatMember);
            LOGGER.info("User banned. username: {}", getUserNameOrFirstName(user));
            sendText(chatId, "metta-group", user.getFirstName() + " has bin removed from the group :(");
        } catch (TelegramApiException e) {
            LOGGER.error("Failed to ban/unban user {}", getUserNameOrFirstName(user), e);
        }
    }

    /**
     * Formats a {@link MettaUser}'s display handle for messages/logging.
     *
     * @param user the stored user
     * @return the user's {@code @username} if present; otherwise their first name
     */
    String getUserNameOrFirstName(MettaUser user) {
        return user.getUserName() != null ? "@" + user.getUserName() : user.getFirstName();
    }

    /**
     * Composes the welcome message for a newly joined user.
     *
     * @param mettaUser the new user
     * @return the formatted welcome text
     */
    String composeWelcomeMessage(MettaUser mettaUser) {
        return MessageFormat.format(WELCOME_MESSAGE, getUserNameOrFirstName(mettaUser));
    }

    /**
     * Composes the admin-facing reminder line describing when a user joined.
     *
     * @param mettaUser the user being reminded about
     * @return the formatted line for admins
     */
    String composeReminderMessageUser(MettaUser mettaUser) {
        return MessageFormat.format(REMINDER_MESSAGE_USER_PART, getUserNameOrFirstName(mettaUser), getFormatedDateJoined(mettaUser));
    }

    /**
     * Formats the user's join timestamp for human-readable output.
     *
     * @param mettaUser the user
     * @return the join date formatted as {@code dd.MM.yyyy HH:mm}
     */
    static String getFormatedDateJoined(MettaUser mettaUser) {
        return mettaUser.getDateTimeJoined().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    /**
     * Composes the admin's suggested DM text to follow up with a user.
     *
     * @param mettaUser the user to contact
     * @return the formatted DM suggestion text for admins
     */
    String composeReminderMessageReminder(MettaUser mettaUser) {
        return MessageFormat.format(REMINDER_MESSAGE_REMINDER_PART, mettaUser.getFirstName());
    }

    /**
     * Formats a Telegram {@link User}'s display handle for messages/logging.
     *
     * @param user the Telegram user (may be {@code null})
     * @return the user's {@code @username} if present; otherwise their first name; or {@code "unknown"} if null
     */
    String getUserNameOrFirstName(User user) {
        if (user == null) return "unknown";
        String username = user.getUserName();
        return username != null ? "@" + username : user.getFirstName();
    }
}