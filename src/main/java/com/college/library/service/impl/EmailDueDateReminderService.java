package com.college.library.service.impl;

import com.college.library.entity.Transaction;
import com.college.library.repository.TransactionRepository;
import com.college.library.service.DueDateReminderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@ConditionalOnProperty(name = "library.reminders.enabled", havingValue = "true")
@Transactional
public class EmailDueDateReminderService implements DueDateReminderService {

    private final JavaMailSender mailSender;
    private final TransactionRepository transactionRepository;
    private final String fromAddress;
    private final int reminderDays;

    public EmailDueDateReminderService(JavaMailSender mailSender,
                                      TransactionRepository transactionRepository,
                                      @Value("${library.reminders.from}") String fromAddress,
                                      @Value("${library.reminders.days-before:3}") int reminderDays) {
        this.mailSender = mailSender;
        this.transactionRepository = transactionRepository;
        this.fromAddress = fromAddress;
        this.reminderDays = reminderDays;
    }

    @Override
    public int sendDueDateReminders() {
        LocalDate today = LocalDate.now();
        List<Transaction> dueSoon = transactionRepository.findByStatusAndDueDateBetweenOrderByDueDateAsc(
                "ISSUED", today, today.plusDays(reminderDays));
        int sent = 0;
        for (Transaction transaction : dueSoon) {
            if (transaction.getLastReminderSentAt() != null
                    && transaction.getLastReminderSentAt().toLocalDate().equals(today)) {
                continue;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(transaction.getMember().getEmail());
            message.setSubject("Library book due reminder");
            message.setText("Hello " + transaction.getMember().getName() + ",\n\n"
                    + "The book \"" + transaction.getBook().getTitle() + "\" is due on "
                    + transaction.getDueDate() + ". Please return or renew it by the due date.\n\n"
                    + "Library Management System");
            mailSender.send(message);
            transaction.setLastReminderSentAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            sent++;
        }
        return sent;
    }

    @Scheduled(cron = "${library.reminders.cron:0 0 8 * * *}")
    public void sendScheduledReminders() {
        sendDueDateReminders();
    }
}
