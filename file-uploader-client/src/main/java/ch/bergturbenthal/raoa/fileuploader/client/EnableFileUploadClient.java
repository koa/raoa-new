package ch.bergturbenthal.raoa.fileuploader.client;

import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FileUploadClientConfiguration.class)
public @interface EnableFileUploadClient {}
