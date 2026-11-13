package org.viewer.pricemeter.crawler.http;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Правила robots.txt для одного агента (RFC 9309): групи User-agent, Allow/Disallow
 * з шаблонами {@code *} та {@code $}, найдовше співпадіння перемагає, при рівності — Allow.
 * Додатково підтримується нестандартна директива Crawl-delay.
 */
public final class RobotsTxt {

    private static final RobotsTxt ALLOW_ALL = new RobotsTxt(List.of(), null);

    private final List<Rule> rules;
    private final Duration crawlDelay;

    private RobotsTxt(List<Rule> rules, Duration crawlDelay) {
        this.rules = rules;
        this.crawlDelay = crawlDelay;
    }

    public static RobotsTxt allowAll() {
        return ALLOW_ALL;
    }

    /**
     * Розбирає robots.txt і залишає правила, що стосуються {@code agentToken}
     * (групи з цим агентом, а якщо таких немає — групи {@code *}).
     */
    public static RobotsTxt parse(String content, String agentToken) {
        String agent = agentToken.toLowerCase(Locale.ROOT);
        List<Group> groups = new ArrayList<>();
        Group current = null;
        boolean lastWasAgent = false;

        for (String rawLine : content.split("\\R")) {
            int hash = rawLine.indexOf('#');
            String line = (hash >= 0 ? rawLine.substring(0, hash) : rawLine).strip();
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();

            if (key.equals("user-agent")) {
                if (current == null || !lastWasAgent) {
                    current = new Group();
                    groups.add(current);
                }
                current.agents.add(value.toLowerCase(Locale.ROOT));
                lastWasAgent = true;
                continue;
            }
            lastWasAgent = false;
            if (current == null) {
                continue;
            }
            switch (key) {
                case "allow" -> addRule(current, value, true);
                case "disallow" -> addRule(current, value, false);
                case "crawl-delay" -> current.crawlDelay = parseDelay(value);
                default -> { /* sitemap та інші директиви тут не потрібні */ }
            }
        }

        List<Group> matched = groups.stream().filter(g -> g.agents.stream().anyMatch(agent::contains)).toList();
        if (matched.isEmpty()) {
            matched = groups.stream().filter(g -> g.agents.contains("*")).toList();
        }
        List<Rule> rules = new ArrayList<>();
        Duration delay = null;
        for (Group group : matched) {
            rules.addAll(group.rules);
            if (group.crawlDelay != null) {
                delay = group.crawlDelay;
            }
        }
        return new RobotsTxt(List.copyOf(rules), delay);
    }

    /**
     * @param pathAndQuery шлях із рядком запиту, напр. {@code /search?q=lg}
     */
    public boolean isAllowed(String pathAndQuery) {
        Rule best = null;
        for (Rule rule : rules) {
            if (rule.pattern.matcher(pathAndQuery).lookingAt()
                    && (best == null || rule.length > best.length || (rule.length == best.length && rule.allow))) {
                best = rule;
            }
        }
        return best == null || best.allow;
    }

    public Optional<Duration> crawlDelay() {
        return Optional.ofNullable(crawlDelay);
    }

    private static void addRule(Group group, String path, boolean allow) {
        if (!path.isEmpty()) {
            group.rules.add(new Rule(toPattern(path), path.length(), allow));
        }
    }

    private static Pattern toPattern(String path) {
        boolean anchored = path.endsWith("$");
        String body = anchored ? path.substring(0, path.length() - 1) : path;
        StringBuilder regex = new StringBuilder();
        for (String part : body.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        if (anchored) {
            regex.append('$');
        }
        return Pattern.compile(regex.toString());
    }

    private static Duration parseDelay(String value) {
        try {
            double seconds = Double.parseDouble(value);
            return seconds > 0 ? Duration.ofMillis(Math.round(seconds * 1000)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class Group {
        private final List<String> agents = new ArrayList<>();
        private final List<Rule> rules = new ArrayList<>();
        private Duration crawlDelay;
    }

    private record Rule(Pattern pattern, int length, boolean allow) {
    }

}
