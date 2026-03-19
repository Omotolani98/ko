package dev.ko.processor.util;

import dev.ko.processor.model.FieldModel;
import dev.ko.processor.model.TypeModel;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public final class TypeMirrorUtils {

    private TypeMirrorUtils() {}

    public static String getSimpleName(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            return declaredType.asElement().getSimpleName().toString();
        }
        return typeMirror.toString();
    }

    public static String getQualifiedName(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            TypeElement typeElement = (TypeElement) declaredType.asElement();
            return typeElement.getQualifiedName().toString();
        }
        return typeMirror.toString();
    }

    public static List<? extends TypeMirror> getTypeArguments(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            return declaredType.getTypeArguments();
        }
        return List.of();
    }

    public static TypeModel extractTypeModel(TypeMirror typeMirror) {
        if (typeMirror == null || typeMirror.getKind() != TypeKind.DECLARED) {
            return null;
        }

        DeclaredType declaredType = (DeclaredType) typeMirror;
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        String className = typeElement.getQualifiedName().toString();

        List<FieldModel> fields = extractFields(typeElement);
        return new TypeModel(className, fields);
    }

    public static List<FieldModel> extractFields(TypeElement typeElement) {
        // Handle records
        List<? extends RecordComponentElement> recordComponents = typeElement.getRecordComponents();
        if (recordComponents != null && !recordComponents.isEmpty()) {
            return recordComponents.stream()
                    .map(rc -> new FieldModel(
                            rc.getSimpleName().toString(),
                            rc.asType().toString(),
                            true
                    ))
                    .toList();
        }

        // Handle regular classes — extract fields
        return typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .map(e -> new FieldModel(
                        e.getSimpleName().toString(),
                        e.asType().toString(),
                        !isNullable(e)
                ))
                .toList();
    }

    private static boolean isNullable(Element element) {
        return element.getAnnotationMirrors().stream()
                .anyMatch(am -> am.getAnnotationType().toString().endsWith("Nullable"));
    }

    public static boolean isVoid(TypeMirror typeMirror) {
        return typeMirror.getKind() == TypeKind.VOID
                || typeMirror.getKind() == TypeKind.NONE;
    }
}
