package org.viewer.pricemeter.matching.cluster;

import org.springframework.stereotype.Component;
import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.matching.MatchingProperties;
import org.viewer.pricemeter.matching.similarity.MatchScore;
import org.viewer.pricemeter.matching.similarity.SimilarityScorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Об'єднує пропозиції однієї категорії в кластери-товари.
 *
 * <ol>
 *   <li>Пропозиції, вручну закріплені за одним товаром ({@code MANUAL}), — одразу один кластер.</li>
 *   <li>Оцінюються пари в межах бренду (blocking); пропозиції без бренду — з усіма.
 *       {@code REJECTED} та пари двох {@code MANUAL} не оцінюються.</li>
 *   <li>Пари з оцінкою ≥ {@code autoThreshold} об'єднуються жадібно, від найвищої оцінки (Union-Find), якщо
 *       в кластері не опиняться дві пропозиції одного магазину або два різні вручну закріплені товари.</li>
 *   <li>Пропозиція, що лишилась сама, але має збіг ≥ {@code reviewThreshold} з іншим кластером
 *       (зокрема сильний, але заборонений обмеженням магазину), — {@code PENDING}.</li>
 * </ol>
 */
@Component
public class OfferClusterer {

    private final SimilarityScorer scorer;
    private final MatchingProperties properties;

    public OfferClusterer(SimilarityScorer scorer, MatchingProperties properties) {
        this.scorer = scorer;
        this.properties = properties;
    }

    public List<OfferCluster> cluster(List<ClusterInput> inputs) {
        int n = inputs.size();
        UnionFind uf = new UnionFind(n);
        List<Set<String>> shops = new ArrayList<>(n);
        Long[] manualProduct = new Long[n];
        for (int i = 0; i < n; i++) {
            ClusterInput input = inputs.get(i);
            shops.add(new HashSet<>(Set.of(input.candidate().shopCode())));
            if (input.status() == MatchStatus.MANUAL) {
                manualProduct[i] = input.productId();
            }
        }

        Map<Long, Integer> manualGroups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (manualProduct[i] != null) {
                Integer first = manualGroups.putIfAbsent(manualProduct[i], i);
                if (first != null) {
                    union(uf, shops, manualProduct, first, i);
                }
            }
        }

        List<Edge> edges = scoreEdges(inputs);
        double[] confidence = new double[n];
        for (Edge edge : edges) {
            if (edge.score < properties.autoThreshold()) {
                break;
            }
            int ri = uf.find(edge.i);
            int rj = uf.find(edge.j);
            if (ri == rj || canMerge(shops, manualProduct, ri, rj)) {
                if (ri != rj) {
                    union(uf, shops, manualProduct, ri, rj);
                }
                confidence[edge.i] = Math.max(confidence[edge.i], edge.score);
                confidence[edge.j] = Math.max(confidence[edge.j], edge.score);
            }
        }

        double[] review = new double[n];
        for (Edge edge : edges) {
            if (uf.find(edge.i) != uf.find(edge.j)) {
                review[edge.i] = Math.max(review[edge.i], edge.score);
                review[edge.j] = Math.max(review[edge.j], edge.score);
            }
        }

        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(uf.find(i), root -> new ArrayList<>()).add(i);
        }
        List<OfferCluster> clusters = new ArrayList<>(groups.size());
        for (List<Integer> group : groups.values()) {
            List<OfferCluster.Member> members = new ArrayList<>(group.size());
            for (int i : group) {
                members.add(decide(inputs.get(i), group.size() > 1, confidence[i], review[i]));
            }
            clusters.add(new OfferCluster(members));
        }
        return clusters;
    }

    private static OfferCluster.Member decide(ClusterInput input, boolean grouped, double confidence, double review) {
        if (input.status() == MatchStatus.MANUAL || input.status() == MatchStatus.REJECTED) {
            return new OfferCluster.Member(input, input.status(), null);
        }
        if (grouped) {
            return new OfferCluster.Member(input, MatchStatus.AUTO, confidence);
        }
        if (review > 0) {
            return new OfferCluster.Member(input, MatchStatus.PENDING, review);
        }
        return new OfferCluster.Member(input, MatchStatus.AUTO, null);
    }

    /** Пари з оцінкою ≥ {@code reviewThreshold}, від найвищої; за рівних — у порядку вхідного списку. */
    private List<Edge> scoreEdges(List<ClusterInput> inputs) {
        Map<String, List<Integer>> byBrand = new HashMap<>();
        List<Integer> withoutBrand = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            String brand = inputs.get(i).candidate().brand();
            if (brand == null) {
                withoutBrand.add(i);
            } else {
                byBrand.computeIfAbsent(brand, b -> new ArrayList<>()).add(i);
            }
        }

        List<Edge> edges = new ArrayList<>();
        for (List<Integer> block : byBrand.values()) {
            for (int a = 0; a < block.size(); a++) {
                for (int b = a + 1; b < block.size(); b++) {
                    addEdge(edges, inputs, block.get(a), block.get(b));
                }
            }
        }
        for (int i : withoutBrand) {
            for (int j = 0; j < inputs.size(); j++) {
                // пари двох пропозицій без бренду — один раз
                if (j != i && (inputs.get(j).candidate().brand() != null || j > i)) {
                    addEdge(edges, inputs, Math.min(i, j), Math.max(i, j));
                }
            }
        }
        edges.sort(Comparator.comparingDouble(Edge::score).reversed()
                .thenComparingInt(Edge::i)
                .thenComparingInt(Edge::j));
        return edges;
    }

    private void addEdge(List<Edge> edges, List<ClusterInput> inputs, int i, int j) {
        ClusterInput a = inputs.get(i);
        ClusterInput b = inputs.get(j);
        if (a.status() == MatchStatus.REJECTED || b.status() == MatchStatus.REJECTED
                || (a.status() == MatchStatus.MANUAL && b.status() == MatchStatus.MANUAL)) {
            return;
        }
        MatchScore score = scorer.score(a.candidate(), b.candidate());
        if (score.value() >= properties.reviewThreshold()) {
            edges.add(new Edge(i, j, score.value()));
        }
    }

    private static boolean canMerge(List<Set<String>> shops, Long[] manualProduct, int ri, int rj) {
        if (manualProduct[ri] != null && manualProduct[rj] != null && !manualProduct[ri].equals(manualProduct[rj])) {
            return false;
        }
        for (String shop : shops.get(ri)) {
            if (shops.get(rj).contains(shop)) {
                return false;
            }
        }
        return true;
    }

    private static void union(UnionFind uf, List<Set<String>> shops, Long[] manualProduct, int a, int b) {
        int ra = uf.find(a);
        int rb = uf.find(b);
        int root = uf.union(ra, rb);
        int other = root == ra ? rb : ra;
        shops.get(root).addAll(shops.get(other));
        if (manualProduct[root] == null) {
            manualProduct[root] = manualProduct[other];
        }
    }

    private record Edge(int i, int j, double score) {
    }

    private static final class UnionFind {

        private final int[] parent;
        private final int[] size;

        UnionFind(int n) {
            parent = new int[n];
            size = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                size[i] = 1;
            }
        }

        int find(int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]];
                x = parent[x];
            }
            return x;
        }

        /** Об'єднує корені {@code a} і {@code b}; повертає новий корінь. */
        int union(int a, int b) {
            if (size[a] < size[b]) {
                int t = a;
                a = b;
                b = t;
            }
            parent[b] = a;
            size[a] += size[b];
            return a;
        }

    }

}
