package dev.ko.runtime.cron;

import dev.ko.annotations.KoCron;
import dev.ko.annotations.KoService;
import dev.ko.runtime.model.AppModel;
import dev.ko.runtime.model.CronJobModel;
import dev.ko.runtime.model.ServiceModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Discovers @KoCron methods on @KoService beans and schedules them.
 * Each job runs on a virtual thread. The scheduler computes the next
 * execution time from the cron expression after each run.
 */
public class KoCronScheduler implements SmartInitializingSingleton, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(KoCronScheduler.class);

    private final ApplicationContext context;
    private final AppModel appModel;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public KoCronScheduler(ApplicationContext context, AppModel appModel) {
        this.context = context;
        this.appModel = appModel;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (ServiceModel service : appModel.services()) {
            if (service.cronJobs() == null || service.cronJobs().isEmpty()) continue;

            Object serviceBean = context.getBean(service.name());
            Class<?> serviceClass = serviceBean.getClass();

            for (CronJobModel cronJob : service.cronJobs()) {
                Method method = findMethod(serviceClass, cronJob.method());
                CronExpression cron = new CronExpression(cronJob.schedule());

                scheduleNext(cronJob.name(), cron, serviceBean, method);

                log.info("Ko: Scheduled cron job '{}' [{}] -> {}.{}()",
                        cronJob.name(), cronJob.schedule(), service.name(), cronJob.method());
            }
        }
    }

    private void scheduleNext(String jobName, CronExpression cron, Object bean, Method method) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = cron.nextAfter(now);
        long delayMs = Duration.between(now, nextRun).toMillis();

        scheduler.schedule(() -> {
            Thread.ofVirtual().name("ko-cron-" + jobName).start(() -> {
                try {
                    log.info("Ko: Running cron job '{}'", jobName);
                    method.invoke(bean);
                    log.info("Ko: Cron job '{}' completed", jobName);
                } catch (Exception e) {
                    log.error("Ko: Cron job '{}' failed: {}", jobName, e.getMessage(), e);
                }
            });

            // Schedule the next run
            scheduleNext(jobName, cron, bean, method);
        }, delayMs, TimeUnit.MILLISECONDS);

        log.debug("Ko: Cron job '{}' next run at {} (in {}ms)", jobName, nextRun, delayMs);
    }

    private Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new IllegalStateException("Cron method not found: " + clazz.getName() + "." + name);
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
        log.info("Ko: Cron scheduler shut down");
    }
}
