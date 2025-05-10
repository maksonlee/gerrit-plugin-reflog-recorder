package com.maksonlee.gerrit.reflog.ssh;

import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.maksonlee.gerrit.reflog.controller.ReflogService;
import com.maksonlee.gerrit.reflog.util.ManifestResolver;
import org.kohsuke.args4j.Argument;
import org.w3c.dom.Document;

import javax.inject.Inject;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@CommandMetaData(name = "resolve-manifest", description = "Generate a pinned manifest from reflog at a given timestamp")
public class ResolveManifestCommand extends SshCommand {

    @Argument(index = 0, required = true, metaVar = "MANIFEST_PROJECT")
    private String manifestProject;

    @Argument(index = 1, required = true, metaVar = "REF")
    private String manifestRef;

    @Argument(index = 2, required = true, metaVar = "TIMESTAMP")
    private String timestampStr;

    @Inject
    private ReflogService reflogService;

    @Inject
    private ManifestResolver manifestResolver;

    @Override
    protected void run() throws Exception {
        Instant timestamp;
        try {
            timestamp = ZonedDateTime.parse(timestampStr).toInstant();
        } catch (DateTimeParseException e) {
            stderr.printf("Invalid timestamp format: %s%n", e.getMessage());
            return;
        }

        Optional<String> manifestOpt = reflogService.getManifestXmlAt(manifestProject, manifestRef, timestamp);
        if (manifestOpt.isEmpty()) {
            stderr.printf("Could not find default.xml in %s at %s%n", manifestProject, timestampStr);
            return;
        }

        Document doc = manifestResolver.generatePinnedManifest(manifestOpt.get(), manifestRef, timestamp);

        Transformer tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        tf.transform(new DOMSource(doc), new StreamResult(stdout));
    }
}