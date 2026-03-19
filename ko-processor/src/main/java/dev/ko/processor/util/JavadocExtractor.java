package dev.ko.processor.util;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;

public final class JavadocExtractor {

    private JavadocExtractor() {}

    public static String extractFirstParagraph(ProcessingEnvironment processingEnv, Element element) {
        String docComment = processingEnv.getElementUtils().getDocComment(element);
        if (docComment == null || docComment.isBlank()) {
            return null;
        }

        // Take first paragraph (up to blank line or @tag)
        StringBuilder sb = new StringBuilder();
        for (String line : docComment.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("@")) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(trimmed);
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }
}
