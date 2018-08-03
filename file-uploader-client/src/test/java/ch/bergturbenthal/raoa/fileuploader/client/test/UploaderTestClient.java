
package ch.bergturbenthal.raoa.fileuploader.client.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Bean;

import com.google.common.util.concurrent.ListenableFuture;

import ch.bergturbenthal.raoa.fileuploader.client.EnableFileUploadClient;
import ch.bergturbenthal.raoa.fileuploader.client.UploaderClient;
import ch.bergturbenthal.raoa.service.file.upload.test.RequestFileUploadServiceGrpc;
import ch.bergturbenthal.raoa.service.file.upload.test.RequestFileUploadServiceGrpc.RequestFileUploadServiceBlockingStub;
import ch.bergturbenthal.raoa.service.file.upload.test.Test.FileUploadRequest;
import ch.bergturbenthal.raoa.service.file.upload.test.Test.FileUploadResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@EnableFileUploadClient
@SpringBootApplication
public class UploaderTestClient {
    public static void main(final String[] args) throws FileNotFoundException, InterruptedException, ExecutionException {
        final SpringApplication springApplication = new SpringApplication(UploaderTestClient.class);
        final ConfigurableApplicationContext applicationContext = springApplication.run(args);
        final UploaderClient uploaderClient = applicationContext.getBean(UploaderClient.class);
        final ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext().build();
        final RequestFileUploadServiceBlockingStub requestUploadService = RequestFileUploadServiceGrpc.newBlockingStub(channel);
        final File uploadFile = new File("pom.xml");
        final FileUploadResponse uploadResponse = requestUploadService
                .requestFileUpload(FileUploadRequest.newBuilder().setFilename(uploadFile.getName()).setFilesize(uploadFile.length()).build());
        final long uploadHandle = uploadResponse.getUploadHandle();
        final ListenableFuture<Void> future = uploaderClient.sendFile(new FileInputStream(uploadFile), uploadHandle, channel);
        future.get();
    }

    @Bean
    public Executor executor() {
        return Executors.newCachedThreadPool();
    }
}
