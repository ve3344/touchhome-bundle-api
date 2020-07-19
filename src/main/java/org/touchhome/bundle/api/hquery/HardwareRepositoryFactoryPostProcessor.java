package org.touchhome.bundle.api.hquery;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.thavam.util.concurrent.blockingMap.BlockingHashMap;
import org.thavam.util.concurrent.blockingMap.BlockingMap;
import org.touchhome.bundle.api.hquery.api.*;
import org.touchhome.bundle.api.util.SpringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Log4j2
public class HardwareRepositoryFactoryPostProcessor implements BeanFactoryPostProcessor {

    private static final BlockingQueue<PrintContext> printLogsJobs = new LinkedBlockingQueue<>(10);
    private static final BlockingMap<Process, List<String>> inputResponse = new BlockingHashMap<>();
    private static final Constructor<MethodHandles.Lookup> lookupConstructor;
    private static final Map<String, ProcessCache> cache = new HashMap<>();
    private static Thread logThread = new Thread(() -> {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                PrintContext printContext = printLogsJobs.take();
                Process process = printContext.process;

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info(line);
                        lines.add(line);
                    }
                    inputResponse.put(process, lines);
                }
            } catch (Exception ex) {
                log.error("Hardware error occurs while take print log job");
            }
        }
    }, "Hardware thread log");

    static {
        try {
            lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
            lookupConstructor.setAccessible(true);
        } catch (Exception ex) {
            throw new RuntimeException("Unable to instantiate MethodHandles.Lookup", ex);
        }
    }

    static {
        logThread.start();
    }

    private final String basePackages;
    private HardwareRepositoryFactoryPostHandler handler;
    private String pm = "apt";

    HardwareRepositoryFactoryPostProcessor(String basePackages, HardwareRepositoryFactoryPostHandler handler) {
        this.basePackages = basePackages;
        this.handler = handler;
    }

    @Override
    @SneakyThrows
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        Environment env = beanFactory.getBean(Environment.class);
        List<Class<?>> classes = getClassesWithAnnotation();
        for (Class<?> aClass : classes) {
            beanFactory.registerSingleton(aClass.getSimpleName(), Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{aClass}, (proxy, method, args) -> {
                List<Object> results = null;
                for (HardwareQuery hardwareQuery : method.getDeclaredAnnotationsByType(HardwareQuery.class)) {
                    if (results == null) {
                        results = new ArrayList<>();
                    }
                    results.add(handleHardwareQuery(hardwareQuery, args, method, env));
                }
                if (results != null) {
                    if (results.isEmpty()) {
                        return null;
                    } else if (results.size() == 1) {
                        return results.iterator().next();
                    } else if (method.getReturnType().isAssignableFrom(List.class)) {
                        return results;
                    } else {
                        return null;
                    }
                }

                if (method.isDefault()) {
                    return lookupConstructor.newInstance(aClass)
                            .in(aClass)
                            .unreflectSpecial(method, aClass)
                            .bindTo(proxy)
                            .invokeWithArguments(args);
                }
                throw new RuntimeException("Unable to execute hardware method without implementation");
            }));
        }
        if (this.handler != null) {
            this.handler.accept(beanFactory);
        }
    }

    private String replaceStringWithArgs(String str, Object[] args, Method method) {
        if (args != null) {
            Annotation[][] apiParams = method.getParameterAnnotations();
            for (int i = 0; i < args.length; i++) {
                String regexp = null;
                Object arg = args[i];
                if (apiParams.length > i) {
                    regexp = ((HQueryParam) apiParams[i][0]).value();
                }

                str = str.replaceAll(regexp == null ? ":([^\\s]+)" : ":" + regexp, String.valueOf(arg));
            }
        }
        return str;
    }

    private Object handleHardwareQuery(HardwareQuery hardwareQuery, Object[] args, Method method, Environment env) throws InstantiationException, IllegalAccessException {
        ErrorsHandler errorsHandler = method.getAnnotation(ErrorsHandler.class);
        List<String> parts = new ArrayList<>();
        for (String cmd : hardwareQuery.value()) {
            String argCmd = replaceStringWithArgs(cmd, args, method);
            String envCmd = SpringUtils.replaceEnvValues(argCmd, env::getProperty);
            String partCmd = envCmd.contains("$PM") ? envCmd.replace("$PM", pm) : envCmd;
            parts.add(partCmd);
        }
        String[] cmdParts = parts.toArray(new String[0]);
        String command = String.join(", ", parts);
        ProcessCache processCache;
        if (hardwareQuery.cache() && cache.containsKey(command)) {
            processCache = cache.get(command);
        } else {
            processCache = new ProcessCache();
            if (!StringUtils.isEmpty(hardwareQuery.echo())) {
                log.info("Execute: <{}>. Command: <{}>", hardwareQuery.echo(), command);
            } else {
                log.info("Execute command: <{}>", command);
            }
            Process process;
            try {
                if (!StringUtils.isEmpty(hardwareQuery.dir())) {
                    File dir = new File(replaceStringWithArgs(hardwareQuery.dir(), args, method));
                    process = Runtime.getRuntime().exec(cmdParts, null, dir);
                } else if (cmdParts.length == 1) {
                    process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmdParts[0]});
                } else {
                    process = Runtime.getRuntime().exec(cmdParts);
                }

                if (hardwareQuery.printOutput()) {
                    printLogsJobs.add(new PrintContext(process));
                }

                process.waitFor(hardwareQuery.maxSecondsTimeout(), TimeUnit.SECONDS);
                processCache.retValue = process.exitValue();
                processCache.errors = IOUtils.readLines(process.getErrorStream());
                processCache.inputs = hardwareQuery.printOutput() ? inputResponse.take(process) : IOUtils.readLines(process.getInputStream());
            } catch (Exception ex) {
                processCache.retValue = 1;
                processCache.errors = Collections.singletonList(ex.getMessage());
            } finally {
                if (hardwareQuery.cache()) {
                    cache.put(command, processCache);
                }
            }
        }

        return handleCommandResult(hardwareQuery, method, errorsHandler, command, processCache.retValue, processCache.inputs, processCache.errors);
    }

    private Object handleCommandResult(HardwareQuery hardwareQuery, Method method, ErrorsHandler errorsHandler, String command, int retValue, List<String> inputs, List<String> errors) throws IllegalAccessException, InstantiationException {
        Class<?> returnType = method.getReturnType();

        // in case we expect return num we ignore any errors
        if (returnType.isPrimitive()) {
            switch (returnType.getName()) {
                case "int":
                    return retValue;
                case "boolean":
                    return retValue == 0;
            }
        }

        if (retValue != 0) {
            throwErrors(errorsHandler, errors);
            if (errorsHandler != null) {
                String error = errors.isEmpty() ? errorsHandler.onRetCodeError() : String.join("; ", errors);
                if (errorsHandler.logError()) {
                    log.error(error);
                }
                if (errorsHandler.throwError()) {
                    throw new IllegalStateException(error);
                }
            } else {
                log.error("Error while execute command <{}>. Code: <{}>, Msg: <{}>", command, retValue, String.join(", ", errors));
                if (!hardwareQuery.ignoreOnError()) {
                    throw new HardwareException(errors, retValue);
                }
            }
        } else {
            for (String error : errors) {
                if (!error.isEmpty()) {
                    log.warn("Error <{}>", error);
                }
            }
            inputs = inputs.stream().map(String::trim).collect(Collectors.toList());
            ListParse listParse = method.getAnnotation(ListParse.class);
            ListParse.LineParse lineParse = method.getAnnotation(ListParse.LineParse.class);
            ListParse.BooleanLineParse booleanParse = method.getAnnotation(ListParse.BooleanLineParse.class);
            ListParse.LineParsers lineParsers = method.getAnnotation(ListParse.LineParsers.class);
            if (listParse != null) {
                String delimiter = listParse.delimiter();
                List<List<String>> buckets = new ArrayList<>();
                List<String> currentBucket = null;

                for (String input : inputs) {
                    if (input.matches(delimiter)) {
                        currentBucket = new ArrayList<>();
                        buckets.add(currentBucket);
                    }
                    if (currentBucket != null) {
                        currentBucket.add(input);
                    }
                }
                Class<?> genericClass = listParse.clazz();
                List<Object> result = new ArrayList<>();
                for (List<String> bucket : buckets) {
                    result.add(handleBucket(bucket, genericClass));
                }
                return result;
            } else if (lineParse != null) {
                return handleBucket(inputs, lineParse, null);
            } else if (lineParsers != null) {
                return handleBucket(inputs, lineParsers);
            } else if (booleanParse != null) {
                return handleBucket(inputs, booleanParse);
            } else {
                return handleBucket(inputs, returnType);
            }
        }
        return null;
    }

    private Object handleBucket(List<String> input, Class<?> genericClass) throws IllegalAccessException, InstantiationException {
        if (genericClass.isPrimitive()) {
            switch (genericClass.getName()) {
                case "void":
                    return null;
            }
        }
        Object obj = genericClass.newInstance();

        boolean handleFields = false;

        SplitParse splitParse = genericClass.getDeclaredAnnotation(SplitParse.class);
        if (splitParse != null) {
            for (String item : input) {
                String[] split = item.split(splitParse.value());
                for (Field field : FieldUtils.getFieldsListWithAnnotation(genericClass, SplitParse.SplitParseIndex.class)) {
                    int splitIndex = field.getDeclaredAnnotation(SplitParse.SplitParseIndex.class).index();
                    if (splitIndex >= 0 && splitIndex < split.length) {
                        String value = split[splitIndex].trim();
                        FieldUtils.writeField(field, obj, handleType(value, field.getType()), true);
                        handleFields = true;
                    }
                }
            }
        }

        for (Field field : FieldUtils.getFieldsListWithAnnotation(genericClass, ListParse.LineParse.class)) {
            Object value = handleBucket(input, field.getDeclaredAnnotation(ListParse.LineParse.class), field);
            FieldUtils.writeField(field, obj, value, true);
            handleFields = true;
        }

        for (Field field : FieldUtils.getFieldsListWithAnnotation(genericClass, ListParse.BooleanLineParse.class)) {
            Object value = handleBucket(input, field.getDeclaredAnnotation(ListParse.BooleanLineParse.class));
            FieldUtils.writeField(field, obj, value, true);
            handleFields = true;
        }

        List<Field> listFields = FieldUtils.getFieldsListWithAnnotation(genericClass, ListParse.LineParsers.class);
        for (Field field : listFields) {
            ListParse.LineParsers lineParsers = field.getDeclaredAnnotation(ListParse.LineParsers.class);
            Object value = handleBucket(input, lineParsers);
            FieldUtils.writeField(field, obj, value, true);
        }

        if (!handleFields && listFields.isEmpty()) {
            if (genericClass.isAssignableFrom(String.class)) {
                return String.join("", input);
            }
        }

        return obj;
    }

    private Object handleBucket(List<String> inputs, ListParse.LineParse lineParse, Field field) {
        for (String input : inputs) {
            if (input.matches(lineParse.value())) {
                String group = findGroup(input, lineParse.value(), lineParse.group());
                if (group != null) {
                    return handleType(group, field.getType());
                }
            }
        }
        return null;
    }

    private Object handleType(String value, Class<?> type) {
        if (type.isAssignableFrom(Integer.class)) {
            return new Integer(value);
        } else if (type.isAssignableFrom(Double.class)) {
            return new Double(value);
        }

        return value;
    }

    private Object handleBucket(List<String> inputs, ListParse.BooleanLineParse lineParse) {
        for (String input : inputs) {
            if (input.matches(lineParse.value())) {
                String group = findGroup(input, lineParse.value(), lineParse.group());
                if (group != null) {
                    if (group.equals(lineParse.when())) {
                        return !lineParse.inverse();
                    }
                }
            }
        }
        if (!lineParse.when().isEmpty()) {
            return lineParse.inverse();
        }

        return null;
    }

    private Object handleBucket(List<String> inputs, ListParse.LineParsers lineParsers) {
        for (ListParse.LineParse lineParse : lineParsers.value()) {
            Object val = handleBucket(inputs, lineParse, null);
            if (val != null) {
                return val;
            }
        }
        return null;
    }

    private String findGroup(String input, String regexp, int group) {
        Matcher m = Pattern.compile(regexp).matcher(input);
        if (m.find()) {
            return m.group(group);
        }
        return null;
    }

    private void throwErrors(ErrorsHandler errorsHandler, List<String> errors) {
        if (!errors.isEmpty() && errorsHandler != null) {
            for (ErrorsHandler.ErrorHandler errorHandler : errorsHandler.errorHandlers()) {
                if (errors.contains(errorHandler.onError())) {
                    throw new IllegalStateException(errorHandler.throwError());
                }
            }
        }
    }

    private <T> List<Class<? extends T>> getClassesWithAnnotation() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };

        scanner.addIncludeFilter(new AnnotationTypeFilter(HardwareRepositoryAnnotation.class));
        List<Class<? extends T>> foundClasses = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(basePackages)) {
            try {
                foundClasses.add((Class<? extends T>) Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return foundClasses;
    }

    private static class ProcessCache {
        int retValue;
        List<String> errors;
        List<String> inputs;
    }

    @AllArgsConstructor
    private static class PrintContext {
        private Process process;
    }
}
