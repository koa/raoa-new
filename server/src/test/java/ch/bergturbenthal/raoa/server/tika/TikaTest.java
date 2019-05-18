package ch.bergturbenthal.raoa.server.tika;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

@Slf4j
public class TikaTest {
  public static void main(String[] args) {
    AutoDetectParser parser = new AutoDetectParser();
    BodyContentHandler handler = new BodyContentHandler();
    Metadata metadata = new Metadata();
    final String file =
        "/data/photos/Pferde/Turniere/2019/Selestat 2019/2019-04-28-17-53-34-_DSC0276.JPG";
    // final String file =
    //   "/data/photos/Pferde/Turniere/2019/Selestat 2019/Dressur Christof König.mp4";
    try (InputStream stream = new FileInputStream(file)) {

      AtomicLong currentPos = new AtomicLong();
      AtomicLong skipped = new AtomicLong();
      final InputStream countingStream =
          new InputStream() {
            @Override
            public int read(final byte[] b) throws IOException {
              final int count = stream.read(b);
              if (count >= 0) currentPos.addAndGet(count);
              return count;
            }

            @Override
            public int read() throws IOException {
              final int read = stream.read();
              if (read >= 0) currentPos.incrementAndGet();
              return read;
            }

            @Override
            public long skip(final long n) throws IOException {
              final long skippedBytes = stream.skip(n);
              if (skippedBytes >= 0) skipped.addAndGet(skippedBytes);
              return skippedBytes;
            }

            @Override
            public int available() throws IOException {
              return stream.available();
            }

            @Override
            public synchronized void mark(final int readlimit) {
              stream.mark(readlimit);
            }

            @Override
            public boolean markSupported() {
              return stream.markSupported();
            }
          };
      parser.parse(countingStream, handler, metadata);
      log.info("read count: " + currentPos);
      log.info("Skipped: " + skipped);
      log.info("Result: " + handler.toString());
      log.info("Type: " + metadata.get("Content-Type"));
      log.info("Created: " + metadata.getDate(TikaCoreProperties.CREATED).toInstant());
      log.info("Created: " + metadata.get(TikaCoreProperties.CREATED));
      log.info("Orientation: " + metadata.get(TIFF.ORIENTATION));

      for (String name : metadata.names()) {
        log.info(name + ": " + metadata.get(name));
      }

    } catch (IOException | SAXException | TikaException e) {
      log.warn("Error parsing file");
      e.printStackTrace();
    }
  }
}
