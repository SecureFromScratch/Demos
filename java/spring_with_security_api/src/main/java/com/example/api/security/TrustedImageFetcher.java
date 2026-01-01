package com.example.api.security;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.api.config.SecurityProperties;

@Component
public final class TrustedImageFetcher {

    private  final long MAX_BYTES = 10L * 1024L * 1024L; // 10 MB

    private final Set<String> trustedDomains;

    private final OkHttpClient client;

     public TrustedImageFetcher(SecurityProperties props) {
        this.trustedDomains = Set.copyOf(props.getTrustedDomains());
        this.client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(8))
                .callTimeout(Duration.ofSeconds(12))
                .build();
    }

    public byte[] fetchHttpsFromTrustedDomain(String userUrl) {
        URI uri = parseHttpsUrl(userUrl);

        String host = requireHost(uri);
        requireTrustedHost(host);

        List<InetAddress> verifiedIps = resolveAndVerifyIps(host);

        OkHttpClient pinnedClient = client.newBuilder()
                .dns(new PinnedDns(host, verifiedIps))
                .build();

        Request req = new Request.Builder()
                .url(uri.toString())
                .get()
                .build();

        try (Response resp = pinnedClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IllegalArgumentException("Fetch failed with HTTP " + resp.code());
            }

            String contentType = resp.header("Content-Type");
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IllegalArgumentException("Response is not an image");
            }

            long len = resp.body() != null ? resp.body().contentLength() : -1L;
            if (len > MAX_BYTES) {
                throw new IllegalArgumentException("Image too large");
            }

            if (resp.body() == null) {
                throw new IllegalArgumentException("Empty response body");
            }

            byte[] bytes = resp.body().bytes();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("Empty image");
            }
            if (bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Image too large");
            }

            return bytes;
        } catch (IOException e) {
            throw new IllegalArgumentException("Fetch failed", e);
        }
    }

    private  URI parseHttpsUrl(String raw) {
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL is not well-formed", e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("URL must use HTTPS");
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URL must not include userinfo");
        }

        return uri;
    }

    private  String requireHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must include a hostname");
        }
        return host;
    }

    private  void requireTrustedHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);

        if ( this.trustedDomains.contains(h)) {
            return;
        }

        for (String allowed :  this.trustedDomains) {
            if (h.equals(allowed) || h.endsWith("." + allowed)) {
                return;
            }
        }

        throw new IllegalArgumentException("Host is not in trusted allowlist");
    }

    private  List<InetAddress> resolveAndVerifyIps(String host) {
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (IOException e) {
            throw new IllegalArgumentException("DNS resolution failed", e);
        }

        if (resolved.length == 0) {
            throw new IllegalArgumentException("DNS returned no IPs");
        }

        List<InetAddress> verified = new ArrayList<>();
        for (InetAddress ip : resolved) {
            if (!isDisallowedIp(ip)) {
                verified.add(ip);
            }
        }

        if (verified.isEmpty()) {
            throw new IllegalArgumentException("All resolved IPs are disallowed");
        }

        return Collections.unmodifiableList(verified);
    }

    private  boolean isDisallowedIp(InetAddress ip) {
        if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress() || ip.isMulticastAddress()) {
            return true;
        }

        if (ip instanceof Inet4Address) {
            byte[] b = ip.getAddress();
            int a = b[0] & 0xFF;
            int c = b[1] & 0xFF;

            if (a == 10) {
                return true;
            } // 10.0.0.0/8
            if (a == 127) {
                return true;
            } // 127.0.0.0/8
            if (a == 169 && c == 254) {
                return true;
            } // 169.254.0.0/16
            if (a == 172 && c >= 16 && c <= 31) {
                return true;
            } // 172.16.0.0/12
            if (a == 192 && c == 168) {
                return true;
            } // 192.168.0.0/16
            if (a == 100 && c >= 64 && c <= 127) {
                return true;
            } // 100.64.0.0/10

            return false;
        }

        if (ip instanceof Inet6Address) {
            byte[] b = ip.getAddress();
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;

            if ((first & 0xFE) == 0xFC) {
                return true;
            } // fc00::/7 (ULA)
            if (first == 0xFE && (second & 0xC0) == 0x80) {
                return true;
            } // fe80::/10 link-local

            return false;
        }

        return true;
    }

    private  final class PinnedDns implements Dns {

        private final String pinnedHostLower;
        private final List<InetAddress> pinnedIps;

        private PinnedDns(String pinnedHost, List<InetAddress> pinnedIps) {
            this.pinnedHostLower = pinnedHost.toLowerCase(Locale.ROOT);
            this.pinnedIps = pinnedIps;
        }

        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            if (!hostname.toLowerCase(Locale.ROOT).equals(pinnedHostLower)) {
                throw new UnknownHostException("Unexpected DNS lookup host");
            }
            return pinnedIps;
        }
    }
}
