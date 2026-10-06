package com.college.library.controller;

import com.college.library.service.DueDateReminderService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final ObjectProvider<DueDateReminderService> reminderServiceProvider;

    public NotificationController(ObjectProvider<DueDateReminderService> reminderServiceProvider) {
        this.reminderServiceProvider = reminderServiceProvider;
    }

    @PostMapping("/due-reminders")
    public ResponseEntity<Map<String, Object>> sendDueReminders() {
        DueDateReminderService reminderService = reminderServiceProvider.getIfAvailable();
        if (reminderService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Email reminders are disabled. Configure SMTP and enable library.reminders.enabled."));
        }
        int sent = reminderService.sendDueDateReminders();
        return ResponseEntity.ok(Map.of("sent", sent, "message", "Due-date reminders sent."));
    }
}
