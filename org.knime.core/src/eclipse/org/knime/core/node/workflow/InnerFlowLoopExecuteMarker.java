/*
 * ------------------------------------------------------------------------
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
 * ------------------------------------------------------------------------
 *
 * History
 *   Jun 7, 2010 (wiswedel): created
 */
package org.knime.core.node.workflow;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Control object on a {@link FlowObjectStack} to indicate the actual
 * execution of a loop start node. Objects of this class are removed (stack
 * pop operation) from the stack immediately before an execution starts in
 * order to remove any previously added flow variables. They are put onto
 * immediately following their removal to indicate an iteration start.
 *
 * Note the difference to the normal FlowLoopContext object which is put
 * onto the stack during configure! This object helps to keep the stack
 * clean during the multiple subsequent execute-calls.
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
public final class InnerFlowLoopExecuteMarker extends FlowObject {

    /** This field is "transient" and doesn't need to be saved as part of the workflow. Details see
     * {@link #getPropagatedVarsNames()}. */
    private List<String> m_propagatedVarsNames;

    InnerFlowLoopExecuteMarker() {
    }

    /**
     * @param propagatedVarsNames list of variables described in {@link #getPropagatedVarsNames()}.
     */
    void setPropagatedVarsNames(final List<String> propagatedVarsNames) {
        m_propagatedVarsNames = propagatedVarsNames;
    }

    /**
     * The names of variables that were passed back to a subsequent loop iteration when
     * {@link LoopEndNode#shouldPropagateModifiedVariables() variables are propagated}. These variables need to be
     * marked as "special" since in iteration 2++ they are 'owned' by the loop start node. However, variables attached
     * to start nodes are not propagated to downstream nodes (following the loop end) as per
     * {@link LoopEndNode#propagateModifiedVariables(org.knime.core.node.NodeModel)}.
     *
     * @return the propagatedVarsNames that list (non-null)
     */
    List<String> getPropagatedVarsNames() {
        return Objects.requireNonNullElse(m_propagatedVarsNames, Collections.emptyList());
    }

    // no functionality, only a marker object
    // @see FlowLoopContext for proper implementation
    // of hashCode and equals when members are added.

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        return super.equals(obj);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("<Inner Loop Context (#execute) - Owner: %s>", getOwner());
    }

}
