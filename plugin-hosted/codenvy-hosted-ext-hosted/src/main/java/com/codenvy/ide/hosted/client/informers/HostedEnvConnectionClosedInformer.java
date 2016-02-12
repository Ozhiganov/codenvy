/*
 *  [2012] - [2016] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.ide.hosted.client.informers;

import com.codenvy.ide.hosted.client.login.PromptToLoginView;
import com.codenvy.ide.hosted.client.HostedLocalizationConstant;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;

import org.eclipse.che.ide.api.ConnectionClosedInformer;
import org.eclipse.che.ide.websocket.events.WebSocketClosedEvent;

import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_ABNORMAL;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_NORMAL;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_FAILURE_TLS_HANDSHAKE;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_NEGOTIATE_EXTENSION;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_NO_STATUS;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_PROTOCOL_ERROR;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_UNEXPECTED_CONDITION;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_UNSUPPORTED;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_TOO_LARGE;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_INCONSISTENT_DATA;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_GOING_AWAY;
import static org.eclipse.che.ide.websocket.events.WebSocketClosedEvent.CLOSE_VIOLATE_POLICY;

/**
 * Notify that WebSocket connection was closed.
 *
 * @author Roman Nikitenko
 */
public class HostedEnvConnectionClosedInformer implements ConnectionClosedInformer {

    private PromptToLoginView          promptToLoginView;
    private HostedLocalizationConstant localizationConstant;

    @Inject
    HostedEnvConnectionClosedInformer(PromptToLoginView promptToLoginView,
                                      HostedLocalizationConstant localizationConstant) {
        this.promptToLoginView = promptToLoginView;
        this.localizationConstant = localizationConstant;

        promptToLoginView.setDelegate(new PromptToLoginView.ActionDelegate() {
            @Override
            public void onLogin() {
                String separator = Window.Location.getQueryString().contains("?") ? "&" : "?";
                Window.Location.replace(Window.Location.getPath() + Window.Location.getQueryString() + separator + "login");
            }

            @Override
            public void onCreateAccount() {
                Window.Location.replace("/site/create-account");
            }
        });
    }

    @Override
    public void onConnectionClosed(WebSocketClosedEvent event) {
        switch (event.getCode()) {
            case CLOSE_NORMAL:
            case CLOSE_GOING_AWAY:
            case CLOSE_PROTOCOL_ERROR:
            case CLOSE_UNSUPPORTED:
            case CLOSE_NO_STATUS:
            case CLOSE_ABNORMAL:
            case CLOSE_INCONSISTENT_DATA:
            case CLOSE_VIOLATE_POLICY:
            case CLOSE_TOO_LARGE:
            case CLOSE_NEGOTIATE_EXTENSION:
            case CLOSE_UNEXPECTED_CONDITION:
            case CLOSE_FAILURE_TLS_HANDSHAKE:
                showPromptToLogin(localizationConstant.connectionClosedDialogTitle(), localizationConstant.connectionClosedDialogMessage());
        }
    }

    /**
     * Displays dialog using title and message.
     */
    private void showPromptToLogin(String title, String message) {
        promptToLoginView.showDialog(title, message);
    }
}
