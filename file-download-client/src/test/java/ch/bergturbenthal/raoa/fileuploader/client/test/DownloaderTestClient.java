package ch.bergturbenthal.raoa.fileuploader.client.test;

import ch.bergturbenthal.raoa.filedownload.client.DownloadClient;
import ch.bergturbenthal.raoa.filedownload.client.EnableFileDownloadClient;
import ch.bergturbenthal.raoa.service.file.upload.test.RequestFileDownloadServiceGrpc;
import ch.bergturbenthal.raoa.service.file.upload.test.RequestFileDownloadServiceGrpc.RequestFileDownloadServiceBlockingStub;
import ch.bergturbenthal.raoa.service.file.upload.test.Test;
import ch.bergturbenthal.raoa.service.file.upload.test.Test.FileDownloadResponse;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Flux;

@Slf4j
@EnableFileDownloadClient
@SpringBootApplication
public class DownloaderTestClient {
  public static void main(final String[] args)
      throws InterruptedException, ExecutionException, IOException {
    final SpringApplication springApplication = new SpringApplication(DownloaderTestClient.class);
    final ConfigurableApplicationContext applicationContext = springApplication.run(args);
    final DownloadClient downloadClient = applicationContext.getBean(DownloadClient.class);
    final ManagedChannel channel =
        ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext().build();

    final RequestFileDownloadServiceBlockingStub requestFileStub =
        RequestFileDownloadServiceGrpc.newBlockingStub(channel);
    final FileDownloadResponse downloadResponse =
        requestFileStub.requestFileDownload(Test.FileDownloadRequest.newBuilder().build());

    final File targetFile = File.createTempFile("data", ".out");
    final FileOutputStream outputStream = new FileOutputStream(targetFile);
    final Flux<ByteString> fileData =
        downloadClient.downloadFile(downloadResponse.getDownloadHandle(), channel);
    final Semaphore waitSemaphore = new Semaphore(0);
    fileData.subscribe(
        data -> {
          try {
            data.writeTo(outputStream);
          } catch (final IOException e) {
            throw new RuntimeException(e);
          }
        },
        ex -> {
          log.error("Error from server: ", ex);
        },
        () -> {
          try {
            outputStream.close();
            log.info("Written " + targetFile.getAbsolutePath());
          } catch (final IOException e) {
            log.error("Cannot write output", e);
          } finally {
            waitSemaphore.release();
          }
        });
    waitSemaphore.acquire();
    applicationContext.close();
  }
}
