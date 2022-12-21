package de.kaleidox.wikigame;

import org.comroid.util.Debug;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

public class Program {
    private static Set<String> checked = new HashSet<>();
    private static boolean debug = false;

    public static void main(String[] args) throws IOException, ExecutionException, TimeoutException {
        if (args.length != 2)
            args = new String[]{"https://en.wikipedia.org/wiki/Oscar_da_Silva", "https://en.wikipedia.org/wiki/Adolf_Hitler"};
        if (args.length != 2)
            throw new RuntimeException("Invalid argument count; expected 2 arguments: start and target");
        debug = Debug.isDebug();

        var start = new URL(args[0]);
        var target = new URL(args[1]);

        var result = run(start, target);
        if (result == null) {
            System.out.println("Article not found: " + target);
            return;
        }

        if (result != null) {
            System.out.println("Search Result:");
            result.printQuery();
            System.exit(0);
        } else System.out.println("No solution found.");
    }

    private static @Nullable ResultQuery run(URL start, URL target) throws ExecutionException, TimeoutException {
        try {
            var executor = Executors.newFixedThreadPool(16);
            checked.add(start.getPath());

            var yield = new CompletableFuture<ResultQuery>();
            var query = new ResultQuery(start);
            findResult$async$rec(target, start, query, yield, executor);

            yield.thenRun(executor::shutdownNow);
            return yield.get(5, TimeUnit.MINUTES);
        } catch (IOException | InterruptedException e) {
            handle(e);
            return null;
        }
    }

    private static void findResult$async$rec(URL target, URL here, ResultQuery query, CompletableFuture<ResultQuery> yield, ExecutorService executor) throws IOException, InterruptedException {
        var content = Jsoup.parse(here, (int) TimeUnit.SECONDS.toMillis(20)).getElementById("mw-content-text");
        if (content == null)
            return;
        for (var a : content.getElementsByTag("a")) {
            var href = a.attr("href");
            if (!href.startsWith("/wiki"))
                continue;
            var link = new URL("https://en.wikipedia.org" + href);
            var myQuery = new ResultQuery(query, link);

            String linkPath = link.getPath();
            if (checked.contains(linkPath))
                continue;
            if (target.getPath().equals(linkPath))
                yield.complete(myQuery);
            else if (!checked.add(linkPath)) {
                Thread.sleep(100);
                executor.submit(() -> {
                    try {
                        findResult$async$rec(target, link, myQuery, yield, executor);
                    } catch (IOException | InterruptedException e) {
                        handle(e);
                    }
                });
            } else if (debug && checked.contains(linkPath)) {
                System.out.println("The following query has yielded no result:");
                myQuery.printQuery();
            }
        }
    }

    private static void handle(Throwable t) {
        t.printStackTrace();
    }

    private static class ResultQuery {
        public @Nullable ResultQuery parent;
        public URL myself;

        public ResultQuery(URL myself) {
            this(null, myself);
        }

        public ResultQuery(@Nullable ResultQuery parent, URL myself) {
            this.parent = parent;
            this.myself = myself;
        }

        public void printQuery() {
            if (parent != null)
                parent.printQuery();
            System.out.println("\t-->\t" + myself.toExternalForm());
        }
    }
}
