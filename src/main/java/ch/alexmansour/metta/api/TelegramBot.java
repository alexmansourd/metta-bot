package ch.alexmansour.metta.api;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public class TelegramBot extends TelegramLongPollingBot {

    private final static String BOT_NAME = "Metta-Bot";

    public TelegramBot() throws IOException {
        super(getToken());
    }

    private static String getToken() throws IOException {
        InputStream inputStream = TelegramBot.class.getResourceAsStream("/app.properties");
        Properties props = new Properties();
        props.load(inputStream);
        return props.getProperty("api.bot.token");
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message msg = update.getMessage();
        sendText(msg.getFrom().getId(), msg.getText());
    }

    @Override
    public String getBotUsername() {
        return BOT_NAME;
    }

    public void sendText(Long who, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString()) //Who are we sending a message to
                .text(what).build();    //Message content
        try {
            execute(sm);                        //Actually sending the message
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);      //Any error will be printed here
        }
    }
}
