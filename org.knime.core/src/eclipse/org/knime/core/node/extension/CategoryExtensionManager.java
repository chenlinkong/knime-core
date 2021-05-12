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
 *   Mar 19, 2021 (hornm): created
 */
package org.knime.core.node.extension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;

/**
 * A singleton class to collect node categories from the respective extension point.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 * @noreference This class is not intended to be referenced by clients.
 */
public final class CategoryExtensionManager {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(CategoryExtensionManager.class);

    /** ID of the "category" extension point */
    private static final String ID_CATEGORY = "org.knime.workbench.repository.categories";

    private static CategoryExtensionManager instance;

    /**
     * Map from the complete category path to the associated {@link CategoryExtension}.
     */
    private Map<String, CategoryExtension> m_categoryExtensions;

    /**
     * @return the singleton instance
     */
    public static synchronized CategoryExtensionManager getInstance() {
        if (instance == null) {
            instance = new CategoryExtensionManager();
            instance.collectCategoryExtensions();
        }
        return instance;
    }

    private CategoryExtensionManager() {
        // singleton
    }

    private void collectCategoryExtensions() {
        IExtensionRegistry registry = Platform.getExtensionRegistry();
        IExtensionPoint point = registry.getExtensionPoint(ID_CATEGORY);
        CheckUtils.checkState(point != null, "Invalid extension point: %s", ID_CATEGORY);
        @SuppressWarnings("null")
        Iterator<IConfigurationElement> it = Arrays.stream(point.getExtensions())//
            .flatMap(ext -> Stream.of(ext.getConfigurationElements())).iterator();
        Map<String, CategoryExtension> categoryExtensions = new HashMap<>();
        while (it.hasNext()) {
            try {
                CategoryExtension categoryExtension = new CategoryExtension(it.next());
                categoryExtensions.put(categoryExtension.getCompletePath(), categoryExtension);
            } catch (IllegalArgumentException iae) {
                LOGGER.error(iae.getMessage(), iae);
            }
        }
        m_categoryExtensions = Collections.unmodifiableMap(categoryExtensions);
    }

    /**
     * @return the category extensions as a map from the complete category path to the respective
     *         {@link CategoryExtension}
     */
    public Map<String, CategoryExtension> getCategoryExtensions() {
        return m_categoryExtensions;
    }

}
