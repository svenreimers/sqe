/* Copyright 2005,2006 Sven Reimers, Florian Vogler
 *
 * This file is part of the Software Quality Environment Project.
 *
 * The Software Quality Environment Project is free software:
 * you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation,
 * either version 2 of the License, or (at your option) any later version.
 *
 * The Software Quality Environment Project is distributed in the hope that
 * it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nbheaven.sqe.tools.findbugs.codedefects.core.ui.result;

import edu.umd.cs.findbugs.BugAnnotation;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.ClassAnnotation;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.MethodAnnotation;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import java.awt.EventQueue;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.nbheaven.sqe.tools.findbugs.codedefects.core.annotations.BugAnnotationProcessor;
import org.nbheaven.sqe.tools.findbugs.codedefects.core.FindBugsResult.Mode;
import org.nbheaven.sqe.tools.findbugs.codedefects.core.FindBugsSession;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

class BugTree extends JTree {

    public static final String PROPERTY_ACTIVE_MODE = "ActiveMode";
    public static final String PROPERTY_CORE_FILTER_ENABLED = "CoreFilterEnabled";
    private final RequestProcessor requestProcessor;
    private final FindBugsSession session;
    private Mode resultMode = Mode.TYPE;
    private boolean isCollapsed = true;
    private boolean coreFilterEnabled = true;

    BugTree(FindBugsSession session) {
        this.session = session;
        this.requestProcessor = new RequestProcessor("BugTree-" + session.getDisplayName(), 1);
        setCellRenderer(BugCellRenderer.instance());
        addMouseListener(new JumpToSourceMouseListener());
        addKeyListener(new JumpToSourceKeyListener());
        setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No result available")));

        // XX must be weak ?
        this.session.getResultProperty().addListener((observable, oldValue, newValue) -> refresh());
    }

    public FindBugsSession getSession() {
        return session;
    }

    public boolean isActiveMode(Mode mode) {
        return this.resultMode == mode;
    }

    public void setActiveMode(Mode mode) {
        Mode oldMode = this.resultMode;
        this.resultMode = mode;
        refresh();
        firePropertyChange(PROPERTY_ACTIVE_MODE, oldMode, this.resultMode);
    }

    public boolean isCoreFilterEnabled() {
        return this.coreFilterEnabled;
    }

    public void setCoreFilterEnabled(boolean coreFilterEnabled) {
        boolean oldCoreFilterEnabled = this.coreFilterEnabled;
        this.coreFilterEnabled = coreFilterEnabled;
        refresh();
        firePropertyChange(PROPERTY_CORE_FILTER_ENABLED, oldCoreFilterEnabled, this.coreFilterEnabled);
    }

    public void refresh() {
        requestProcessor.post(new Runnable() {
            @Override
            public void run() {
                final TreeNode rootNode = createRootTreeNode(session, coreFilterEnabled, resultMode);
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        setModel(new DefaultTreeModel(rootNode));
                        if (isCollapsed) {
                            collapseAll();
                        } else {
                            expandAll();
                        }
                    }
                });
            }
        });
    }

    public void collapseAll() {
        int row = getRowCount() - 1;
        while (row > 0) {
            collapseRow(row);
            row--;
        }
    }

    public void expandAll() {
        int row = getRowCount() - 1;
        while (row > 0) {
            expandRow(row);
            row--;
        }
    }

    private static TreeNode createRootTreeNode(FindBugsSession session, boolean coreFilterEnabled, Mode resultMode) {
        if (null == session || null == session.getResult()) {
            return new DefaultMutableTreeNode("No result available");
        }

        MutableTreeNode rootNode = new SessionNode(session, session.getResult().getCodeDefectCount(coreFilterEnabled));
        rootNode.setUserObject(session);

        Map<?, Collection<BugInstance>> instances = resultMode.getInstanceList(session.getResult(), coreFilterEnabled);
        int typeIndex = 0;
        for (Map.Entry<?, Collection<BugInstance>> entry : instances.entrySet()) {
            // Do not display nodes with empty children list
            if (entry.getValue().isEmpty()) {
                continue;
            }
            MutableTreeNode typeNode = new BugGroupNode(entry.getKey(), entry.getValue().size());
            int index = 0;
            for (BugInstance bugInstance : entry.getValue()) {
                BugInstanceNode bugInstanceNode = new BugInstanceNode(bugInstance, true);
                typeNode.insert(bugInstanceNode, index);
                index++;
                int annotationIndex = 0;
                for (Iterator<BugAnnotation> it = bugInstance.annotationIterator(); it.hasNext();) {
                    BugAnnotation annotation = it.next();
                    bugInstanceNode.insert(new BugAnnotationNode(annotation), annotationIndex);
                    annotationIndex++;
                }
            }
            rootNode.insert(typeNode, typeIndex);
            typeIndex++;
        }
        return rootNode;
    }

    @Override
    public JPopupMenu getComponentPopupMenu() {
        TreePath treePath = getSelectionModel().getSelectionPath();
        if (null != treePath && treePath.getPathCount() > 0) {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treePath.getLastPathComponent();
            Object obj = selectedNode.getUserObject();
            if (obj instanceof BugInstance) {
                Action disableDetector = new DisableDetectorAction((BugInstance) obj, session.getProject());
                Action filterBugPattern = new FilterBugPattern((BugInstance) obj);
                return Utilities.actionsToPopup(new Action[]{disableDetector, filterBugPattern}, Utilities.actionsGlobalContext());
            }
        }
        return null;
    }

    /**
     * Dispatch the event to open the source file according to type of
     * annotation
     */
    private static void jumpToSource(final TreePath treePath) {
        if (null == treePath || treePath.getPathCount() == 0) {
            return;
        }
        if (EventQueue.isDispatchThread()) {
            RequestProcessor.getDefault().post(new Runnable() {

                @Override
                public void run() {
                    jumpToSource(treePath);
                }
            });
            return;
        }

        FindBugsSession session = (FindBugsSession) ((DefaultMutableTreeNode) treePath.getPathComponent(0)).getUserObject();
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        Object obj = selectedNode.getUserObject();
        if (obj instanceof SourceLineAnnotation) {
            BugInstance bugInstance = (BugInstance) ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
            BugAnnotationProcessor.openSourceFile(bugInstance, (SourceLineAnnotation) obj, session.getProject());
        } else if (obj instanceof ClassAnnotation) {
            BugInstance bugInstance = (BugInstance) ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
            BugAnnotationProcessor.openSourceFile(bugInstance, (ClassAnnotation) obj, session.getProject());
        } else if (obj instanceof MethodAnnotation) {
            BugInstance bugInstance = (BugInstance) ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
            BugAnnotationProcessor.openSourceFile(bugInstance, (MethodAnnotation) obj, session.getProject());
        } else if (obj instanceof FieldAnnotation) {
            BugInstance bugInstance = (BugInstance) ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
            BugAnnotationProcessor.openSourceFile(bugInstance, (FieldAnnotation) obj, session.getProject());
        } else if (obj instanceof BugInstance) {
            BugInstance bugInstance = (BugInstance) obj;
            BugAnnotationProcessor.openSourceFile(bugInstance, session.getProject());
        }

    }

    private static class JumpToSourceMouseListener extends MouseAdapter {

        @Override
        public void mouseClicked(MouseEvent evt) {
            if (2 == evt.getClickCount()) {
                JTree jTree = (JTree) evt.getSource();
                jumpToSource(jTree.getSelectionPath());
            }
        }
    }

    private static class JumpToSourceKeyListener extends KeyAdapter {

        @Override
        public void keyPressed(KeyEvent keyEvent) {
            if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
                JTree jTree = (JTree) keyEvent.getSource();
                jumpToSource(jTree.getSelectionPath());
            }
        }

        @Override
        public void keyTyped(KeyEvent keyEvent) {
            if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
                JTree jTree = (JTree) keyEvent.getSource();
                jumpToSource(jTree.getSelectionPath());
            }
        }
    }
}
