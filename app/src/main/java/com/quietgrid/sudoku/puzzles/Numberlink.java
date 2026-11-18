package com.quietgrid.sudoku.puzzles;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Numberlink implements UndoablePuzzle {
    public static final int N = 4, PAIRS = 3, MAX_PAIRS = 5;
    public static final GridEdges GRID = new GridEdges(N);
    public final GridEdges grid;
    public final int[] endpoints, edges;
    private Numberlink(int[] endpoints, int[] edges) {
        this.endpoints = endpoints.clone(); this.edges = edges.clone();
        grid = endpoints.length == 16 ? GRID : new GridEdges(6);
    }
    @Override public Mode mode() { return Mode.NUMBERLINK; }
    @Override public int size() { return grid.width; }
    public int pairs() { return Arrays.stream(endpoints).max().orElse(0); }
    @Override public int[] entries() { return edges; }
    @Override public Numberlink copy() { return new Numberlink(endpoints, edges); }
    @Override public boolean solved() { return valid(endpoints, edges); }
    public String toggle(int x, int y) {
        if (x < 0 || y < 0 || x >= endpoints.length || y >= endpoints.length) return "Not adjacent.";
        int edge = grid.between(x, y);
        if (edge < 0) return "Not adjacent.";
        if (solved()) return null;
        if (edges[edge] == 1) { edges[edge] = 0; return null; }
        if (grid.degree(edges, x) >= (endpoints[x] == 0 ? 2 : 1)
                || grid.degree(edges, y) >= (endpoints[y] == 0 ? 2 : 1))
            return "Connection limit reached.";
        boolean[] part = grid.component(edges, x);
        if (part[y]) return "Paths cannot close into loops.";
        int label = 0, count = 0;
        boolean[] other = grid.component(edges, y);
        for (int cell = 0; cell < endpoints.length; cell++) if ((part[cell] || other[cell]) && endpoints[cell] > 0) {
            if (label != 0 && endpoints[cell] != label) return "Only matching endpoints may connect.";
            label = endpoints[cell]; count++;
        }
        if (count > 2) return "Each path connects just one pair.";
        edges[edge] = 1;
        return null;
    }

    public int pairAt(int cell) {
        boolean[] part = grid.component(edges, cell);
        for (int i = 0; i < endpoints.length; i++) if (part[i] && endpoints[i] != 0) return endpoints[i];
        return 0;
    }

    /** A private prospective path: cancel discards it; release commits one board edit. */
    public static final class Drag {
        public final Numberlink preview;
        private final List<Integer> path = new ArrayList<>();
        private final GridEdges grid;
        private int label;
        private boolean moved;

        private Drag(Numberlink game, int start) {
            preview = game.copy();
            grid = game.grid;
            label = game.pairAt(start);
            boolean[] part = grid.component(game.edges, start);
            int root = start;
            if (game.endpoints[start] == 0) {
                root = -1;
                for (int i = 0; i < game.endpoints.length; i++) if (part[i] && game.endpoints[i] == label) { root = i; break; }
                if (label == 0) {
                    root = -1;
                    for (int i = 0; i < game.endpoints.length; i++) if (part[i] && grid.degree(game.edges, i) == 1) { root = i; break; }
                }
            }
            if (root < 0 || !trace(root, -1, start, game)) throw new IllegalArgumentException("Path has no editable endpoint.");
        }

        public static Drag begin(Numberlink game, int cell) {
            if (cell < 0 || cell >= game.endpoints.length || game.solved() || game.pairAt(cell) == 0 && game.grid.degree(game.edges, cell) == 0) return null;
            return new Drag(game, cell);
        }

        private boolean trace(int cell, int previous, int end, Numberlink original) {
            if (path.contains(cell)) return false;
            path.add(cell);
            if (cell == end) return true;
            for (int edge : grid.at[cell]) if (original.edges[edge] == 1) {
                int next = grid.other(edge, cell);
                if (next != previous && trace(next, cell, end, original)) return true;
            }
            path.remove(path.size() - 1);
            return false;
        }

        public int head() { return path.get(path.size() - 1); }

        public boolean moveTo(int next) {
            int head = head(), edge = grid.between(head, next);
            if (edge < 0) return false;
            if (path.size() > 1 && path.get(path.size() - 2) == next) {
                trimTail();
                preview.edges[edge] = 0;
                path.remove(path.size() - 1); moved = true;
                return true;
            }
            if (path.contains(next) || preview.endpoints[head] != 0 && path.size() > 1
                    || label != 0 && preview.endpoints[next] != 0 && preview.endpoints[next] != label) return false;
            Numberlink trial = preview.copy();
            trimTail(trial);
            if (grid.degree(trial.edges, next) != 0) return false;
            if (trial.toggle(head, next) != null) return false;
            System.arraycopy(trial.edges, 0, preview.edges, 0, preview.edges.length);
            if (label == 0 && preview.endpoints[next] != 0) label = preview.endpoints[next];
            path.add(next); moved = true;
            return true;
        }

        private void trimTail() { trimTail(preview); }
        private void trimTail(Numberlink board) {
            int keep = path.size() > 1 ? grid.between(head(), path.get(path.size() - 2)) : -1;
            for (int edge : grid.at[head()]) if (edge != keep && board.edges[edge] == 1) {
                int cell = grid.other(edge, head());
                board.edges[edge] = 0;
                while (board.endpoints[cell] == 0) {
                    int following = -1;
                    for (int remaining : grid.at[cell]) if (board.edges[remaining] == 1) { following = remaining; break; }
                    if (following < 0) break;
                    int next = grid.other(following, cell);
                    board.edges[following] = 0; cell = next;
                }
            }
        }

        public boolean changed() { return moved; }
    }
    public static boolean valid(int[] endpoints, int[] edges) {
        if (!validEndpoints(endpoints)) return false;
        GridEdges grid = endpoints.length == 16 ? GRID : new GridEdges(6);
        if (edges.length != grid.a.length) return false;
        for (int edge : edges) if (edge < 0 || edge > 1) return false;
        for (int cell = 0; cell < endpoints.length; cell++) if (grid.degree(edges, cell) != (endpoints[cell] == 0 ? 2 : 1)) return false;
        boolean[] seen = new boolean[endpoints.length];
        for (int cell = 0; cell < endpoints.length; cell++) if (!seen[cell]) {
            boolean[] part = grid.component(edges, cell); int label = 0, count = 0;
            for (int i = 0; i < endpoints.length; i++) if (part[i]) {
                seen[i] = true;
                if (endpoints[i] > 0) {
                    if (label != 0 && label != endpoints[i]) return false;
                    label = endpoints[i]; count++;
                }
            }
            if (count != 2) return false;
        }
        return true;
    }
    private static boolean validEndpoints(int[] endpoints) {
        if (endpoints.length != 16 && endpoints.length != 36) return false;
        int[] counts = new int[MAX_PAIRS + 1];
        int pairs = Arrays.stream(endpoints).max().orElse(0);
        if (endpoints.length == 16 ? pairs < PAIRS || pairs > 4 : pairs != 5) return false;
        for (int value : endpoints) { if (value < 0 || value > pairs) return false; counts[value]++; }
        for (int pair = 1; pair <= pairs; pair++) if (counts[pair] != 2) return false;
        return true;
    }

    public static int count(int[] endpoints, int limit, Generation budget) {
        if (!validEndpoints(endpoints)) return 0;
        if (limit < 1) throw new IllegalArgumentException("Positive solution limit required.");
        return new Search(endpoints, budget).pair((1 << Arrays.stream(endpoints).max().orElse(0)) - 1, limit);
    }

    private static final class Search {
        final int[] endpoints, occupied, start, end;
        final int[][] neighbors;
        final Generation budget;
        Search(int[] endpoints, Generation budget) {
            this.endpoints = endpoints; this.budget = budget;
            occupied = new int[endpoints.length];
            int pairs = Arrays.stream(endpoints).max().orElse(0), n = endpoints.length == 16 ? 4 : 6;
            start = new int[pairs]; end = new int[pairs]; Arrays.fill(start, -1);
            neighbors = new int[endpoints.length][];
            for (int i = 0; i < endpoints.length; i++) {
                neighbors[i] = Generation.neighbors(i, n);
                if (endpoints[i] != 0) {
                    int pair = endpoints[i] - 1;
                    if (start[pair] < 0) start[pair] = i; else end[pair] = i;
                }
            }
        }
        int pair(int remaining, int limit) {
            budget.tick();
            if (remaining == 0) return Arrays.stream(occupied).allMatch(v -> v > 0) ? 1 : 0;
            int chosen = -1, minimum = Integer.MAX_VALUE;
            for (int p = 0; p < start.length; p++) if ((remaining & 1 << p) != 0) {
                int ways = available(start[p], -1) * available(end[p], -1);
                if (ways < minimum) { minimum = ways; chosen = p; }
            }
            occupied[start[chosen]] = 1;
            int found = path(chosen, start[chosen], remaining ^ 1 << chosen, limit);
            occupied[start[chosen]] = 0;
            return found;
        }
        int available(int cell, int head) {
            int count = 0;
            for (int next : neighbors[cell]) if (next >= 0 && (occupied[next] == 0 || next == head)) count++;
            return count;
        }
        boolean feasible(int head, int active, int remaining) {
            for (int i = 0; i < occupied.length; i++) if (occupied[i] == 0
                    && available(i, head) < (endpoints[i] == 0 ? 2 : 1)) return false;
            int[] component = new int[occupied.length], queue = new int[occupied.length]; int id = 0;
            for (int i = 0; i < occupied.length; i++) if ((occupied[i] == 0 || i == head) && component[i] == 0) {
                int read = 0, size = 1; queue[0] = i; component[i] = ++id;
                while (read < size) {
                    for (int next : neighbors[queue[read++]]) if (next >= 0 && component[next] == 0
                            && (occupied[next] == 0 || next == head)) {
                        component[next] = id; queue[size++] = next;
                    }
                }
            }
            if (component[head] != component[end[active]]) return false;
            boolean[] hasPair = new boolean[id + 1]; hasPair[component[head]] = true;
            for (int p = 0; p < start.length; p++) if ((remaining & 1 << p) != 0) {
                if (component[start[p]] != component[end[p]]) return false;
                hasPair[component[start[p]]] = true;
            }
            for (int c = 1; c <= id; c++) if (!hasPair[c]) return false;
            return true;
        }
        int path(int active, int head, int remaining, int limit) {
            budget.tick();
            if (head == end[active]) return pair(remaining, limit);
            if (!feasible(head, active, remaining)) return 0;
            int forced = -1;
            for (int next : neighbors[head]) if (next >= 0 && occupied[next] == 0 && endpoints[next] == 0
                    && available(next, head) == 2) {
                if (forced >= 0) return 0;
                forced = next;
            }
            int count = 0;
            for (int next : neighbors[head]) {
                if (next < 0 || occupied[next] != 0 || forced >= 0 && next != forced
                        || endpoints[next] != 0 && next != end[active]) continue;
                occupied[next] = 1;
                count += path(active, next, remaining, limit - count);
                occupied[next] = 0;
                if (count >= limit) break;
            }
            return count;
        }
    }
    public static Numberlink generate(Random random, Generation budget) {
        return generate(PAIRS, random, budget);
    }
    public static Numberlink generate(int setting, Random random, Generation budget) {
        if (setting != PAIRS && setting != 4 && setting != 6) throw new IllegalArgumentException("Choose three/four pairs or the larger 6x6 board.");
        int n = setting == 6 ? 6 : 4, pairs = n == 6 ? 5 : setting;
        for (int attempt = 0; attempt < (n == 6 ? 800 : 160); attempt++) {
            int[] path = new int[n * n];
            if (n == 4) {
                boolean[] used = new boolean[16];
                path[0] = random.nextInt(16); used[path[0]] = true;
                if (!hamilton(path, used, 1, random, budget)) continue;
            } else randomizedPath(path, n, random, budget);
            int minimum = n == 6 ? 4 : 2;
            int[] lengths = new int[pairs]; Arrays.fill(lengths, minimum);
            for (int spare = pairs * minimum; spare < path.length; spare++) lengths[random.nextInt(pairs)]++;
            int[] endpoints = new int[path.length];
            int start = 0;
            for (int label = 1; label <= pairs; label++) {
                endpoints[path[start]] = label;
                start += lengths[label - 1];
                endpoints[path[start - 1]] = label;
            }
            if (n == 6 && !challenging(path, lengths, n)) continue;
            if (count(endpoints, 2, budget) == 1) return new Numberlink(endpoints, new int[2 * n * (n - 1)]);
        }

        throw new IllegalStateException("Could not certify unique Numberlink paths. Please retry.");
    }

    private static boolean challenging(int[] path, int[] lengths, int n) {
        int offset = 0, detour = 0, bends = 0;
        for (int length : lengths) {
            int first = path[offset], last = path[offset + length - 1];
            int distance = Math.abs(first / n - last / n) + Math.abs(first % n - last % n);
            if (distance < 2) return false;
            detour += length - 1 - distance;
            for (int i = offset + 2; i < offset + length; i++)
                if (path[i] - path[i - 1] != path[i - 1] - path[i - 2]) bends++;
            offset += length;
        }
        return detour >= 10 && bends >= 12;
    }

    private static void randomizedPath(int[] path, int n, Random random, Generation budget) {
        for (int i = 0; i < path.length; i++) path[i] = i / n * n + (i / n % 2 == 0 ? i % n : n - 1 - i % n);
        for (int mix = 0; mix < path.length * 12; mix++) {
            budget.tick();
            if (random.nextBoolean()) reverse(path, path.length);
            int next = Generation.neighbors(path[0], n)[random.nextInt(4)];
            if (next < 0 || next == path[1]) continue;
            int index = 2; while (path[index] != next) index++;
            reverse(path, index);
        }
    }
    private static void reverse(int[] path, int length) {
        for (int i = 0; i < length / 2; i++) {
            int other = length - 1 - i, value = path[i]; path[i] = path[other]; path[other] = value;
        }
    }
    private static boolean hamilton(int[] path, boolean[] used, int length, Random random, Generation budget) {
        budget.tick();
        if (length == path.length) return true;
        int[] neighbors = Generation.neighbors(path[length - 1], N);
        for (int direction : Generation.order(4, random)) {
            int next = neighbors[direction];
            if (next < 0 || used[next]) continue;
            used[next] = true; path[length] = next;
            if (hamilton(path, used, length + 1, random, budget)) return true;
            used[next] = false;
        }
        return false;
    }
    @Override public void write(DataOutputStream out) throws IOException {
        if (size() != 4) { out.writeInt(-1); out.writeInt(size()); }
        PuzzleStore.array(out, endpoints); PuzzleStore.array(out, edges);
    }
    static Numberlink read(DataInputStream in) throws IOException {
        int first = in.readInt(), n = 4;
        if (first == -1) {
            n = in.readInt(); if (n != 6) throw new IOException("Unsupported Numberlink size.");
            first = in.readInt();
        }
        if (first < 0 || first > MAX_PAIRS) throw new IOException("Invalid endpoint.");
        int[] endpoints = new int[n * n]; endpoints[0] = first;
        int[] rest = PuzzleStore.array(in, endpoints.length - 1, 0, MAX_PAIRS);
        System.arraycopy(rest, 0, endpoints, 1, rest.length);
        int[] edges = PuzzleStore.array(in, 2 * n * (n - 1), 0, 1);
        if (!validEndpoints(endpoints)) throw new IOException("Invalid endpoint pairs.");
        return new Numberlink(endpoints, edges);
    }
}
