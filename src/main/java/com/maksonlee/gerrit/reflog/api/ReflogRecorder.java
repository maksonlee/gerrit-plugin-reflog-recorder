package com.maksonlee.gerrit.reflog.api;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.maksonlee.gerrit.reflog.controller.ReflogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

@Singleton
public class ReflogRecorder implements GitReferenceUpdatedListener {
    private static final Logger logger = LoggerFactory.getLogger(ReflogRecorder.class);

    private final ReflogService reflogService;
    private final String pluginName;

    @Inject
    public ReflogRecorder(ReflogService reflogService, @PluginName String pluginName) {
        this.reflogService = reflogService;
        this.pluginName = pluginName;
    }

    @Override
    public void onGitReferenceUpdated(Event event) {
        try {
            String project = event.getProjectName();
            String ref = event.getRefName();
            String oldRev = event.getOldObjectId();
            String newRev = event.getNewObjectId();

            logger.debug("Recording ref update: project={}, ref={}, old={}, new={}",
                    project, ref, oldRev, newRev);

            reflogService.recordReflog(project, ref, oldRev, newRev, "system", Instant.now());

            logger.info("Reflog recorded for {}/{}", project, ref);

        } catch (Exception e) {
            logger.error("Failed to record reflog event", e);
        }
    }
}