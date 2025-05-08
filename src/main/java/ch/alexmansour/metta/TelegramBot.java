package ch.alexmansour.metta;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class TelegramBot extends TelegramLongPollingBot {

    private final static String BOT_NAME = "Metta-Bot";

    public TelegramBot() throws IOException {
        super(getToken());
    }

    private static String getToken() throws IOException {
        String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        String appConfigPath = rootPath + "app.properties";

        Properties appProps = new Properties();
        appProps.load(new FileInputStream(appConfigPath));
        return appProps.getProperty("api.bot.token");
    }

    @Override
    public void onUpdateReceived(Update update) {

    }

    @Override
    public String getBotUsername() {
        return BOT_NAME;
    }
}
