/* @(#)$RCSfile$ 
 * $Revision: 280 $ $Date: 2006-02-21 17:39:37 +0100 (Di, 21 Feb 2006) $ $Author: sieb $
 * 
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 * 
 * Copyright, 2003 - 2006
 * Universitaet Konstanz, Germany.
 * Lehrstuhl fuer Angewandte Informatik
 * Prof. Dr. Michael R. Berthold
 * 
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner.
 * -------------------------------------------------------------------
 * 
 * History
 *   25.05.2005 (Florian Georg): created
 */
package de.unikn.knime.workbench.editor2.actions;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;

import de.unikn.knime.core.node.NodeLogger;
import de.unikn.knime.workbench.editor2.ImageRepository;
import de.unikn.knime.workbench.editor2.WorkflowEditor;
import de.unikn.knime.workbench.editor2.editparts.NodeContainerEditPart;

/**
 * Action to cancel all nodes that are running.
 * 
 * @author Christoph sieb, University of Konstanz
 */
public class CancelAllAction extends AbstractNodeAction {

    private static final NodeLogger LOGGER = NodeLogger
            .getLogger(CancelAllAction.class);

    /** unique ID for this action. * */
    public static final String ID = "knime.action.cancelall";

    /**
     * 
     * @param editor The workflow editor
     */
    public CancelAllAction(final WorkflowEditor editor) {
        super(editor);
    }

    /**
     * @see org.eclipse.jface.action.IAction#getId()
     */
    public String getId() {
        return ID;
    }

    /**
     * @see org.eclipse.jface.action.IAction#getText()
     */
    public String getText() {
        return "Cancel all";
    }

    /**
     * @see org.eclipse.jface.action.IAction#getImageDescriptor()
     */
    public ImageDescriptor getImageDescriptor() {
        return ImageRepository.getImageDescriptor("icons/executeAll.PNG");
    }

    /**
     * @see org.eclipse.jface.action.IAction#getToolTipText()
     */
    public String getToolTipText() {
        return "Cancel all running nodes.";
    }

    /**
     * @return <code>true</code>, if at least one node is running
     * @see org.eclipse.gef.ui.actions.WorkbenchPartAction#calculateEnabled()
     */
    protected boolean calculateEnabled() {
        if (getManager() == null) {
            return false;
        }
        return getManager().executionInProgress();
    }

    /**
     * This cancels all running jobs.
     * 
     * @see de.unikn.knime.workbench.editor2.actions.AbstractNodeAction
     *      #runOnNodes(de.unikn.knime.workbench.editor2.
     *      editparts.NodeContainerEditPart[])
     */
    public void runOnNodes(final NodeContainerEditPart[] nodeParts) {

        MessageBox mb = new MessageBox(Display.getDefault().getActiveShell(),
                SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
        mb.setText("Confirm cancel all...");
        mb.setMessage("Do you really want to cancel all running node(s) ?");
        if (mb.open() != SWT.YES) {
            return;
        }

        LOGGER.debug("(Cancel all)  cancel all running jobs.");
        getManager().cancelExecution();
    }
}
