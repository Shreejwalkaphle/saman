package com.bajar.saman.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Enforces per-IP rate limits on the two brute-force-sensitive auth endpoints
 * (/api/auth/login, /api/auth/register). Runs BEFORE JwtAuthenticationFilter in the
 * chain (wired in SecurityConfig) so an attacker gets rejected here cheaply, without
 * us paying the cost of JWT parsing or a DB lookup for a request we're about to
 * reject anyway.
 *
 * Closes gap #2 flagged in PROGRESS.md ("No rate limiting on register/login").
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<byte[]> proxyManager;

    public RateLimitFilter(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Each protected endpoint gets its OWN Bandwidth (limit rule) and its OWN
        // Redis key prefix — so hammering /login doesn't burn through the SEPARATE
        // /register allowance for the same IP, and vice versa.
        Bandwidth bandwidth;
        String bucketPrefix;

        if (path.equals("/api/auth/login")) {
            // 5 attempts per 1 minute per IP. Tight — this is the exact endpoint a
            // credential-stuffing / brute-force attack would target directly.
            bandwidth = Bandwidth.builder()
                    .capacity(5)
                    .refillGreedy(5, Duration.ofMinutes(1))
                    .build();
            bucketPrefix = "rl:login:";
        } else if (path.equals("/api/auth/register")) {
            // 3 attempts per 1 HOUR per IP. Looser attack surface than login (no
            // password to guess against), but still needs a hard cap — otherwise a
            // script can spam our database with junk accounts indefinitely.
            bandwidth = Bandwidth.builder()
                    .capacity(3)
                    .refillGreedy(3, Duration.ofHours(1))
                    .build();
            bucketPrefix = "rl:register:";
        } else {
            // Any endpoint OTHER than these two skips this filter entirely — cheap
            // check, avoids an unnecessary Redis round trip on every single request
            // in the whole application.
            filterChain.doFilter(request, response);
            return;
        }

        // Bucket key = endpoint + client IP, so limits are isolated per-IP AND
        // per-endpoint.
        //
        // KNOWN LIMITATION (flagging deliberately, tracked in PROGRESS.md too):
        // getRemoteAddr() returns the IP of whoever is DIRECTLY connected to this
        // server over TCP. That's correct right now (client -> this app, no hop in
        // between). Once this sits behind a reverse proxy / load balancer / API
        // gateway in production (per the roadmap), getRemoteAddr() would always
        // return the PROXY's IP instead of the real client's — at that point this
        // needs to read the "X-Forwarded-For" header instead, or every real client
        // would share one rate-limit bucket. Not fixing now since there's no
        // gateway yet, but must not be forgotten when one is added.
        String clientIp = request.getRemoteAddr();
        byte[] bucketKey = (bucketPrefix + clientIp).getBytes(StandardCharsets.UTF_8);

        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(bandwidth)
                .build();

        // Supplier, not the config object directly: Bucket4j only needs this if the
        // bucket doesn't already exist in Redis yet (first request from this IP)
        // — it uses the supplier to create it lazily, rather than us having to
        // check "does it exist" ourselves first.
        Supplier<BucketConfiguration> configSupplier = () -> config;
        Bucket bucket = proxyManager.builder().build(bucketKey, configSupplier);

        // Attempts to remove exactly 1 token from this IP's bucket. Returns true if
        // a token was available (request allowed to proceed), false if the bucket
        // was empty (limit hit — request must be rejected below). This single call
        // is the atomic "check AND consume" operation — there's no separate
        // "check first, consume second" step, which is exactly what avoids the
        // race condition CAS-based Redis storage was chosen for (see
        // RateLimitConfig's comment on casBasedBuilder).
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            // 429 Too Many Requests — the HTTP-correct status code for rate limiting.
            //
            // We hand-build this JSON response here instead of routing through
            // GlobalExceptionHandler, because that handler is a @RestControllerAdvice
            // — it only catches exceptions thrown INSIDE controller methods. This
            // filter runs BEFORE the request ever reaches a controller, so
            // GlobalExceptionHandler physically cannot intervene here. This is the
            // same category of gap flagged in PROGRESS.md (#5, inconsistent error
            // shape for filter-level rejections) — being worked around manually for
            // this one case, not yet solved generally.
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded, please try again later.\"}"
            );
            return; // Critical: do NOT call filterChain.doFilter() past this point —
            // that would let the rejected request continue on to the
            // controller anyway, defeating the entire point of this filter.
        }

        filterChain.doFilter(request, response);
    }
}