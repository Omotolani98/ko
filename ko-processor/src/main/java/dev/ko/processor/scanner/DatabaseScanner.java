package dev.ko.processor.scanner;

import dev.ko.annotations.KoDatabase;
import dev.ko.processor.model.DatabaseModel;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseScanner {

    public List<DatabaseModel> scan(TypeElement serviceElement) {
        List<DatabaseModel> databases = new ArrayList<>();

        for (var enclosed : serviceElement.getEnclosedElements()) {
            KoDatabase dbAnnotation = enclosed.getAnnotation(KoDatabase.class);
            if (dbAnnotation == null) {
                continue;
            }

            databases.add(new DatabaseModel(
                    dbAnnotation.name(),
                    dbAnnotation.migrations()
            ));
        }

        return databases;
    }
}
