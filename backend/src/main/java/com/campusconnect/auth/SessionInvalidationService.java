package com.campusconnect.auth;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class SessionInvalidationService {
    private final SessionRegistry sessions;
    public SessionInvalidationService(SessionRegistry sessions) { this.sessions = sessions; }

    public void invalidateUserSessions(String email) {
        for (Object principal : sessions.getAllPrincipals()) {
            String principalEmail = principal instanceof UserDetails details ? details.getUsername() : String.valueOf(principal);
            if (!email.equalsIgnoreCase(principalEmail)) continue;
            for (SessionInformation session : sessions.getAllSessions(principal, false)) session.expireNow();
        }
    }
}
