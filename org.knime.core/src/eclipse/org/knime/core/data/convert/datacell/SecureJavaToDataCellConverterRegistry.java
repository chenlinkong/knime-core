/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Apr 29, 2021 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.core.data.convert.datacell;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.knime.core.data.DataType;
import org.knime.core.node.NodeLogger;

/**
 * A secure registry for {@link JavaToDataCellConverterFactory JavaToDataCellConverterFactories}.<br>
 * In contrast to {@link JavaToDataCellConverterRegistry} this class does not blindly overwrite factories when another
 * factory with the same ID is provided. It also prioritizes factories provided by KNIME plugins over factories provided
 * by the community.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @noreference This class is not intended to be referenced by clients.
 */
public enum SecureJavaToDataCellConverterRegistry {
        /**
         * The singleton instance.
         */
        INSTANCE;

    // TODO use this class for save serialization of ProductionPaths

    /**
         *
         */
    private static final String DUPLICATE_KNIME_CONVERTER_TEMPLATE =
        "Duplicate JavaToDataCellConverter provided by KNIME detected: '%s' and '%s'. There should only ever be one such converter.";

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SecureJavaToDataCellConverterRegistry.class);

    private final Map<Class<?>, List<FactoryItem>> m_bySourceType = new HashMap<>();

    private final Map<DataType, List<FactoryItem>> m_byDestType = new HashMap<>();

    private final Map<String, List<FactoryItem>> m_byIdentifier = new HashMap<>();

    private SecureJavaToDataCellConverterRegistry() {
        final Collection<JavaToDataCellConverterFactory<?>> availableConverters =
            JavaToDataCellConverterRegistry.getInstance().getAllFactories();
        for (JavaToDataCellConverterFactory<?> factory : availableConverters) {
            register(factory);
        }
        m_bySourceType.values().forEach(l -> l.sort(Comparator.naturalOrder()));
        m_byDestType.values().forEach(l -> l.sort(Comparator.naturalOrder()));
        m_byIdentifier.values().forEach(l -> l.sort(Comparator.naturalOrder()));
    }

    private void register(final JavaToDataCellConverterFactory<?> factory) {
        final FactoryItem factoryItem = createFactoryItem(factory);
        checkForDuplicateKnimeConverters(factoryItem);
        m_bySourceType.computeIfAbsent(factory.getSourceType(), c -> new ArrayList<>()).add(factoryItem);
        m_byDestType.computeIfAbsent(factory.getDestinationType(), c -> new ArrayList<>()).add(factoryItem);
        m_byIdentifier.computeIfAbsent(factory.getIdentifier(), c -> new ArrayList<>()).add(factoryItem);
    }

    private void checkForDuplicateKnimeConverters(final FactoryItem factoryItem) {
        Origin origin = factoryItem.m_origin;
        final JavaToDataCellConverterFactory<?> factory = factoryItem.m_factory;
        if (origin.isKnime() && m_bySourceType.containsKey(factory.getSourceType())) {
            final DataType destinationType = factory.getDestinationType();
            m_bySourceType.get(factory.getSourceType()).stream()//
                .map(FactoryItem::getFactory).filter(f -> destinationType.equals(f.getDestinationType()))//
                .findFirst()//
                .ifPresent(f -> LOGGER.errorWithFormat(DUPLICATE_KNIME_CONVERTER_TEMPLATE, f.getIdentifier(),
                    factory.getIdentifier()));
        }
    }

    private static FactoryItem createFactoryItem(final JavaToDataCellConverterFactory<?> factory) {
        if (factory instanceof FactoryMethodToDataCellConverterFactory) {
            return createFactoryItemForAnnotationFactory((FactoryMethodToDataCellConverterFactory<?, ?>)factory);
        } else {
            return createFactoryItemForExtensionPointFactory(factory);
        }
    }

    private static FactoryItem
        createFactoryItemForAnnotationFactory(final FactoryMethodToDataCellConverterFactory<?, ?> factory) {
        return new FactoryItem(factory, Origin.forClass(factory.getDeclaringClass()));
    }

    private static FactoryItem
        createFactoryItemForExtensionPointFactory(final JavaToDataCellConverterFactory<?> factory) {
        return new FactoryItem(factory, Origin.forClass(factory.getClass()));
    }

    /**
     * Retrieves the factories registered under the provided identifier.<br>
     * The returned list is ordered by the origin of the factories, i.e. factories from org.knime.core come before
     * factories from some KNIME extension and community factories come last.
     *
     * @param identifier String id of the converter factory to retrieve
     * @return the factories registered under the provided identifier (the list is empty if the identifier is unknown)
     */
    public List<JavaToDataCellConverterFactory<?>> getConverterFactoriesByIdentifier(final String identifier) {
        return getConverterFactoriesByIdentifier(identifier, EnumSet.allOf(Origin.class));
    }

    /**
     * Retrieves the factories registered under the provided identifier from the provided {@link Origin origins}.<br>
     * The returned list is ordered by the origin of the factories, i.e. factories from org.knime.core come before
     * factories from some KNIME extension and community factories come last.
     *
     * @param identifier String id of the converter factory to retrieve
     * @param origin {@link Origin} of the factories to return
     * @param otherOrigins more origins to include
     * @return the factories registered under the provided identifier from the provided origins (the list is empty if
     *         the identifier is unknown or no such factory is provided by any of the origins)
     */
    public List<JavaToDataCellConverterFactory<?>> getConverterFactoriesByIdentifier(final String identifier,
        final Origin origin, final Origin... otherOrigins) {
        return getConverterFactoriesByIdentifier(identifier, EnumSet.of(origin, otherOrigins));
    }

    private List<JavaToDataCellConverterFactory<?>> getConverterFactoriesByIdentifier(final String identifier,
        final Set<Origin> origins) {
        return m_byIdentifier.getOrDefault(identifier, Collections.emptyList()).stream()//
            .filter(f -> origins.contains(f.m_origin))//
            .map(FactoryItem::getFactory)//
            .collect(toList());
    }

    /**
     * Retrieves the factories registered for the provided {@link DataType destinationType}.<br>
     * The returned list is ordered by the origin of the factories, i.e. factories from org.knime.core come before
     * factories from some KNIME extension and community factories come last.
     *
     * @param destinationType {@link DataType} for which to retrieve factories
     * @return the factories registered for the provided {@link DataType destinationType} (the list is empty if there
     *         are no factories for the destinationType)
     */
    public List<JavaToDataCellConverterFactory<?>>
        getConverterFactoriesByDestinationType(final DataType destinationType) {
        return getConverterFactoriesByDestinationType(destinationType, EnumSet.allOf(Origin.class));
    }

    /**
     * Retrieves the factories registered for the provided {@link DataType destinationType} from the provided
     * {@link Origin origins}.<br>
     * The returned list is ordered by the origin of the factories, i.e. factories from org.knime.core come before
     * factories from some KNIME extension and community factories come last.
     *
     * @param destinationType {@link DataType} for which to retrieve factories
     * @param origin {@link Origin} of the factories to return
     * @param otherOrigins more origins to include
     * @return the factories registered for the provided {@link DataType destinationType} from the provided origins (the
     *         list is empty if none of the origins provided a factory of destinationType)
     */
    public List<JavaToDataCellConverterFactory<?>> getConverterFactoriesByDestinationType(
        final DataType destinationType, final Origin origin, final Origin... otherOrigins) {
        return getConverterFactoriesByDestinationType(destinationType, EnumSet.of(origin, otherOrigins));
    }

    private List<JavaToDataCellConverterFactory<?>>
        getConverterFactoriesByDestinationType(final DataType destinationType, final Set<Origin> origins) {
        return m_byDestType.getOrDefault(destinationType, Collections.emptyList()).stream()//
            .filter(f -> origins.contains(f.m_origin))//
            .map(FactoryItem::getFactory)//
            .collect(toList());
    }

    /**
     * Retrieves the factories registered for the provided sourceType.<br>
     * The returned list is ordered by the origin of the factories, i.e. factories from org.knime.core come before
     * factories from some KNIME extension and community factories come last.
     *
     * @param sourceType for which to retrieve factories
     * @return the factories registered for the provided sourceType (the list is empty if there are no factories for the
     *         sourceType)
     */
    public <S> List<JavaToDataCellConverterFactory<S>> getConverterFactoriesBySourceType(final Class<S> sourceType) {
        return getConverterFactoriesBySourceType(sourceType, EnumSet.allOf(Origin.class));
    }

    /**
     * Retrieves the factories registered for the provided sourceType from the provided {@link Origin origins}.<br>
     * The returned list is ordered by the origin of the factories, i.e. factories from org.knime.core come before
     * factories from some KNIME extension and community factories come last.
     *
     * @param sourceType for which to retrieve factories
     * @param origin {@link Origin} of the factories to return
     * @param otherOrigins more origins to include
     * @return the factories registered for the provided sourceType from the provided origins (the list is empty if none
     *         of the origins provided a factory of sourceType)
     */
    public <S> List<JavaToDataCellConverterFactory<S>> getConverterFactoriesBySourceType(final Class<S> sourceType,
        final Origin origin, final Origin... otherOrigins) {
        return getConverterFactoriesBySourceType(sourceType, EnumSet.of(origin, otherOrigins));
    }

    @SuppressWarnings("unchecked") // the contract of JavaToDataCellConverterFactory ensures that the cast is safe
    private <S> List<JavaToDataCellConverterFactory<S>> getConverterFactoriesBySourceType(final Class<S> sourceType,
        final Set<Origin> origins) {
        return m_bySourceType.getOrDefault(sourceType, Collections.emptyList()).stream()//
            .filter(f -> origins.contains(f.m_origin))//
            .map(FactoryItem::getFactory)//
            .map(f -> (JavaToDataCellConverterFactory<S>)f)//
            .collect(toList());
    }

    /**
     * Classifies the origin of a {@link JavaToDataCellConverterFactory}.
     *
     * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
     */
    public enum Origin {
            /**
             * The factory was declared in KNIME core.
             */
            KNIME_CORE("org.knime.core.", true),
            /**
             * The factory was declared in a KNIME extension.
             */
            KNIME_EXTENSION("org.knime.", true),
            /**
             * The factory was declared in a community extension.
             */
            COMMUNITY("", false);

        private final String m_prefix;

        private final boolean m_isKnime;

        private Origin(final String prefix, final boolean isKnime) {
            m_prefix = prefix;
            m_isKnime = isKnime;
        }

        boolean isKnime() {
            return m_isKnime;
        }

        static Origin forClass(final Class<?> factoryClass) {
            final String name = factoryClass.getName();
            for (Origin origin : values()) {
                if (name.startsWith(origin.m_prefix)) {
                    return origin;
                }
            }
            throw new IllegalArgumentException(
                "The provided factory class is not attributable to any known origin: " + factoryClass);
        }
    }

    private static final class FactoryItem implements Comparable<FactoryItem> {

        private final JavaToDataCellConverterFactory<?> m_factory;

        private final Origin m_origin;

        FactoryItem(final JavaToDataCellConverterFactory<?> factory, final Origin origin) {
            m_origin = origin;
            m_factory = factory;
        }

        private JavaToDataCellConverterFactory<?> getFactory() {
            return m_factory;
        }

        @Override
        public int compareTo(final FactoryItem o) {
            return m_origin.compareTo(o.m_origin);
        }
    }
}
