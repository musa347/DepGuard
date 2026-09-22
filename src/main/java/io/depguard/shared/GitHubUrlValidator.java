package io.depguard.shared;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates that a repository URL points at the root of a public GitHub repository and normalises it
 * into its canonical {@code https://github.com/{owner}/{repo}} form.
 *
 * <p>Everything else is rejected: {@code http://}, other hosts (including look-alikes such as
 * {@code github.com.evil.com}), scp/SSH-style remotes, embedded credentials, custom ports, query
 * strings, fragments, percent-encoded segments, paths that reach beyond the repository root (for
 * example {@code /owner/repo/tree/main}), and reserved GitHub routes such as {@code /settings/...}.
 *
 * <p>Only the URL string is inspected: nothing is fetched, cloned or executed here.
 */
public final class GitHubUrlValidator {

    /** Single rejection message so API clients get one predictable validation error. */
    public static final String REJECTION_MESSAGE =
            "Only public GitHub URLs are accepted (https://github.com/owner/repo)";

    /** Maximum length accepted, aligned with {@code projects.repository_url} (VARCHAR(512)). */
    private static final int MAX_URL_LENGTH = 512;

    private static final String SCHEME = "https";
    private static final String HOST = "github.com";
    private static final String GIT_SUFFIX = ".git";

    /** Exactly {@code /{owner}/{repo}} with an optional trailing slash. */
    private static final Pattern PATH_PATTERN = Pattern.compile("^/([^/]+)/([^/]+?)/?$");

    /** GitHub owners: alphanumeric with inner hyphens — no leading or trailing separator. */
    private static final Pattern OWNER_PATTERN = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$");

    /** GitHub repositories: alphanumeric plus {@code .}, {@code _} and {@code -}; never {@code .}, {@code ..} or {@code .git}. */
    private static final Pattern REPOSITORY_PATTERN =
            Pattern.compile("^(?!(?:\\.{1,2}|\\.git)$)[A-Za-z0-9._-]+$", Pattern.CASE_INSENSITIVE);

    /** GitHub top-level routes that can never be a user or organisation name. */
    private static final Set<String> RESERVED_GITHUB_ROUTES = Set.of(
            "about",
            "account",
            "apps",
            "codespaces",
            "collections",
            "contact",
            "dashboard",
            "enterprise",
            "explore",
            "features",
            "issues",
            "login",
            "logout",
            "marketplace",
            "new",
            "notifications",
            "organizations",
            "orgs",
            "pricing",
            "pulls",
            "readme",
            "search",
            "security",
            "settings",
            "signup",
            "site",
            "sponsors",
            "stars",
            "topics",
            "trending",
            "users",
            "watching");

    private GitHubUrlValidator() {}

    /**
     * Validates the given repository URL and returns its canonical form, stripped of any trailing
     * slash or {@code .git} suffix.
     *
     * @param repositoryUrl the URL submitted by a client
     * @return the canonical {@code https://github.com/{owner}/{repo}} URL
     * @throws IllegalArgumentException if the URL is blank, too long, or is not a public GitHub repository URL
     */
    public static String validateAndNormalize(String repositoryUrl) {
        String candidate = repositoryUrl == null ? null : repositoryUrl.trim();
        if (candidate == null || candidate.isEmpty()) {
            throw new IllegalArgumentException("Repository URL must not be blank");
        }
        if (candidate.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Repository URL must not exceed " + MAX_URL_LENGTH + " characters");
        }

        URI uri = parse(candidate);
        if (!SCHEME.equalsIgnoreCase(uri.getScheme())
                || !HOST.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getPort() != -1
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getRawPath() == null
                || uri.getRawPath().contains("%")) {
            throw rejection();
        }

        Matcher matcher = PATH_PATTERN.matcher(uri.getRawPath());
        if (!matcher.matches()) {
            throw rejection();
        }
        String owner = matcher.group(1);
        String repository = stripGitSuffix(matcher.group(2));
        if (!OWNER_PATTERN.matcher(owner).matches()
                || RESERVED_GITHUB_ROUTES.contains(owner.toLowerCase(Locale.ROOT))
                || !REPOSITORY_PATTERN.matcher(repository).matches()) {
            throw rejection();
        }
        return SCHEME + "://" + HOST + "/" + owner + "/" + repository;
    }

    private static URI parse(String candidate) {
        try {
            return new URI(candidate);
        } catch (URISyntaxException ex) {
            throw rejection();
        }
    }

    private static String stripGitSuffix(String repository) {
        String stripped = repository;
        while (stripped.length() > GIT_SUFFIX.length()
                && stripped.regionMatches(true, stripped.length() - GIT_SUFFIX.length(), GIT_SUFFIX, 0, 4)) {
            stripped = stripped.substring(0, stripped.length() - GIT_SUFFIX.length());
        }
        return stripped;
    }

    private static IllegalArgumentException rejection() {
        return new IllegalArgumentException(REJECTION_MESSAGE);
    }
}
