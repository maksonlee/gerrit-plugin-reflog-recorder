package com.maksonlee.gerrit.reflog.util;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Singleton;
import com.maksonlee.gerrit.reflog.controller.ReflogService;
import com.maksonlee.gerrit.reflog.model.ReflogRepository;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.Optional;

@Singleton
public class ManifestResolver {

    private final ReflogService reflogService;
    private final GitRepositoryManager repoManager;

    @Inject
    public ManifestResolver(ReflogService reflogService, GitRepositoryManager repoManager) {
        this.reflogService = reflogService;
        this.repoManager = repoManager;
    }

    public Document generatePinnedManifest(String manifestXml, String manifestRef, Instant timestamp) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(manifestXml)));

            NodeList projects = doc.getElementsByTagName("project");

            for (int i = 0; i < projects.getLength(); i++) {
                Element project = (Element) projects.item(i);

                String groups = project.getAttribute("groups");
                if (groups.contains("notdefault")) continue;

                String name = project.getAttribute("name");
                String originalRevision = project.getAttribute("revision");
                if (originalRevision.isEmpty()) {
                    originalRevision = manifestRef;
                }

                try (Repository repo = repoManager.openRepository(Project.nameKey(name))) {
                    Optional<String> resolved = resolveProjectRevision(repo, name, originalRevision, timestamp);
                    if (resolved.isPresent()) {
                        project.setAttribute("revision", resolved.get());
                        project.setAttribute("upstream", originalRevision);
                        project.setAttribute("dest-branch", originalRevision);
                    }
                } catch (RepositoryNotFoundException e) {
                    // skip
                } catch (IOException e) {
                    // skip
                }
            }

            removeElementsByTag(doc, "superproject");
            removeElementsByTag(doc, "contactinfo");
            removeEmptyTextNodes(doc.getDocumentElement());

            return doc;

        } catch (Exception e) {
            throw new RuntimeException("Failed to process manifest", e);
        }
    }

    private Optional<String> resolveProjectRevision(
            Repository repo,
            String project,
            String revisionAttr,
            Instant timestamp
    ) {
        try {
            RefDatabase refDb = repo.getRefDatabase();

            if (revisionAttr.startsWith("refs/heads/")) {
                return reflogService.getRevisionAt(project, revisionAttr, timestamp)
                        .map(ReflogRepository.RevisionAtTime::revision);
            }

            if (revisionAttr.startsWith("refs/tags/")) {
                ObjectId tagId = repo.resolve(revisionAttr);
                return Optional.ofNullable(tagId).map(ObjectId::name);
            }

            String asBranch = "refs/heads/" + revisionAttr;
            if (refDb.exactRef(asBranch) != null) {
                return reflogService.getRevisionAt(project, asBranch, timestamp)
                        .map(ReflogRepository.RevisionAtTime::revision);
            }

            String asTag = "refs/tags/" + revisionAttr;
            if (refDb.exactRef(asTag) != null) {
                ObjectId tagId = repo.resolve(asTag);
                return Optional.ofNullable(tagId).map(ObjectId::name);
            }

            ObjectId resolved = repo.resolve(revisionAttr);
            return Optional.ofNullable(resolved).map(ObjectId::name);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void removeElementsByTag(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            Node node = nodes.item(i);
            node.getParentNode().removeChild(node);
        }
    }

    private void removeEmptyTextNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
                node.removeChild(child);
            } else {
                removeEmptyTextNodes(child);
            }
        }
    }
}
