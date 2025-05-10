package com.maksonlee.gerrit.reflog.ssh;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.maksonlee.gerrit.reflog.controller.ReflogService;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;

@CommandMetaData(name = "import-native-reflog", description = "Import Git reflog history into reflog-recorder DB")
public class ImportReflogCommand extends SshCommand {

    @Inject
    private GitRepositoryManager repoManager;

    @Inject
    private ReflogService reflogService;

    @Override
    protected void run() {
        for (Project.NameKey projectKey : repoManager.list()) {
            stdout.printf("Project: %s%n", projectKey.get());

            try (Repository repo = repoManager.openRepository(projectKey)) {
                List<Ref> refs = repo.getRefDatabase().getRefsByPrefix("refs/heads/");
                if (refs.isEmpty()) {
                    stdout.printf("  No refs/heads/* found%n");
                    continue;
                }

                for (Ref ref : refs) {
                    String refName = ref.getName();  // e.g., "refs/heads/main"
                    stdout.printf("  Ref: %s%n", refName);
                    importReflogFromRef(repo, projectKey.get(), refName);
                }
            } catch (Exception e) {
                stderr.printf("Failed to process %s: %s%n", projectKey.get(), e.getMessage());
            }
        }
    }

    private void importReflogFromRef(Repository repo, String project, String ref) {
        try {
            ReflogReader reader = repo.getRefDatabase().getReflogReader(ref);
            if (reader == null) {
                stderr.printf("    No reflog for %s%n", ref);
                return;
            }

            for (ReflogEntry entry : reader.getReverseEntries()) {
                String oldRev = entry.getOldId().name();
                String newRev = entry.getNewId().name();
                Instant timestamp = entry.getWho().getWhenAsInstant();
                String user = entry.getWho().getName() + " <" + entry.getWho().getEmailAddress() + ">";

                if (reflogService.hasReflog(project, ref, newRev)) {
                    continue; // already imported
                }

                reflogService.recordReflog(project, ref, oldRev, newRev, user, timestamp);
            }
        } catch (Exception e) {
            stderr.printf("    Error reading reflog for %s: %s%n", ref, e.getMessage());
        }
    }
}