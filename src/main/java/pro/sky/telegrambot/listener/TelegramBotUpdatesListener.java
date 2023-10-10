package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.entity.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import javax.annotation.PostConstruct;
import javax.validation.Path;
import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    public static final Pattern PATTERN = Pattern.compile("([0-9\\.\\:\\s]{16})(\\s)([\\W+]+)");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    @Autowired
    private TelegramBot telegramBot;
    @Autowired
    private NotificationTaskRepository repository;

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {


            String message = update.message().text();
            Long chatId = update.message().chat().id();
            Matcher matcher = PATTERN.matcher(message);
            if ("/start".equalsIgnoreCase(message)) {
                telegramBot.execute(new SendMessage(chatId, "Добро пожаловать!"));
            } else if (matcher.matches()) {
                try {
                    String time = matcher.group(1);
                    String userMessage = matcher.group(3);
                    LocalDateTime execDate = LocalDateTime.parse(time, DATE_TIME_FORMATTER);

                    NotificationTask task = new NotificationTask();
                    task.setChatId(chatId);
                    task.setText(userMessage);
                    task.setExecDate(execDate);

                    repository.save(task);
                    telegramBot.execute(new SendMessage(chatId, "Событие сохранено \uD83D\uDC4C"));
                } catch (DateTimeParseException e) {
                    telegramBot.execute(new SendMessage(chatId, "Введён неверный формат даты.❗\uFE0F"));
                }
            }
        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    @Scheduled(fixedDelay = 60_000L)
    public void schedule() {
        List<NotificationTask> tasks = repository
                .findAllByExecDate(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        tasks.forEach(t -> {
            SendResponse response = telegramBot.execute(new SendMessage(t.getChatId(), t.getText()));
            if(response.isOk()){
                repository.delete(t);
            }
        });
    }
}
