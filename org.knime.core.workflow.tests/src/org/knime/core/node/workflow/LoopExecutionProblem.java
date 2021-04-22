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
 */
package org.knime.core.node.workflow;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.knime.core.node.workflow.NativeNodeContainer.LoopStatus;

/**
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public class LoopExecutionProblem extends WorkflowTestCase {

	@Test
	public void test() throws Exception {
		loadAndSetWorkflow();
		WorkflowManager wfm = getManager();
		NativeNodeContainer node = (NativeNodeContainer) wfm.getNodeContainer(new NodeID(wfm.getID(), 4));
		wfm.executeUpToHere(node.getID());
		wfm.pauseLoopExecution(node);
		await().atMost(5, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
			assertThat(node.getLoopStatus(), is(LoopStatus.PAUSED));
		});
		wfm.cancelExecution(node);
	}

	@Test
	public void test2() throws Exception {
		NodeID wfId = loadAndSetWorkflow();
		WorkflowManager componentWfm = ((SubNodeContainer) getManager().getNodeContainer(new NodeID(wfId, 5)))
				.getWorkflowManager();
		NativeNodeContainer node = (NativeNodeContainer) componentWfm
				.getNodeContainer(new NodeID(componentWfm.getID(), 4));
		componentWfm.executeUpToHere(node.getID());
		componentWfm.pauseLoopExecution(node);
		await().atMost(5, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
			assertThat(node.getLoopStatus(), is(LoopStatus.PAUSED));
		});
		componentWfm.cancelExecution(node);
	}

}
