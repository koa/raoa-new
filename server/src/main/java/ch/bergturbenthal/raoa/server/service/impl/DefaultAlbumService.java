package ch.bergturbenthal.raoa.server.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.service.AlbumService;
import ch.bergturbenthal.raoa.server.service.Thumbnailer;
import lombok.Builder;
import lombok.Cleanup;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DefaultAlbumService implements AlbumService {

    @Value
    @Builder(toBuilder = true)
    private static class AlbumState {
        private boolean loadingData;
        private boolean creatingThumbnails;
        private boolean shouldUpdateThumbnails;
    }

    private final class GitObjectResource extends AbstractResource {
        private final Supplier<ObjectLoader> currentObjectLoader;
        private final String                 pathString;
        private final ObjectId               objectId;

        private GitObjectResource(final Supplier<ObjectLoader> currentObjectLoader, final String pathString, final ObjectId objectId) {
            this.currentObjectLoader = currentObjectLoader;
            this.pathString = pathString;
            this.objectId = objectId;
        }

        @Override
        public long contentLength() throws IOException {
            return currentObjectLoader.get().getSize();
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public String getDescription() {
            return "Resource: " + objectId.toString();
        }

        @Override
        public String getFilename() {
            return pathString;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return currentObjectLoader.get().openStream();
        }
    }

    private final Thumbnailer           thumbnailer;
    private final Map<File, AlbumState> currentAlbumState = Collections.synchronizedMap(new HashMap<>());

    public DefaultAlbumService(final Thumbnailer thumbnailer) {
        this.thumbnailer = thumbnailer;
    }

    @Override
    public void detectAlbum(final File dir) {
        try {
            log.info("Open " + dir);
            @Cleanup
            final Git git = Git.open(dir);
            final Repository repository = git.getRepository();

            final Ref masterRef = repository.exactRef("refs/heads/master");
            final Ref thumbnailsRef = repository.exactRef("refs/heads/thumbnails");
            @Cleanup
            final RevWalk walk = new RevWalk(repository);
            @Cleanup
            final TreeWalk treeWalk = new TreeWalk(repository);
            final Set<AnyObjectId> existingThumbnails = new HashSet<>();
            if (thumbnailsRef != null) {
                final RevCommit thumbnailsCommit = walk.parseCommit(thumbnailsRef.getObjectId());
                treeWalk.addTree(thumbnailsCommit.getTree());
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    final ObjectId objectId = ObjectId.fromString(treeWalk.getNameString());
                    existingThumbnails.add(objectId);
                }
                treeWalk.reset();
            }
            // final ObjectDatabase objectDatabase = repository.getObjectDatabase();
            final RevCommit commit = walk.parseCommit(masterRef.getObjectId());
            final RevTree tree = commit.getTree();
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                final String pathString = treeWalk.getPathString();
                final ObjectId objectId = treeWalk.getObjectId(0);
                if (!existingThumbnails.contains(objectId)) {
                    final Supplier<ObjectLoader> currentObjectLoader = () -> {
                        try {
                            final ObjectDatabase objectDatabase = repository.getObjectDatabase();
                            final ObjectReader newReader = objectDatabase.newReader();
                            return newReader.open(objectId);
                        } catch (final IOException e) {
                            throw new RuntimeException("Cannot open object", e);
                        }
                    };
                    final Resource gitContentResource = new GitObjectResource(currentObjectLoader, pathString, objectId);
                    thumbnailer.createThumbnail(gitContentResource).subscribe((final Resource result) -> {
                        final ObjectInserter newInserter = repository.getObjectDatabase().newInserter();
                        try {
                            final ObjectId objectId2 = newInserter.insert(Constants.OBJ_BLOB, result.contentLength(), result.getInputStream());
                            log.info("Created Thumbnail: " + objectId2);
                        } catch (final IOException e) {
                            log.error("Cannot write new object", e);
                        }
                    }, ex -> {
                        log.error("cannot create thumbnail", ex);
                    });
                    log.info("Path: " + pathString + " -> " + objectId.name());
                }
            }
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // TODO Auto-generated method stub

    }

    private AlbumState stateOf(final File albumDir) {
        return currentAlbumState.computeIfAbsent(albumDir, k -> AlbumState.builder().build());
    }

}
