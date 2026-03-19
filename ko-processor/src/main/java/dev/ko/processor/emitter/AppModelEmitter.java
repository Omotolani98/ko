package dev.ko.processor.emitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.ko.processor.model.AppModel;

import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;

public class AppModelEmitter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Filer filer;

    public AppModelEmitter(Filer filer) {
        this.filer = filer;
    }

    public void emit(AppModel appModel) throws IOException {
        FileObject fileObject = filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                "ko-app-model.json"
        );

        try (Writer writer = fileObject.openWriter()) {
            writer.write(MAPPER.writeValueAsString(appModel));
        }
    }
}
