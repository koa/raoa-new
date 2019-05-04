package ch.bergturbenthal.raoa.filedownload.client;

import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(DownloadClientConfiguration.class)
public @interface EnableFileDownloadClient {}
