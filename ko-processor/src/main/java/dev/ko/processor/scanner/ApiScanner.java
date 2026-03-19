package dev.ko.processor.scanner;

import dev.ko.annotations.KoAPI;
import dev.ko.processor.model.ApiEndpointModel;
import dev.ko.processor.model.TypeModel;
import dev.ko.processor.util.JavadocExtractor;
import dev.ko.processor.util.TypeMirrorUtils;
import dev.ko.processor.validator.PathValidator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApiScanner {

    private final ProcessingEnvironment processingEnv;

    public ApiScanner(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public List<ApiEndpointModel> scan(TypeElement serviceElement) {
        List<ApiEndpointModel> apis = new ArrayList<>();

        for (var enclosed : serviceElement.getEnclosedElements()) {
            KoAPI apiAnnotation = enclosed.getAnnotation(KoAPI.class);
            if (apiAnnotation == null) {
                continue;
            }

            ExecutableElement method = (ExecutableElement) enclosed;
            String name = method.getSimpleName().toString();

            // Extract request type (first non-path-param parameter, if any)
            TypeModel requestType = extractRequestType(method);

            // Extract response type
            TypeModel responseType = TypeMirrorUtils.isVoid(method.getReturnType())
                    ? null
                    : TypeMirrorUtils.extractTypeModel(method.getReturnType());

            String javadoc = JavadocExtractor.extractFirstParagraph(processingEnv, method);

            apis.add(new ApiEndpointModel(
                    name,
                    apiAnnotation.method(),
                    apiAnnotation.path(),
                    apiAnnotation.auth(),
                    Arrays.asList(apiAnnotation.permissions()),
                    apiAnnotation.expose(),
                    requestType,
                    responseType,
                    javadoc
            ));
        }

        return apis;
    }

    private TypeModel extractRequestType(ExecutableElement method) {
        List<? extends VariableElement> params = method.getParameters();
        for (VariableElement param : params) {
            // Skip path parameters
            if (param.getAnnotation(dev.ko.annotations.PathParam.class) != null) {
                continue;
            }
            return TypeMirrorUtils.extractTypeModel(param.asType());
        }
        return null;
    }
}
