package dev.ko.processor.emitter;

import com.squareup.javapoet.*;
import dev.ko.processor.model.ApiEndpointModel;
import dev.ko.processor.model.ServiceModel;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import java.io.IOException;

public class ServiceClientEmitter {

    private final Filer filer;

    public ServiceClientEmitter(Filer filer) {
        this.filer = filer;
    }

    public void emit(ServiceModel service) throws IOException {
        String clientClassName = toClassName(service.name()) + "Client";
        String packageName = service.packageName();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(clientClassName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Generated client for the $L service.\n", service.name());

        for (ApiEndpointModel api : service.apis()) {
            MethodSpec method = MethodSpec.methodBuilder(api.name())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(Object.class)
                    .addStatement("throw new $T($S)",
                            UnsupportedOperationException.class,
                            "Service client not yet implemented — Phase 2")
                    .build();

            classBuilder.addMethod(method);
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .build();

        javaFile.writeTo(filer);
    }

    private static String toClassName(String kebabName) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebabName.split("-")) {
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }
}
