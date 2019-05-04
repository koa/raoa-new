package ch.bergturbenthal.raoa.filedownloader.server;

import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FileDownloadServiceConfiguration.class)
public @interface EnableFileDownloadService {}
