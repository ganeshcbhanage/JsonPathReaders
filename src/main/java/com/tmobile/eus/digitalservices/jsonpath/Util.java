package com.tmobile.eus.digitalservices.jsonpath;

import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Set;

public class Util {

    public static void initialize(Object object, Set<String> packages)
            throws IllegalArgumentException,
            IllegalAccessException {
        Field[] fields = object.getClass().getDeclaredFields();

        for (Field field : fields) {
            String fieldName = field.getName();
            Class<?> fieldClass = field.getType();

            // skip primitives
            if (fieldClass.isPrimitive()) {
                System.out.println("Skipping primitive: " + fieldName);
                continue;
            }

            // skip if not in packages
            boolean inPackage = false;
            for (String pack : packages) {
                if (fieldClass.getPackage() != null && fieldClass.getPackage().getName().startsWith(pack)) {
                    inPackage = true;
                }
            }
            if (!inPackage) {
                String packageName = (fieldClass.getPackage() != null) ? fieldClass.getPackage().getName() : fieldClass.getName();
                System.out.println("Skipping package: "
                        + packageName);
                continue;
            }

            // allow access to private fields
            boolean isAccessible = field.isAccessible();
            field.setAccessible(true);

            Object fieldValue = field.get(object);
            if (fieldValue == null) {
                System.out.println("Initializing: " + fieldName);
                try {
                    if (fieldClass.isInterface() && fieldClass.getName().equals("java.util.List")) {
//                        Type actualTypeArgument = ((ParameterizedTypeImpl) field.getGenericType()).getActualTypeArguments()[0];
                        field.set(object, new ArrayList<>());
                    } else {
                        field.set(object, fieldClass.newInstance());
                    }
                } catch (IllegalArgumentException | IllegalAccessException
                        | InstantiationException e) {
                    System.err.println("Could not initialize "
                            + fieldClass.getSimpleName());
                }
            } else {
                System.out
                        .println("Field is already initialized: " + fieldName);
            }

            fieldValue = field.get(object);

            // reset accessible
            field.setAccessible(isAccessible);

            // recursive call for sub-objects
            initialize(fieldValue, packages);
        }

    }
}
