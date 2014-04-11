/*
 * Copyright 2014, Mysema Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mysema.query.util;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Primitives;
import com.mysema.query.types.ExpressionException;
import com.mysema.util.ArrayUtils;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author Shredder121
 */
public class ConstructorUtils {

    /**
     * The parameter list for the default constructor;
     */
    private static final Class<?>[] NO_ARGS = {};

    private static final ClassToInstanceMap<Object> defaultPrimitives
            = ImmutableClassToInstanceMap.builder()
            .put(Boolean.TYPE, false)
            .put(Byte.TYPE, (byte) 0)
            .put(Character.TYPE, (char) 0)
            .put(Short.TYPE, (short) 0)
            .put(Integer.TYPE, 0)
            .put(Long.TYPE, 0L)
            .put(Float.TYPE, 0.0F)
            .put(Double.TYPE, 0.0)
            .build();

    /**
     * Returns the constructor where the formal parameter list matches the
     * givenTypes argument.
     *
     * It is advisable to first call
     * {@link #getConstructorParameters(java.lang.Class, java.lang.Class<?>[])}
     * to get the parameters.
     *
     * @param type
     * @param givenTypes
     * @return
     * @throws NoSuchMethodException
     */
    public static <C> Constructor<C> getConstructor(Class<C> type, Class<?>[] givenTypes) throws NoSuchMethodException {
        return type.getConstructor(givenTypes);
    }

    /**
     * Returns the parameters for the constructor that matches the given types.
     *
     * @param type
     * @param givenTypes
     * @return
     */
    public static Class<?>[] getConstructorParameters(Class<?> type, Class<?>[] givenTypes) {
        next_constructor:
        for (Constructor<?> constructor : type.getConstructors()) {
            int matches = 0;
            Class<?>[] parameters = constructor.getParameterTypes();
            Iterator<Class<?>> parameterIterator = Arrays
                    .asList(parameters)
                    .iterator();
            if (!ArrayUtils.isEmpty(givenTypes)
                    && !ArrayUtils.isEmpty(parameters)) {
                Class<?> parameter = null;
                for (Class<?> argument : givenTypes) {

                    if (parameterIterator.hasNext()) {
                        parameter = parameterIterator.next();
                        if (!compatible(parameter, argument)) {
                            continue next_constructor;
                        }
                        matches++;
                    } else if (constructor.isVarArgs()) {
                        if (!compatible(parameter, argument)) {
                            continue next_constructor;
                        }
                    } else {
                        continue next_constructor; //default
                    }
                }
                if (matches == parameters.length) {
                    return parameters;
                }
            } else if (ArrayUtils.isEmpty(givenTypes)
                    && ArrayUtils.isEmpty(parameters)) {
                return NO_ARGS;
            }
        }
        throw new ExpressionException("No constructor found for " + type.toString()
                + " with parameters: " + Arrays.toString(givenTypes));
    }

    /**
     * Returns a list of transformers applicable to the given constructor.
     *
     * @param constructor
     * @return
     */
    public static Collection<? extends Function<Object[], Object[]>> getTransformers(Constructor<?> constructor) {
        ArrayList<ConstructorArgumentTransformer> transformers = Lists.newArrayList(
                new VarArgsTransformer(constructor),
                new NullSafePrimitiveTransformer(constructor));

        return Collections2
                .filter(transformers, applicableFilter);
    }

    private static Class<?> normalize(Class<?> clazz) {
        if (clazz.isArray()) {
            clazz = clazz.getComponentType();
        }
        return Primitives.wrap(clazz);
    }

    private static boolean compatible(Class<?> parameter, Class<?> argument) {
        return normalize(parameter)
                .isAssignableFrom(normalize(argument));
    }

    private static final Predicate<ConstructorArgumentTransformer> applicableFilter
            = new Predicate<ConstructorArgumentTransformer>() {

                @Override
                public boolean apply(ConstructorArgumentTransformer transformer) {
                    return transformer != null ? transformer.isApplicable() : false;
                }
            };

    protected static abstract class ConstructorArgumentTransformer implements Function<Object[], Object[]> {

        protected final Constructor<?> constructor;

        public ConstructorArgumentTransformer(Constructor<?> constructor) {
            this.constructor = constructor;
        }

        public abstract boolean isApplicable();
    }

    private static class VarArgsTransformer extends ConstructorArgumentTransformer {

        private final Class<?>[] paramTypes;
        private final Class<?> componentType;

        private VarArgsTransformer(Constructor<?> constructor) {
            super(constructor);

            paramTypes = constructor.getParameterTypes();
            if (paramTypes.length > 0) {
                componentType = paramTypes[paramTypes.length - 1].getComponentType();

            } else {
                componentType = null;
            }
        }

        @Override
        public boolean isApplicable() {
            return constructor.isVarArgs();
        }

        @Override
        public Object[] apply(Object[] args) {
            Iterator<Object> iterator = Arrays
                    .asList(args)
                    .iterator();

            // constructor args
            Object[] cargs = new Object[paramTypes.length];
            for (int i = 0; i < cargs.length - 1; i++) {
                Array.set(cargs, i, iterator.next());
            }
            // array with vargs
            int size = args.length - cargs.length + 1;
            Object vargs = Array.newInstance(
                    componentType, size);
            cargs[cargs.length - 1] = vargs;
            for (int i = 0; i < Array.getLength(vargs); i++) {
                Array.set(vargs, i, iterator.next());
            }
            return cargs;
        }

    }

    private static class NullSafePrimitiveTransformer extends ConstructorArgumentTransformer {

        private final Map<Integer, Class<?>> primitiveLocations = Maps.newLinkedHashMap();

        private NullSafePrimitiveTransformer(Constructor<?> constructor) {
            super(constructor);

            Class<?>[] parameterTypes = constructor.getParameterTypes();
            for (int location = 0; location < parameterTypes.length; location++) {
                Class<?> parameterType = parameterTypes[location];

                if (parameterType.isPrimitive()) {
                    primitiveLocations.put(location, parameterType);
                }
            }
        }

        @Override
        public boolean isApplicable() {
            return !primitiveLocations.isEmpty();
        }

        @Override
        public Object[] apply(Object[] args) {
            for (Map.Entry<Integer, Class<?>> primitiveEntry : primitiveLocations.entrySet()) {
                Integer location = primitiveEntry.getKey();
                if (args[location] == null) {
                    Class<?> primitiveClass = primitiveEntry.getValue();
                    args[location] = defaultPrimitives.getInstance(primitiveClass);
                }
            }
            return args;
        }

    }

}
