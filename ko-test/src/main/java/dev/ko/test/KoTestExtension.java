package dev.ko.test;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit 5 extension for Ko integration tests.
 * Resets test utilities (TestPubSub, TestDatabase) between tests
 * to ensure test isolation.
 */
public class KoTestExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        var appContext = SpringExtension.getApplicationContext(context);

        // Reset published messages before each test
        if (appContext.containsBean("testPubSub")) {
            appContext.getBean(TestPubSub.class).reset();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // Future: clean up test database state, reset mock service clients, etc.
    }
}
