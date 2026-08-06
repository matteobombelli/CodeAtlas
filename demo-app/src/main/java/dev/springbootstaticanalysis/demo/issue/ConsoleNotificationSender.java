package dev.springbootstaticanalysis.demo.issue;

import org.springframework.stereotype.Component;

@Component
public class ConsoleNotificationSender implements NotificationSender {

    @Override
    public void issueAssigned(IssueEntity issue) {
        System.out.println("Assigned issue " + issue.getId());
    }
}
