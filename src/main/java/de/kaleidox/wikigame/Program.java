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
            args = new String[]{"https://en.wikipedia.org/wiki/Base64", "https://en.wikipedia.org/wiki/Spain"};
        if (args.length != 2)
            throw new RuntimeException("Invalid argument count; expected 2 arguments: start and target");
        debug = Debug.isDebug();

        var start = new URL(args[0]);
        var target = new URL(args[1]);

        run(start, target);
    }

    private static void run(URL start, URL target) throws ExecutionException, TimeoutException {
        try {
            var executor = Executors.newFixedThreadPool(16);
            checked.add(start.getPath());

            var yield = new CompletableFuture<ResultQuery>();
            var query = new ResultQuery(start);
            findResult$async$rec(target, start, query, yield);

            yield.thenAccept((result -> {
                executor.shutdownNow();

                if (result == null) {
                    System.out.println("Article not found: " + target);
                    return;
                }

                System.out.println("Search Result:");
                result.printQuery();

                System.exit(0);
            }));
            yield.get(30, TimeUnit.MINUTES);
        } catch (IOException | InterruptedException e) {
            handle(e);
        }
    }

    private static void findResult$async$rec(URL target, URL here, ResultQuery query, CompletableFuture<ResultQuery> yield) throws IOException, InterruptedException {
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
            else if (addPath(linkPath)) {
                Thread.sleep(50);
                new Thread(new ThreadGroup(link.getPath()), () -> {
                    try {
                        findResult$async$rec(target, link, myQuery, yield);
                    } catch (IOException | InterruptedException e) {
                        handle(e);
                    }
                }).start();
            } else if (debug && checked.contains(linkPath)) {
                System.out.println("The following query has yielded no result:");
                myQuery.printQuery();
            }// else yield.completeExceptionally(new RuntimeException("No result"));
        }
    }

    private static boolean addPath(String linkPath) {
        if (checked.add(linkPath)) {
            if (debug)
                System.out.println(Thread.currentThread().getThreadGroup().getName() + ": Checking " + linkPath);
            return true;
        }
        return false;
    }

    private static void handle(Throwable t) {
        if (debug)
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
