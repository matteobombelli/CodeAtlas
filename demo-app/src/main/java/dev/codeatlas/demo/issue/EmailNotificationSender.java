package dev.codeatlas.demo.issue;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class EmailNotificationSender implements NotificationSender {

    @Override
    public void issueAssigned(IssueEntity issue) {
        // A real adapter would send email. The demo intentionally terminates here.
    }
}
