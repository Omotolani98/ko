package dev.ko.processor.scanner;

import dev.ko.annotations.KoCron;
import dev.ko.processor.model.CronJobModel;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

public class CronScanner {

    public List<CronJobModel> scan(TypeElement serviceElement) {
        List<CronJobModel> cronJobs = new ArrayList<>();

        for (var enclosed : serviceElement.getEnclosedElements()) {
            KoCron cronAnnotation = enclosed.getAnnotation(KoCron.class);
            if (cronAnnotation == null) {
                continue;
            }

            ExecutableElement method = (ExecutableElement) enclosed;
            String name = cronAnnotation.name().isEmpty()
                    ? method.getSimpleName().toString()
                    : cronAnnotation.name();

            cronJobs.add(new CronJobModel(
                    name,
                    cronAnnotation.schedule(),
                    method.getSimpleName().toString()
            ));
        }

        return cronJobs;
    }
}
