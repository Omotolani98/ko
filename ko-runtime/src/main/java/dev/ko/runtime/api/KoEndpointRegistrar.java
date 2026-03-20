package dev.ko.runtime.api;

import dev.ko.annotations.PathParam;
import dev.ko.runtime.model.ApiEndpointModel;
import dev.ko.runtime.model.AppModel;
import dev.ko.runtime.model.ServiceModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Registers @KoAPI methods as Spring MVC HTTP endpoints at startup.
 * Uses a dispatcher approach: registers a handler method on this class,
 * which delegates to the actual service method via reflection.
 */
public class KoEndpointRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(KoEndpointRegistrar.class);
    private static final Pattern PATH_PARAM = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    private final RequestMappingHandlerMapping handlerMapping;
    private final ApplicationContext context;
    private final AppModel appModel;
    private final ObjectMapper objectMapper;

    public KoEndpointRegistrar(RequestMappingHandlerMapping handlerMapping,
                               ApplicationContext context,
                               AppModel appModel,
                               ObjectMapper objectMapper) {
        this.handlerMapping = handlerMapping;
        this.context = context;
        this.appModel = appModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (ServiceModel service : appModel.services()) {
            Object serviceBean = context.getBean(service.name());
            Class<?> serviceClass = serviceBean.getClass();

            for (ApiEndpointModel api : service.apis()) {
                try {
                    String springPath = convertPath(api.path());
                    RequestMethod requestMethod = RequestMethod.valueOf(api.method());
                    Method javaMethod = findMethod(serviceClass, api.name());

                    KoApiHandler handler = new KoApiHandler(serviceBean, javaMethod, objectMapper);

                    RequestMappingInfo mappingInfo = RequestMappingInfo
                            .paths(springPath)
                            .methods(requestMethod)
                            .produces(MediaType.APPLICATION_JSON_VALUE)
                            .build();

                    Method handleMethod = KoApiHandler.class.getMethod(
                            "handle", HttpServletRequest.class, Map.class);

                    handlerMapping.registerMapping(mappingInfo, handler, handleMethod);

                    log.info("Ko: Registered {} {} -> {}.{}()",
                            api.method(), springPath, service.name(), api.name());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to register endpoint "
                            + api.method() + " " + api.path() + " on " + service.name(), e);
                }
            }
        }
    }

    static String convertPath(String koPath) {
        return PATH_PARAM.matcher(koPath).replaceAll("{$1}");
    }

    private Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalStateException("Method not found: " + clazz.getName() + "." + name);
    }

    /**
     * A handler object whose "handle" method is registered with Spring MVC.
     * It adapts between Spring's request handling and the Ko service method.
     */
    public static class KoApiHandler {

        private final Object serviceBean;
        private final Method serviceMethod;
        private final ObjectMapper objectMapper;

        public KoApiHandler(Object serviceBean, Method serviceMethod, ObjectMapper objectMapper) {
            this.serviceBean = serviceBean;
            this.serviceMethod = serviceMethod;
            this.objectMapper = objectMapper;
        }

        @SuppressWarnings("unchecked")
        public ResponseEntity<Object> handle(
                HttpServletRequest request,
                @org.springframework.web.bind.annotation.PathVariable Map<String, String> pathVars
        ) throws Exception {
            Parameter[] params = serviceMethod.getParameters();
            Object[] args = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                PathParam pathParam = params[i].getAnnotation(PathParam.class);
                if (pathParam != null) {
                    args[i] = pathVars.get(pathParam.value());
                } else if (!BeanUtils.isSimpleProperty(params[i].getType())) {
                    args[i] = objectMapper.readValue(request.getInputStream(), params[i].getType());
                }
            }

            Object result = serviceMethod.invoke(serviceBean, args);
            return ResponseEntity.ok(result);
        }
    }
}
