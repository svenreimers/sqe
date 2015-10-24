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
package org.nbheaven.sqe.codedefects.ui.actions;

import java.awt.Component;
import java.awt.event.ActionEvent;
import javax.swing.JToggleButton;
import org.nbheaven.sqe.codedefects.core.api.QualityProvider;
import org.nbheaven.sqe.codedefects.core.api.QualitySession;
import org.nbheaven.sqe.codedefects.core.util.SQECodedefectSupport;
import org.nbheaven.sqe.codedefects.ui.actions.AbstractQualitySessionAwareAction;
import org.netbeans.api.project.Project;
import org.openide.awt.Actions;
import org.openide.util.Lookup;
import org.openide.util.actions.Presenter;

/**
 * Action to show or hide project result annotations.
 *
 * @author Florian Vogler
 */
public abstract class AbstractEnableBackgroundScanningAction extends AbstractQualitySessionAwareAction implements Presenter.Toolbar {

    private final QualityProvider provider;

    protected AbstractEnableBackgroundScanningAction(Lookup context, QualityProvider provider) {
        super(context, provider.getSessionEventProxy());
        this.provider = provider;

    }

    @Override
    protected void updateActionStateImpl(Project project) {
        QualitySession session = SQECodedefectSupport.retrieveSession(project, provider);
        setEnabled(isEnabledForProject(project));
        putValue(SELECTED_KEY, session != null && session.isBackgroundScanningEnabled());
    }

    @Override
    protected boolean isEnabledForProject(Project project) {
        return SQECodedefectSupport.isQualityProviderEnabled(project, provider)
                && provider.isValidFor(project);
    }

    @Override
    protected void actionPerformedImpl(ActionEvent e, Project project) {
        QualitySession session = SQECodedefectSupport.retrieveSession(project, provider);
        Boolean value = (Boolean) getValue(SELECTED_KEY);
        session.setBackgroundScanningEnabled(value != null && value);
    }

    @Override
    public Component getToolbarPresenter() {
        JToggleButton button = new JToggleButton(this);
        button.setHideActionText(true);
        button.setRolloverEnabled(false);
        button.setFocusable(false);
        return button;
    }

}
