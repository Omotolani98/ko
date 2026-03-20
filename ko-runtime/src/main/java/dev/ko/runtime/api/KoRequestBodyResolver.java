package dev.ko.runtime.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ko.annotations.PathParam;
import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Resolves non-@PathParam parameters as JSON request body.
 */
public class KoRequestBodyResolver implements HandlerMethodArgumentResolver {

    private final ObjectMapper objectMapper;

    public KoRequestBodyResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return !parameter.hasParameterAnnotation(PathParam.class)
                && !BeanUtils.isSimpleProperty(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }
        try {
            return objectMapper.readValue(request.getInputStream(), parameter.getParameterType());
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize request body to "
                    + parameter.getParameterType().getSimpleName(), e);
        }
    }
}
