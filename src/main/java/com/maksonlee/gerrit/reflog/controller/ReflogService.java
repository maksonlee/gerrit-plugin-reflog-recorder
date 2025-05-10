package com.maksonlee.gerrit.reflog.controller;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Singleton;
import com.maksonlee.gerrit.reflog.model.ReflogRepository;
import com.maksonlee.gerrit.reflog.model.ReflogRepository.RevisionAtTime;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

@Singleton
public class ReflogService {
    private final ReflogRepository repo;
    private final GitRepositoryManager repoManager;

    @Inject
    public ReflogService(ReflogRepository repo, GitRepositoryManager repoManager) {
        this.repo = repo;
        this.repoManager = repoManager;
    }

    public Optional<RevisionAtTime> getRevisionAt(String project, String ref, Instant at) {
        return repo.findLastRevisionBefore(project, ref, at);
    }

    public boolean hasReflog(String project, String ref, String newRev) {
        return repo.exists(project, ref, newRev);
    }

    public void recordReflog(String project, String ref, String oldRev, String newRev, String username, Instant timestamp) {
        repo.save(project, ref, oldRev, newRev, username, timestamp);
    }

    public Optional<String> getManifestXmlAt(String manifestProject, String ref, Instant timestamp) {
        Optional<RevisionAtTime> rev = getRevisionAt(manifestProject, ref, timestamp);
        if (rev.isEmpty()) {
            return Optional.empty();
        }

        String commitSha = rev.get().revision();
        try (Repository gitRepo = repoManager.openRepository(Project.nameKey(manifestProject));
             RevWalk revWalk = new RevWalk(gitRepo)) {

            RevCommit commit = revWalk.parseCommit(ObjectId.fromString(commitSha));
            RevTree tree = commit.getTree();

            try (TreeWalk tw = TreeWalk.forPath(gitRepo, "default.xml", tree)) {
                if (tw == null) return Optional.empty();

                ObjectLoader loader = gitRepo.open(tw.getObjectId(0));
                return Optional.of(new String(loader.getBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default.xml from commit " + commitSha, e);
        }
    }
}
