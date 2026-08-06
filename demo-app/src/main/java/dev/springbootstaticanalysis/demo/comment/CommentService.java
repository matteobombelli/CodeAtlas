package dev.springbootstaticanalysis.demo.comment;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CommentService {

    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    public CommentEntity add(Long issueId, String body) {
        return commentRepository.save(new CommentEntity(issueId, body));
    }

    public List<CommentEntity> findForIssue(Long issueId) {
        return commentRepository.findByIssueId(issueId);
    }
}
