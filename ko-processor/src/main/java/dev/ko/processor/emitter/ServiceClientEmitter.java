package dev.ko.processor.emitter;

import com.squareup.javapoet.*;
import dev.ko.annotations.KoAPI;
import dev.ko.processor.model.ServiceModel;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;

/**
 * Generates a {ServiceName}Client class for each @KoService.
 * Each @KoAPI method gets a matching client method that delegates
 * to KoServiceCaller for in-process or HTTP invocation.
 */
public class ServiceClientEmitter {

    private static final ClassName KO_SERVICE_CALLER =
            ClassName.get("dev.ko.runtime.service", "KoServiceCaller");

    private final Filer filer;

    public ServiceClientEmitter(Filer filer) {
        this.filer = filer;
    }

    public void emit(ServiceModel service, TypeElement serviceElement) throws IOException {
        String clientClassName = toClassName(service.name()) + "Client";
        String packageName = service.packageName();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(clientClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("javax.annotation.processing", "Generated"))
                        .addMember("value", "$S", "ko-annotation-processor")
                        .build())
                .addJavadoc("Generated client for the $L service.\n" +
                        "Call methods on this class to invoke the target service's endpoints.\n", service.name());

        // Add KoServiceCaller field and setter
        classBuilder.addField(KO_SERVICE_CALLER, "caller", Modifier.PRIVATE);

        classBuilder.addMethod(MethodSpec.methodBuilder("setCaller")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(KO_SERVICE_CALLER, "caller")
                .addStatement("this.caller = caller")
                .build());

        // Generate a method for each @KoAPI endpoint
        for (var enclosed : serviceElement.getEnclosedElements()) {
            KoAPI apiAnnotation = enclosed.getAnnotation(KoAPI.class);
            if (apiAnnotation == null) continue;

            ExecutableElement method = (ExecutableElement) enclosed;
            classBuilder.addMethod(buildClientMethod(method, apiAnnotation, service.name()));
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .build();

        javaFile.writeTo(filer);
    }

    private MethodSpec buildClientMethod(ExecutableElement method, KoAPI api, String serviceName) {
        String methodName = method.getSimpleName().toString();
        TypeMirror returnType = method.getReturnType();

        MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "$S", "unchecked")
                        .build());

        // Set return type
        boolean isVoid = returnType.getKind() == TypeKind.VOID;
        if (isVoid) {
            builder.returns(TypeName.VOID);
        } else {
            builder.returns(TypeName.get(returnType));
        }

        // Add parameters matching the original method
        for (VariableElement param : method.getParameters()) {
            builder.addParameter(TypeName.get(param.asType()), param.getSimpleName().toString());
        }

        // Build args array
        StringBuilder argsLiteral = new StringBuilder("new Object[]{");
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i > 0) argsLiteral.append(", ");
            argsLiteral.append(method.getParameters().get(i).getSimpleName());
        }
        argsLiteral.append("}");

        // Build the call statement
        if (isVoid) {
            builder.addStatement("caller.call($S, $S, $S, $L)",
                    serviceName, api.method(), api.path(), argsLiteral.toString());
        } else {
            builder.addStatement("return ($T) caller.call($S, $S, $S, $L)",
                    TypeName.get(returnType),
                    serviceName, api.method(), api.path(), argsLiteral.toString());
        }

        return builder.build();
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
