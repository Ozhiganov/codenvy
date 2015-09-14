/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2015] Codenvy, S.A.
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
package com.codenvy.auth.sso.oauth;

import org.eclipse.che.api.auth.AuthenticationException;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.UsersWorkspace;
import org.eclipse.che.api.user.server.dao.User;
import org.eclipse.che.api.user.server.dao.UserDao;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import com.codenvy.auth.sso.server.InputDataException;
import com.codenvy.auth.sso.server.InputDataValidator;
import com.codenvy.auth.sso.server.handler.BearerTokenAuthenticationHandler;
import org.eclipse.che.security.oauth.OAuthAuthenticationException;
import org.eclipse.che.security.oauth.OAuthAuthenticator;
import org.eclipse.che.security.oauth.OAuthAuthenticatorProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


@Singleton
public class OAuthLoginServlet extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(OAuthLoginServlet.class);
    @Inject
    private UserDao                          userDao;
    @Inject
    private WorkspaceManager                 workspaceManager;
    @Inject
    private OAuthAuthenticatorProvider       authenticatorProvider;
    @Inject
    private BearerTokenAuthenticationHandler handler;
    @Inject
    private InputDataValidator               inputDataValidator;
    @Named("auth.sso.create_workspace_page_url")
    @Inject
    private String                           createWorkspacePage;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String username = req.getParameter("username");
        String oauthProvider = req.getParameter("oauth_provider");

        String bearertoken = req.getParameter("oauthbearertoken");

        if (username == null || oauthProvider == null || bearertoken == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }


        try {
            handler.authenticate(username, bearertoken);
        } catch (AuthenticationException e) {
            resp.sendError(e.getResponseStatus(), e.getLocalizedMessage());
            return;
        }

        if (username.contains("+")) {
            req.setAttribute("errorMessage", "Username with '+' is not allowed for registration");
            req.getRequestDispatcher("/login.html")
               .forward(req, resp);
            return;
        }

        OAuthAuthenticator authenticator = authenticatorProvider.getAuthenticator(oauthProvider);
        if (authenticator == null) {
            LOG.error("Unknown OAuthAuthenticatorProvider  {} ", oauthProvider);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                           "Authentication failed. Unknown oauth_provider " + oauthProvider);
            return;
        }

        final OAuthToken token = authenticator.getToken(username);
        if (token == null || token.getToken()
                                  .isEmpty()) {
            LOG.error("Unable obtain email address for user {} ", username);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unable obtain email address for user " + username);
            return;
        }

        if (isUserHasPersistentTenants(username)) {
            // send user on login
            URI uri = UriBuilder.fromUri(createWorkspacePage)
                                .replaceQuery(req.getQueryString())
                                .replaceQueryParam("signature")
                                .replaceQueryParam("oauth_provider")
                                .replaceQueryParam("oauthbearertoken")
                                .queryParam("bearertoken", handler.generateBearerToken(username,
                                                                                       Collections
                                                                                               .singletonMap(
                                                                                                       "initiator",
                                                                                                       oauthProvider)
                                                                                      ))
                                .build();

            LOG.debug("Oauth login. Redirect after: {}", uri.toString());
            resp.sendRedirect(uri.toString());
        } else {
            // fill user profile if user doesn't exists and login in first time.
            Map profileInfo = createProfileInfo(username, authenticator, token);
            profileInfo.put("initiator", oauthProvider);

            try {
                inputDataValidator.validateUserMail(username);

                URI uri =
                        UriBuilder.fromUri(createWorkspacePage).replaceQuery(req.getQueryString())
                                  .replaceQueryParam("signature")
                                  .replaceQueryParam("oauth_provider")
                                  .replaceQueryParam("bearertoken", handler.generateBearerToken(username, profileInfo)).build();

                resp.sendRedirect(uri.toString());
            } catch (InputDataException e) {
                throw new ServletException(e.getLocalizedMessage(), e);
            }
        }
    }

    private boolean isUserHasPersistentTenants(String username) throws IOException {
        try {
            User user = userDao.getByAlias(username);
            for (UsersWorkspace ws : workspaceManager.getWorkspaces(user.getId())) {
                if (!ws.isTemporary()) {
                    return true;
                }
            }
        } catch (NotFoundException e) {
            //ok
        } catch (ServerException | BadRequestException e) {
            throw new IOException(e.getLocalizedMessage(), e);
        }
        return false;

    }

    Map<String, String> createProfileInfo(String username, OAuthAuthenticator authenticator, OAuthToken token) {
        Map<String, String> profileInfo = new HashMap<>();
        try {
            try {
                userDao.getByAlias(username);
            } catch (NotFoundException e) {
                String fullName = authenticator.getUser(token).getName();
                if (fullName != null && !fullName.isEmpty()) {
                    String firstName, lastName = "";
                    String[] names = fullName.trim().split(" ", 2);
                    firstName = names[0].trim();
                    if (names.length > 1)
                        lastName = names[1].trim();
                    if (!(firstName.isEmpty() && lastName.isEmpty())) {
                        if (!firstName.isEmpty()) {
                            profileInfo.put("firstName", firstName);
                        }
                        if (!lastName.isEmpty()) {
                            profileInfo.put("lastName", lastName);
                        }
                    }
                }
            }
        } catch (ApiException | OAuthAuthenticationException e) {
            LOG.error(e.getLocalizedMessage(), e);
        }

        return profileInfo;
    }
}
