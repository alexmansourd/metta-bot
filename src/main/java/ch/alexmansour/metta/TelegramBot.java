package ch.alexmansour.metta;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final String botUsername;
    private static final long LIMIT_FOR_REMINDER_IN_HOURS = 24;
    private static final long LIMIT_FOR_BAN_IN_HOURS = 48;

    private final static Map<Pair<User, Long>, LocalDateTime> newUserJoinedTimeMap = new HashMap<>();
    private final static Set<Pair<User, Long>> newUserRemindedSet = new HashSet<>();

    private final static String welcomeMessage = "Welcome @{0} \uD83E\uDDDA\uD83C\uDFFB\u200D♀\uFE0F\n" +
            "\n" +
            "As we are a community of people that values real connections we would love to learn three things from you upon joining: \n" +
            "1) WHO brought you here? \n" +
            "2) WHAT about our community resonates with you?\n" +
            "3) HOW are you planning on contributing?\n" +
            "\n" +
            "Don’t worry, we’re not looking for perfectly polished answers, but simply a little sign from you to get to know you. Please know that we’d love to read from you within 48 hours and otherwise will have to ask you to leave the group. \n" +
            "\n" +
            "With ❤\uFE0F, \n" +
            "The Metta Explorers";

    private final static String reminderMessage = "Hi {0}," +
            "\n" +
            "You did not answer the questions so far. \n" +
            "Please answer them in the group chat. \n" +
            "You have 24 hours left. After that, you will be removed from the group. \n" +
            "\n" +
            "With ❤\uFE0F, \n" +
            "The Metta Explorers";

    public TelegramBot(@Value("${telegram.bot.token}") String botToken,
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
        Long chatID = msg.getChat().getId();
        if (!newUserList.isEmpty()) {
            for (User newUser : newUserList) {
                newUserJoinedTimeMap.put(Pair.of(newUser, chatID), LocalDateTime.now());
                sendText(chatID, composeWelcomeMessage(newUser.getUserName()));
            }
        } else {
            newUserJoinedTimeMap.keySet().removeIf(userLongPair -> userLongPair.getLeft().getId().equals(msg.getFrom().getId()));
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    // Every 7 min
    @Scheduled(fixedRate = 1000 * 60 * 7)
    public void removeUserIfNotAnswered() {
        Set<Pair<User, Long>> bannedUserChatPair = new HashSet<>();
        newUserJoinedTimeMap.forEach((userChatPair, userJoinedTime) -> {
            User user = userChatPair.getLeft();
            if (userJoinedTime.isBefore(LocalDateTime.now().minusHours(LIMIT_FOR_BAN_IN_HOURS))) {
                banUser(userChatPair.getRight(), user);
                bannedUserChatPair.add(userChatPair);
            } else if (userJoinedTime.isBefore(LocalDateTime.now().minusHours(LIMIT_FOR_REMINDER_IN_HOURS)) && !newUserRemindedSet.contains(userChatPair)) {
                sendText(user.getId(), composeReminderMessage(user.getFirstName()));
                newUserRemindedSet.add(userChatPair);
            }
        });
        newUserJoinedTimeMap.keySet().removeIf(bannedUserChatPair::contains);
        bannedUserChatPair.removeIf(userLongPair -> !newUserJoinedTimeMap.containsKey(userLongPair));
    }

    private void sendText(Long who, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString()) //Who are we sending a message to
                .text(what).build();    //Message content
        try {
            execute(sm);                        //Actually sending the message
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);      //Any error will be printed here
        }
    }

    private void banUser(Long chatId, User user) {
        BanChatMember banChatMember = new BanChatMember();
        banChatMember.setChatId(chatId);
        banChatMember.setUserId(user.getId());

        try {
            execute(banChatMember);
            sendText(chatId, "@" + user.getUserName() + " has bin removed from the group :(");
            sendText(user.getId(), "Sadly you have been removed since you did not answer the questions. You can rejoin anytime :)");
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private String composeWelcomeMessage(String username) {
        return MessageFormat.format(welcomeMessage, username);
    }

    private String composeReminderMessage(String firstName) {
        return MessageFormat.format(reminderMessage, firstName);
    }
}