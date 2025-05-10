package ch.alexmansour.metta;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class TelegramBot extends TelegramLongPollingBot {

    private final static String BOT_NAME = "Metta-Bot";

    public TelegramBot() {
        super(ApiToken.API_TOKEN);
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
