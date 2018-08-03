package ch.bergturbenthal.raoa.server;

import java.io.File;

import ch.bergturbenthal.raoa.server.service.AlbumService;
import ch.bergturbenthal.raoa.server.service.impl.DefaultAlbumService;

public class TestRepositoryInit {
    public static void main(final String[] args) {
        final AlbumService albumService = new DefaultAlbumService();

        albumService.detectAlbum(new File("/tmp/raoa-storage/Vereinsolympiade Final 2018.git"));
    }
}
