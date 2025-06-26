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

@Component
public class TelegramBot extends TelegramLongPollingBot {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class);
    private static final long LIMIT_FOR_REMINDER_IN_HOURS = 12;
    private static final long USER_ID_OF_LUC = 982237762;

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
            "Welcome to the Metta Community! I wanted to kindly ask you to share the introduction in the chat, the questions that were sent by the bot could be of inspiration for it. We'd really like to keep this a community where people know each other. \n" +
            "Normally we give the people one day time for it after entering the group, do you think you'd manage within the next day? \n" +
            "Wishing you a great week! Best, Luc";

    private final String botUsername;

    @Autowired
    private UserService userService;

    private TelegramBot(@Value("${telegram.bot.token}") String botToken,
                        @Value("${telegram.bot.username}") String botUsername) throws TelegramApiException {
        super(botToken);
        this.botUsername = botUsername;
        // Register and start the bot
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(this);
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message msg = update.getMessage();
        List<User> newUserList = msg.getNewChatMembers();
        User leftUser = msg.getLeftChatMember();
        Long chatID = msg.getChat().getId();
        Long userId = msg.getFrom().getId();
        if (!newUserList.isEmpty()) {
            for (User newUser : newUserList) {
                MettaUser mettaUser = new MettaUser(userId, chatID, newUser.getFirstName(), newUser.getUserName(), LocalDateTime.now(), false);
                userService.saveUser(mettaUser);
                LOGGER.info("New user joined group. username or firstname: {}", getUserNameOrFirstName(mettaUser));
                sendText(chatID, getUserNameOrFirstName(mettaUser), composeWelcomeMessage(mettaUser));
            }
        } else if (leftUser != null) {
            userService.deleteUser(leftUser.getId());
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
                    LOGGER.error(e.getMessage());
                }

                LOGGER.info("User answered the questions. username or firstname: {}", getUserNameOrFirstName(mettaUser));
                userService.deleteUser(mettaUser.getUserId());
            });
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    // run every min
    @Scheduled(fixedRate = 1000 * 60)
    public void removeUserIfNotAnswered() {
        for (MettaUser mettaUser : userService.fetchAll()) {
            if (mettaUser.getDateTimeJoined().isBefore(LocalDateTime.now().minusHours(LIMIT_FOR_REMINDER_IN_HOURS)) && !mettaUser.hasBeenReminded()) {
                // Text an Luc
                sendText(USER_ID_OF_LUC, getUserNameOrFirstName(mettaUser), composeReminderMessageUser(mettaUser));
                sendText(USER_ID_OF_LUC, getUserNameOrFirstName(mettaUser), composeReminderMessageReminder(mettaUser));
                mettaUser.setHasBeenReminded(true);
                userService.saveUser(mettaUser);
            }
        }
    }

    private void sendText(Long who, String username, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString()) //Who are we sending a message to
                .text(what)
                .build();
        try {
            execute(sm);
            LOGGER.info("Sent text. {} to {}", what, username);
        } catch (TelegramApiException e) {
            LOGGER.error(e.getMessage());
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
            LOGGER.error(e.getMessage());
        }
    }

    private String getUserNameOrFirstName(MettaUser user) {
        return user.getUserName() != null ? "@" + user.getUserName() : user.getFirstName();
    }

    private String composeWelcomeMessage(MettaUser mettaUser) {
        return MessageFormat.format(welcomeMessage, getUserNameOrFirstName(mettaUser));
    }

    private String composeReminderMessageUser(MettaUser mettaUser) {
        return MessageFormat.format(reminderMessageUserPart, getUserNameOrFirstName(mettaUser), mettaUser.getDateTimeJoined().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
    }

    private String composeReminderMessageReminder(MettaUser mettaUser) {
        return MessageFormat.format(reminderMessageReminderPart, mettaUser.getFirstName());
    }
}