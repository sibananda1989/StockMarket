package org.example.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Silently catches {@code ClientAbortException} / broken-pipe errors
 * that occur when the browser disconnects before the server finishes
 * writing the HTTP response. These are harmless and produce noisy
 * stack traces through Tomcat's error valve.
 */
@Component
@Order(Integer.MAX_VALUE)
@Slf4j
public class ClientAbortSilencerFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            chain.doFilter(request, response);
        } catch (IOException e) {
            if (isBrokenPipe(e)) {
                log.trace("Client disconnected (broken pipe) for {} — ignored",
                        request instanceof HttpServletRequest r ? r.getRequestURI() : "");
                // Swallow silently
            } else {
                throw e;
            }
        }
    }

    private boolean isBrokenPipe(IOException e) {
        String msg = e.getMessage();
        if (msg != null && msg.toLowerCase().contains("broken pipe")) {
            return true;
        }
        // Check cause chain for ClientAbortException wrapping a broken pipe
        Throwable cause = e.getCause();
        while (cause != null) {
            String cm = cause.getMessage();
            if (cm != null && cm.toLowerCase().contains("broken pipe")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
