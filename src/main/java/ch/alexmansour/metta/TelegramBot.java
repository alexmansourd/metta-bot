package ch.alexmansour.metta;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

public class TelegramBot extends TelegramLongPollingBot {

    private final static String BOT_NAME = "Metta-Bot";

    public TelegramBot() {
        super(ApiToken.API_TOKEN);
    }

    @Override
    public void onUpdateReceived(Update update) {

    }

    @Override
    public String getBotUsername() {
        return BOT_NAME;
    }
}
