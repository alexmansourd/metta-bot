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
public class TelegramBot extends TelegramLongPollingBot {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class);
    private static final long LIMIT_FOR_REMINDER_IN_HOURS = 12;
    private static final String STATUS_REQUESTED = "status";

    private final static String welcomeMessage = "Welcome {0} \uD83E\uDDDA\uD83C\uDFFB\u200D♀️\n" +
            "\n" +
            "As we are a community of people that values real connections we would love to learn three things from you upon joining: \n" +
            "1) WHO brought you here? \n" +
            "2) WHAT about our community resonates with you?\n" +
            "3) HOW are you planning on contributing?\n" +
            "\n" +
            "Don’t worry, we’re not looking for perfectly polished answers, but simply a little sign from you to get to know you. Please know that we’d love to read from you within 24 hours and otherwise will have to ask you to leave the group. \n" +
            "\n" +
            "With ❤️, \n" +
            "The Metta Explorers";

    private final static String reminderMessageUserPart = "Der User {0} ist am {1} in die Metta Explorers Gruppe eingeladen worden.";
    private final static String reminderMessageReminderPart = "Hello {0}," +
            "\n" +
            "Welcome to the Metta Community! I wanted to kindly ask you to share the introduction in the chat, the questions that were sent by the bot could be of inspiration for it. We’d really like to keep this a community where people know each other. \n" +
            "Normally we give the people one day time for it after entering the group, do you think you’d manage within the next day? \n" +
            "Wishing you a great week! Best, Luc";

    private final String botUsername;
    private final long userIdLuc;
    private final long userIdAlex;

    @Autowired
    private UserService userService;

    public TelegramBot(@Value("${telegram.bot.token}") String botToken,
                       @Value("${telegram.bot.username}") String botUsername,
                       @Value("${telegram.bot.userIdLuc}") long userIdLuc,
                       @Value("${telegram.bot.userIdAlex}") long userIdAlex) throws TelegramApiException {
        super(botToken);
        this.userIdLuc = userIdLuc;
        this.userIdAlex = userIdAlex;
        this.botUsername = botUsername;
        // Register and start the bot
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(this);
    }

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
                        chatID,
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

    private boolean statusUpdateRequested(Message msg, Long userId) {
        boolean statusRequested = STATUS_REQUESTED.equalsIgnoreCase(msg.getText());
        return (userIdLuc == userId || userIdAlex == userId) && statusRequested;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    // run every min
    @Scheduled(fixedRate = 1000 * 60)
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

    // run every day
    @Scheduled(fixedRate = 1000 * 60 * 60 * 24)
    public void houseKeeping() {
        LOGGER.info("Housekeeping started");
        for (MettaUser mettaUser : userService.fetchAll()) {
            if (mettaUser.hasBeenReminded() && mettaUser.getDateTimeJoined().isBefore(LocalDateTime.now().minusDays(2))) {
                userService.deleteUser(mettaUser.getUserId());
                LOGGER.info("Housekeeping for user: {}", getUserNameOrFirstName(mettaUser));
            }
        }
        LOGGER.info("Housekeeping finished");
    }

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
     * Is not used at the moment
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

    private String getUserNameOrFirstName(MettaUser user) {
        return user.getUserName() != null ? "@" + user.getUserName() : user.getFirstName();
    }

    private String composeWelcomeMessage(MettaUser mettaUser) {
        return MessageFormat.format(welcomeMessage, getUserNameOrFirstName(mettaUser));
    }

    private String composeReminderMessageUser(MettaUser mettaUser) {
        return MessageFormat.format(reminderMessageUserPart, getUserNameOrFirstName(mettaUser), getFormatedDateJoined(mettaUser));
    }

    private static String getFormatedDateJoined(MettaUser mettaUser) {
        return mettaUser.getDateTimeJoined().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    private String composeReminderMessageReminder(MettaUser mettaUser) {
        return MessageFormat.format(reminderMessageReminderPart, mettaUser.getFirstName());
    }

    // Utility to format a Telegram User's display name for logging/messages
    private String getUserNameOrFirstName(User user) {
        if (user == null) return "unknown";
        String username = user.getUserName();
        return username != null ? "@" + username : user.getFirstName();
    }
}