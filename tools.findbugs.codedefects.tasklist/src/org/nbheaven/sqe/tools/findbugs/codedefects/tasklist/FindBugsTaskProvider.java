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
package org.nbheaven.sqe.tools.findbugs.codedefects.tasklist;

import edu.umd.cs.findbugs.BugInstance;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.nbheaven.sqe.codedefects.core.util.SQECodedefectSupport;
import org.nbheaven.sqe.tools.findbugs.codedefects.core.FindBugsResult;
import org.nbheaven.sqe.tools.findbugs.codedefects.core.FindBugsSession;
import org.nbheaven.sqe.core.java.search.SearchUtilities;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.spi.tasklist.PushTaskScanner;
import org.netbeans.spi.tasklist.Task;
import org.netbeans.spi.tasklist.TaskScanningScope;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Sven Reimers
 */
public class FindBugsTaskProvider extends PushTaskScanner {

    public FindBugsTaskProvider() {
        super("FindBugs", "FindBugs found Bugs", null);
    }

    private final Map<FindBugsSession, ChangeListener<FindBugsResult>> listeners = new WeakHashMap<>();

    @Override
    public synchronized void setScope(TaskScanningScope taskScanningScope, final Callback callback) {
        if (taskScanningScope == null) {
            synchronized (listeners) {
                for (Map.Entry<FindBugsSession, ChangeListener<FindBugsResult>> entry : listeners.entrySet()) {
                    entry.getKey().getResultProperty().removeListener(entry.getValue());
                }
            }
            return;
        }

        for (FileObject file : taskScanningScope.getLookup().lookupAll(FileObject.class)) {
            if (JavaSource.forFileObject(file) == null) {
                continue;
            }
            Project project = FileOwnerQuery.getOwner(file);
            if (project == null) {
                continue;
            }
            FindBugsSession qualitySession = SQECodedefectSupport.retrieveSession(project, FindBugsSession.class);
            if (qualitySession == null) {
                continue;
            }
            FindBugsResult result = getResult(qualitySession);
            Map<FindBugsResult.ClassKey, Collection<BugInstance>> instanceByClass = result.getInstanceByClass(true);
            Collection<String> classes = SearchUtilities.getFQNClassNames(file);
            List<Task> tasks = new LinkedList<Task>();
            for (String className : classes) {
                for (FindBugsResult.ClassKey classKey : instanceByClass.keySet()) {
                    if (classKey.getDisplayName().equals(className)) {
                        Collection<BugInstance> bugs = instanceByClass.get(classKey);
                        tasks.addAll(getTasks(bugs, file));
                    }
                }
            }
            callback.setTasks(file, tasks);
        }

        for (final Project project : taskScanningScope.getLookup().lookupAll(Project.class)) {
            FindBugsSession qualitySession = SQECodedefectSupport.retrieveSession(project, FindBugsSession.class);
            if (qualitySession == null) {
                continue;
            }
            FindBugsResult result = getResult(qualitySession);
            pushTasks(result, callback, project);
            synchronized (listeners) {
                ChangeListener<FindBugsResult> listener = listeners.get(qualitySession);
                if (listener != null) {
                    qualitySession.getResultProperty().removeListener(listener);
                }
                listener = new ChangeListener<FindBugsResult>() {
                    @Override
                    public void changed(ObservableValue<? extends FindBugsResult> observable, FindBugsResult oldValue, FindBugsResult newValue) {
                        pushTasks(newValue, callback, project);
                    }
                };
                listeners.put(qualitySession, listener);
                qualitySession.getResultProperty().addListener(listener);
            }
        }

    }

    private void pushTasks(FindBugsResult result, Callback callback, Project project) {
        List<Task> tasks = new LinkedList<Task>();
        for (Map.Entry<FindBugsResult.ClassKey, Collection<BugInstance>> classEntry : result.getInstanceByClass(true).entrySet()) {
            tasks.addAll(getTasks(classEntry.getValue(), classEntry.getKey().getFileObject()));
        }
        // XXX shouldn't this break out tasks by file?
        callback.setTasks(project.getProjectDirectory(), tasks);
    }

    private List<Task> getTasks(Collection<BugInstance> bugs, final FileObject file) {
        if (file == null) {
            return Collections.emptyList();
        }
        List<Task> tasks = new LinkedList<Task>();
        for (final BugInstance bugInstance : bugs) {
            tasks.add(Task.create(file, "sqe-tasklist-findbugs", bugInstance.getMessage(), bugInstance.getPrimarySourceLineAnnotation().getStartLine()));
        }
        return tasks;
    }

    private FindBugsResult getResult(FindBugsSession qualitySession) {
        FindBugsResult result = qualitySession.getResult();
        if (null == result) {
            result = qualitySession.computeResultAndWait();
        }
        return result;
    }
}
