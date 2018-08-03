package ch.bergturbenthal.raoa.server.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import ch.bergturbenthal.raoa.server.service.AlbumService;
import lombok.Builder;
import lombok.Cleanup;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultAlbumService implements AlbumService {

    @Value
    @Builder(toBuilder = true)
    private static class AlbumState {
        private boolean loadingData;
        private boolean creatingThumbnails;
        private boolean shouldUpdateThumbnails;
    }

    private final Map<File, AlbumState> currentAlbumState = Collections.synchronizedMap(new HashMap<>());

    @Override
    public void detectAlbum(final File dir) {
        try {
            log.info("Open " + dir);
            @Cleanup
            final Git git = Git.open(dir);
            final Repository repository = git.getRepository();

            final Ref masterRef = repository.exactRef("refs/heads/master");
            // final ObjectDatabase objectDatabase = repository.getObjectDatabase();
            @Cleanup
            final RevWalk walk = new RevWalk(repository);
            final RevCommit commit = walk.parseCommit(masterRef.getObjectId());
            final RevTree tree = commit.getTree();
            @Cleanup
            final TreeWalk treeWalk = new TreeWalk(repository);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                final String pathString = treeWalk.getPathString();
                final ObjectId objectId = treeWalk.getObjectId(0);
                log.info("Path: " + pathString + " -> " + objectId.name());
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
