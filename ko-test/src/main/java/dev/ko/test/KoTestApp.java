package dev.ko.test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class as a Ko integration test.
 * Bootstraps a Spring Boot test context with Ko auto-configuration,
 * in-memory providers, and test utilities.
 *
 * <pre>{@code
 * @KoTestApp
 * class GreetingServiceTest {
 *
 *     @Autowired
 *     private TestPubSub testPubSub;
 *
 *     @Autowired
 *     private TestDatabase testDatabase;
 *
 *     @Test
 *     void createGreeting_publishesEvent() {
 *         // call API...
 *         assertThat(testPubSub.published("greeting-events")).hasSize(1);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(KoTestExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public @interface KoTestApp {
}
