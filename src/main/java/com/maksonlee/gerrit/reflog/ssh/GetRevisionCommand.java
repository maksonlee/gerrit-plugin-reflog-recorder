package com.maksonlee.gerrit.reflog.ssh;

import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.maksonlee.gerrit.reflog.controller.ReflogService;
import com.maksonlee.gerrit.reflog.model.ReflogRepository.RevisionAtTime;
import org.kohsuke.args4j.Argument;

import javax.inject.Inject;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@CommandMetaData(name = "get-reflog", description = "Get reflog revision at time")
public class GetRevisionCommand extends SshCommand {

    @Argument(index = 0, required = true, metaVar = "PROJECT")
    private String project;

    @Argument(index = 1, required = true, metaVar = "REF")
    private String ref;

    @Argument(index = 2, required = true, metaVar = "TIMESTAMP (ISO8601)")
    private String timestampStr;

    @Inject
    private ReflogService reflogService;

    @Override
    protected void run() {
        Instant timestamp;
        try {
            timestamp = ZonedDateTime.parse(timestampStr).toInstant();
        } catch (DateTimeParseException e) {
            stderr.printf("Invalid timestamp format: %s%n", e.getMessage());
            return;
        }

        Optional<RevisionAtTime> result = reflogService.getRevisionAt(project, ref, timestamp);
        if (result.isEmpty()) {
            stderr.printf("Not found for %s %s at %s%n", project, ref, timestampStr);
            return;
        }

        RevisionAtTime r = result.get();
        stdout.printf("Project:  %s%n", r.project());
        stdout.printf("Ref:      %s%n", r.ref());
        stdout.printf("Revision: %s%n", r.revision());
        stdout.printf("Time:     %s%n", r.timestamp());
    }
}
