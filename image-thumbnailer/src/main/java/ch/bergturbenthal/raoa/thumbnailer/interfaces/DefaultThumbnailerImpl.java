package ch.bergturbenthal.raoa.thumbnailer.interfaces;

import ch.bergturbenthal.raoa.filedownloader.server.domain.service.DownloadService;
import ch.bergturbenthal.raoa.filedownloader.server.domain.service.DownloadService.DownloadDataSource;
import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService;
import ch.bergturbenthal.raoa.fileuploader.server.domain.service.FileuploadService.UploadingFileSlot;
import ch.bergturbenthal.raoa.service.thumbnailer.*;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.lognet.springboot.grpc.GRpcService;
import org.xml.sax.SAXException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

@Slf4j
@GRpcService
public class DefaultThumbnailerImpl extends ThumbnailerServiceGrpc.ThumbnailerServiceImplBase {

  private final FileuploadService fileuploadService;
  private final DownloadService downloadService;
  private final Semaphore throttleSemaphore = new Semaphore(4);

  public DefaultThumbnailerImpl(
      final FileuploadService fileuploadService, final DownloadService downloadService) {
    this.fileuploadService = fileuploadService;
    this.downloadService = downloadService;
  }

  private static Mono<File> convertPlainJava(FileuploadService.UploadedFile data) {
    try {
      AutoDetectParser parser = new AutoDetectParser();
      BodyContentHandler handler = new BodyContentHandler();
      Metadata metadata = new Metadata();
      final TikaInputStream inputStream = TikaInputStream.get(data.getFile().toPath());
      parser.parse(inputStream, handler, metadata);
      final int orientation = Optional.ofNullable(metadata.getInt(TIFF.ORIENTATION)).orElse(1);

      AffineTransform t = new AffineTransform();
      final BufferedImage inputImage = ImageIO.read(data.getFile());

      final int width = inputImage.getWidth();
      final int height = inputImage.getHeight();
      switch (orientation) {
        case 1:
          break;
        case 2: // Flip X
          t.scale(-1.0, 1.0);
          t.translate(-width, 0);
          break;
        case 3: // PI rotation
          t.translate(width, height);
          t.quadrantRotate(2);
          break;
        case 4: // Flip Y
          t.scale(1.0, -1.0);
          t.translate(0, -height);
          break;
        case 5: // - PI/2 and Flip X
          t.quadrantRotate(3);
          t.scale(-1.0, 1.0);
          break;
        case 6: // -PI/2 and -width
          t.translate(height, 0);
          t.quadrantRotate(1);
          break;
        case 7: // PI/2 and Flip
          t.scale(-1.0, 1.0);
          t.translate(-height, 0);
          t.translate(0, width);
          t.quadrantRotate(3);
          break;
        case 8: // PI / 2
          t.translate(0, width);
          t.quadrantRotate(3);
          break;
      }
      int maxLength = Math.max(width, height);
      double scale = 1600.0 / maxLength;
      t.scale(scale, scale);
      AffineTransformOp op = new AffineTransformOp(t, AffineTransformOp.TYPE_BICUBIC);

      BufferedImage canvasImage =
          op.createCompatibleDestImage(
              inputImage,
              (inputImage.getType() == BufferedImage.TYPE_BYTE_GRAY)
                  ? inputImage.getColorModel()
                  : null);
      Graphics2D g = canvasImage.createGraphics();
      g.setBackground(Color.WHITE);
      g.clearRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
      final BufferedImage targetImage = op.filter(inputImage, canvasImage);
      final File tempFile = File.createTempFile("", ".jpg");
      ImageIO.write(targetImage, "jpeg", tempFile);
      return Mono.just(tempFile);
    } catch (TikaException | IOException | SAXException e) {
      return Mono.error(e);
    } finally {
      data.close();
    }
  }

  private static Mono<File> convert(FileuploadService.UploadedFile data) {
    return Mono.create(
        (MonoSink<File> sink) -> {
          try {
            final File sourceFile = data.getFile();
            final File targetTempFile1 = File.createTempFile("out", ".jpg");
            final File logfile = File.createTempFile("out", ".log");
            final Process scaleDownprocess =
                new ProcessBuilder(
                        "convert",
                        sourceFile.getAbsolutePath(),
                        "-auto-orient",
                        "-resize",
                        "1600x1600",
                        targetTempFile1.getAbsolutePath())
                    .redirectOutput(logfile)
                    .start();
            // create thread to supervise external process
            new Thread(
                    () -> {
                      try {
                        final int resultCode = scaleDownprocess.waitFor();
                        if (resultCode == 0) {
                          sink.success(targetTempFile1);
                          logfile.delete();
                        } else {
                          sink.error(
                              new RuntimeException("Cannot convert image, result: " + resultCode));
                          targetTempFile1.delete();
                        }
                      } catch (final InterruptedException e) {
                        sink.error(new RuntimeException("Waiting on result interruppted", e));
                      } finally {
                        data.close();
                      }
                    })
                .start();
          } catch (final IOException e) {
            sink.error(new RuntimeException("Cannot scale image " + data.getFile(), e));
            data.close();
          }
        });
  }

  @Override
  public void generateThumbnail(
      final GenerationRequest request, final StreamObserver<GenerationResponse> responseObserver) {
    if (!throttleSemaphore.tryAcquire()) {
      responseObserver.onNext(
          GenerationResponse.newBuilder()
              .setOverloadedResponse(ServiceOverloadedResponse.newBuilder())
              .build());
      responseObserver.onCompleted();
    }
    final long length = request.getLength();
    final UploadingFileSlot uploadingFileSlot = fileuploadService.anounceUpload(length, ".jpg");
    uploadingFileSlot.setUploadTimeout(Instant.now().plus(1, ChronoUnit.MINUTES));
    responseObserver.onNext(
        GenerationResponse.newBuilder().setFileUploadHandle(uploadingFileSlot.getHandle()).build());
    uploadingFileSlot
        .getData()
        .flatMap(
            // create thread to supervise external process
            // TODO: handle error
            DefaultThumbnailerImpl::convertPlainJava)
        .map(
            targetTempFile ->
                FileGeneratedMessage.newBuilder()
                    .setFileDownloadHandle(
                        downloadService.anounceFile(
                            new DownloadDataSource() {

                              @Override
                              public void downloadFinished() {
                                targetTempFile.delete();
                              }

                              @Override
                              public InputStream getInputStream() throws IOException {
                                return new FileInputStream(targetTempFile);
                              }
                            }))
                    .setFileSize(targetTempFile.length())
                    .build())
        .map(message -> GenerationResponse.newBuilder().setFileReadyResponse(message).build())
        .doFinally(signal -> throttleSemaphore.release())
        .subscribe(
            r -> {
              responseObserver.onNext(r);
              responseObserver.onCompleted();
            },
            responseObserver::onError,
            responseObserver::onCompleted);
  }

  @Override
  public void getCapabilities(
      final Empty request, final StreamObserver<ThumbnailerCapabilities> responseObserver) {
    responseObserver.onNext(ThumbnailerCapabilities.newBuilder().addRegex(".*\\.JPG").build());
    responseObserver.onCompleted();
  }
}
