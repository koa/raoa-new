package ch.bergturbenthal.raoa.fileuploader.server;

import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FileUploaderServiceConfiguration.class)
public @interface EnableFileUploadServerService {}
