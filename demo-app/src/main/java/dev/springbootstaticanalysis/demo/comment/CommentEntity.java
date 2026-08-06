package dev.springbootstaticanalysis.demo.comment;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class CommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long issueId;
    private String body;

    protected CommentEntity() {
    }

    public CommentEntity(Long issueId, String body) {
        this.issueId = issueId;
        this.body = body;
    }

    public Long getId() {
        return id;
    }

    public Long getIssueId() {
        return issueId;
    }

    public String getBody() {
        return body;
    }
}
