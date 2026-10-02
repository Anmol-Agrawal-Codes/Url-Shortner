package com.example.urlshortener.url;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UrlValidator {

    public void validate(String url) {
        if (url == null || url.isBlank() || url.length() > 2048) {
            throw invalid("URL must contain between 1 and 2048 characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is malformed", exception);
        }
        String host = uri.getHost();
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || host == null || uri.getUserInfo() != null || uri.getPort() > 65535) {
            throw invalid("URL must be an absolute HTTP(S) URL without credentials");
        }
        String normalized = host.toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
        if (normalized.equals("localhost") || normalized.endsWith(".localhost")
                || normalized.endsWith(".local")) {
            throw invalid("Local or private destinations are not allowed");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlocked(address)) {
                    throw invalid("Local or private destinations are not allowed");
                }
            }
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL hostname could not be resolved", exception);
        }
    }

    static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0 || first >= 224 || (first == 100 && second >= 64 && second <= 127)
                    || (first == 198 && (second == 18 || second == 19));
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        return (first & 0xfe) == 0xfc || (first == 0xfe && (Byte.toUnsignedInt(bytes[1]) & 0xc0) == 0xc0);
    }

    private ResponseStatusException invalid(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
