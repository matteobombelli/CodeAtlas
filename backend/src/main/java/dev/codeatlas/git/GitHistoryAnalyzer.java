package dev.codeatlas.git;

import dev.codeatlas.indexing.DiscoveredSourceFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.springframework.stereotype.Component;

@Component
public class GitHistoryAnalyzer {

    public List<GitFileStat> analyze(Path repositoryRoot, List<DiscoveredSourceFile> files) {
        Map<String, MutableStat> stats = new HashMap<>();
        Map<String, java.util.UUID> fileIds = new HashMap<>();
        files.forEach(file -> fileIds.put(file.relativePath(), file.id()));
        Instant recentThreshold = Instant.now().minus(90, ChronoUnit.DAYS);

        try (Git git = Git.open(repositoryRoot.toFile());
             DiffFormatter formatter = new DiffFormatter(new ByteArrayOutputStream())) {
            var repository = git.getRepository();
            formatter.setRepository(repository);
            for (RevCommit commit : git.log().call()) {
                AbstractTreeIterator oldTree = commit.getParentCount() == 0
                        ? new EmptyTreeIterator()
                        : tree(repository, commit.getParent(0).getTree().getId());
                AbstractTreeIterator newTree = tree(repository, commit.getTree().getId());
                for (DiffEntry diff : formatter.scan(oldTree, newTree)) {
                    String path = DiffEntry.DEV_NULL.equals(diff.getNewPath())
                            ? diff.getOldPath() : diff.getNewPath();
                    if (!fileIds.containsKey(path)) {
                        continue;
                    }
                    MutableStat stat = stats.computeIfAbsent(path, ignored -> new MutableStat());
                    stat.totalCommits++;
                    Instant committedAt = commit.getCommitterIdent().getWhenAsInstant();
                    if (committedAt.isAfter(recentThreshold)) {
                        stat.commitsLast90Days++;
                    }
                    stat.contributors.add(commit.getAuthorIdent().getName());
                    if (stat.lastModifiedAt == null || committedAt.isAfter(stat.lastModifiedAt)) {
                        stat.lastModifiedAt = committedAt;
                        stat.lastAuthor = commit.getAuthorIdent().getName();
                        stat.lastCommitSha = commit.getName();
                    }
                    if (stat.recentSubjects.size() < 5) {
                        stat.recentSubjects.add(commit.getShortMessage());
                    }
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }

        List<GitFileStat> result = new ArrayList<>();
        stats.forEach((path, stat) -> result.add(new GitFileStat(
                fileIds.get(path),
                stat.totalCommits,
                stat.commitsLast90Days,
                stat.lastModifiedAt,
                stat.lastAuthor,
                stat.lastCommitSha,
                stat.contributors.size(),
                List.copyOf(stat.recentSubjects))));
        return result;
    }

    private AbstractTreeIterator tree(
            org.eclipse.jgit.lib.Repository repository,
            ObjectId treeId) throws IOException {
        try (var reader = repository.newObjectReader()) {
            CanonicalTreeParser parser = new CanonicalTreeParser();
            parser.reset(reader, treeId);
            return parser;
        }
    }

    private static final class MutableStat {
        private int totalCommits;
        private int commitsLast90Days;
        private Instant lastModifiedAt;
        private String lastAuthor;
        private String lastCommitSha;
        private final Set<String> contributors = new HashSet<>();
        private final List<String> recentSubjects = new ArrayList<>();
    }
}
