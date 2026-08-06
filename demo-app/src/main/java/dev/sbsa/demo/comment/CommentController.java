package dev.sbsa.demo.comment;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/issues/{issueId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public List<CommentEntity> list(@PathVariable Long issueId) {
        return commentService.findForIssue(issueId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentEntity add(
            @PathVariable Long issueId,
            @RequestBody CreateCommentRequest request) {
        return commentService.add(issueId, request.body());
    }

    public record CreateCommentRequest(String body) {
    }
}
